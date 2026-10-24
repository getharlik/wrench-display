package dev.harlik.wrenchdisplay.fabric.wrench;

import com.mojang.math.Transformation;
import dev.harlik.wrenchdisplay.common.DisplayMeta;
import dev.harlik.wrenchdisplay.common.PermissionView;
import dev.harlik.wrenchdisplay.common.Permissions;
import dev.harlik.wrenchdisplay.common.Settings;
import dev.harlik.wrenchdisplay.common.edit.Bounds;
import dev.harlik.wrenchdisplay.common.edit.DisplayKind;
import dev.harlik.wrenchdisplay.common.edit.DisplayTransform;
import dev.harlik.wrenchdisplay.common.edit.EditProperty;
import dev.harlik.wrenchdisplay.common.edit.EditableDisplay;
import dev.harlik.wrenchdisplay.common.edit.PropertyEditor;
import dev.harlik.wrenchdisplay.common.edit.WrenchSession;
import dev.harlik.wrenchdisplay.fabric.DisplayStore;
import dev.harlik.wrenchdisplay.fabric.FabricDisplay;
import dev.harlik.wrenchdisplay.fabric.FabricPermissions;
import dev.harlik.wrenchdisplay.fabric.Lang;
import dev.harlik.wrenchdisplay.fabric.ModItems;
import dev.harlik.wrenchdisplay.fabric.Protection;
import dev.harlik.wrenchdisplay.fabric.WrenchDisplay;
import dev.harlik.wrenchdisplay.fabric.compat.Compat;
import dev.harlik.wrenchdisplay.fabric.compat.DisplayTypes;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything the wrench does on a Fabric server.
 *
 * <ul>
 *   <li>right-click / left-click a display: + / − the selected property</li>
 *   <li>sneak + right/left-click or sneak + scroll: next / previous property</li>
 *   <li>right-click a block (no display targeted) with an item in the off hand: place it as a display</li>
 * </ul>
 */
public final class WrenchHandler {
	/** A block right-click arrives as several packets (block use, item use, off hand); act on one of them. */
	private static final int USE_DEBOUNCE_TICKS = 2;
	private static final int OUTLINE_INTERVAL = 4;
	private static final int HUD_INTERVAL = 10;
	private static final int COLOR_EDIT = 0xFFAA00;
	private static final int COLOR_GROUP = 0x3AB3DA;
	private static final int COLOR_LOCKED = 0xE04040;

	private static final Map<UUID, PlayerState> STATES = new HashMap<>();

	private WrenchHandler() {
	}

	private static final class PlayerState {
		final WrenchSession session = new WrenchSession();
		/** Server tick of the last handled right-click; -1 before the first one. */
		long lastUseTick = -1;
		Display target;
		/** Shown instead of the property line until this tick (e.g. "limit reached"). */
		Component notice;
		long noticeUntil;
	}

	public static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) ->
				player instanceof ServerPlayer sp ? onUse(sp, hand, hit) : InteractionResult.PASS);
		UseItemCallback.EVENT.register((player, level, hand) ->
				player instanceof ServerPlayer sp ? onUse(sp, hand, null) : InteractionResult.PASS);
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			if (!(player instanceof ServerPlayer sp) || !holdsWrench(sp)) return InteractionResult.PASS;
			onLeftClick(sp);
			return InteractionResult.FAIL; // never mine with the wrench
		});
		ServerTickEvents.END_SERVER_TICK.register(WrenchHandler::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STATES.remove(handler.player.getUUID()));
	}

	private static PlayerState state(ServerPlayer player) {
		return STATES.computeIfAbsent(player.getUUID(), id -> new PlayerState());
	}

	public static boolean holdsWrench(ServerPlayer player) {
		return ModItems.isWrench(player.getMainHandItem());
	}

	private static Settings settings() {
		return WrenchDisplay.settings();
	}

	// --- input ---------------------------------------------------------------------------------

	private static InteractionResult onUse(ServerPlayer player, InteractionHand hand, BlockHitResult hit) {
		if (!holdsWrench(player)) return InteractionResult.PASS;
		// The off hand holds what gets placed; never let it act on its own while the wrench is out.
		if (hand == InteractionHand.OFF_HAND) return InteractionResult.FAIL;
		PlayerState state = state(player);
		long now = player.level().getServer().getTickCount();
		if (state.lastUseTick >= 0 && now - state.lastUseTick < USE_DEBOUNCE_TICKS) return InteractionResult.SUCCESS;
		state.lastUseTick = now;
		onRightClick(player, hit);
		return InteractionResult.SUCCESS;
	}

	private static void onRightClick(ServerPlayer player, BlockHitResult hit) {
		PermissionView permissions = FabricPermissions.of(player);
		if (!permissions.has(Permissions.USE_WRENCH)) {
			notice(player, Lang.tr("wrench.no_permission", "You can't use the wrench here.").withStyle(ChatFormatting.RED));
			return;
		}
		Display target = findTarget(player);
		if (player.isShiftKeyDown()) {
			cycle(player, target, +1);
		} else if (target != null) {
			if (state(player).session.property(available(player, new FabricDisplay(target), permissions)) == EditProperty.REMOVE) {
				remove(player, new FabricDisplay(target), permissions);
			} else {
				adjust(player, target, +1);
			}
		} else if (hit != null && !player.getOffhandItem().isEmpty()) {
			place(player, hit, permissions);
		}
		refreshHud(player);
	}

	private static void onLeftClick(ServerPlayer player) {
		if (!FabricPermissions.of(player).has(Permissions.USE_WRENCH)) return;
		Display target = findTarget(player);
		if (player.isShiftKeyDown()) {
			cycle(player, target, -1);
		} else if (target != null) {
			adjust(player, target, -1);
		}
		refreshHud(player);
	}

	/** Called from the packet mixin for a left-click that hit nothing. */
	public static void onLeftClickAir(ServerPlayer player) {
		if (holdsWrench(player)) {
			onLeftClick(player);
		}
	}

	/**
	 * Called from the packet mixin when the client changes the hotbar slot.
	 *
	 * @return true if the change was used for property cycling and must be cancelled
	 */
	public static boolean onHotbarScroll(ServerPlayer player, int newSlot) {
		if (!player.isShiftKeyDown() || !holdsWrench(player)) return false;
		Inventory inventory = player.getInventory();
		int current = inventory.getSelectedSlot();
		if (newSlot == current || newSlot < 0 || newSlot >= Inventory.getSelectionSize()) return false;
		int size = Inventory.getSelectionSize();
		int forward = Math.floorMod(newSlot - current, size);
		// Scrolling down selects the next slot; treat the shorter way round as the scroll direction.
		cycle(player, findTarget(player), forward <= size / 2 ? +1 : -1);
		player.connection.send(new ClientboundSetHeldSlotPacket(current));
		refreshHud(player);
		return true;
	}

	// --- actions -------------------------------------------------------------------------------

	private static Display findTarget(ServerPlayer player) {
		DisplayTargeting.Hit hit = DisplayTargeting.find(player, settings().wrench.reach);
		Display target = hit == null ? null : hit.display();
		state(player).target = target;
		return target;
	}

	private static List<EditProperty> available(ServerPlayer player, FabricDisplay display, PermissionView permissions) {
		boolean inGroup = display.meta() != null && display.meta().groupId() != null;
		boolean groupMode = inGroup && state(player).session.groupMode();
		return EditProperty.available(display.kind(), inGroup, groupMode, permissions);
	}

	private static void cycle(ServerPlayer player, Display target, int direction) {
		PermissionView permissions = FabricPermissions.of(player);
		List<EditProperty> available = target != null
				? available(player, new FabricDisplay(target), permissions)
				: EditProperty.available(DisplayKind.BLOCK, false, false, permissions);
		state(player).session.cycle(available, direction);
	}

	/** Why the player may not change this display, or null if they may. */
	private static Component denyReason(ServerPlayer player, FabricDisplay display, PermissionView permissions) {
		DisplayMeta meta = display.meta();
		if (meta == null) {
			if (!permissions.has(Permissions.EDIT_FOREIGN)) return Lang.tr("wrench.foreign", "This display wasn't placed with a wrench.");
		} else if (!meta.owner().equals(player.getUUID()) && !permissions.has(Permissions.EDIT_OTHERS)) {
			return Lang.tr("wrench.not_owner", "This display belongs to someone else.");
		}
		if (!Protection.canBuild(player, center(display))) {
			return Lang.tr("protection.denied", "You can't build here.");
		}
		return null;
	}

	private static void adjust(ServerPlayer player, Display target, int direction) {
		PermissionView permissions = FabricPermissions.of(player);
		FabricDisplay focus = new FabricDisplay(target);
		Component denied = denyReason(player, focus, permissions);
		if (denied != null) {
			notice(player, denied.copy().withStyle(ChatFormatting.RED));
			return;
		}
		PlayerState state = state(player);
		List<EditProperty> available = available(player, focus, permissions);
		EditProperty property = state.session.property(available);
		if (property == EditProperty.REMOVE) {
			notice(player, Lang.tr("wrench.remove_hint", "Right-click to remove").withStyle(ChatFormatting.YELLOW));
			return;
		}
		List<FabricDisplay> members = members(player, focus);
		List<Matrix4f> before = new ArrayList<>();
		members.forEach(m -> before.add(m.transformation()));

		PropertyEditor.Outcome outcome = PropertyEditor.adjust(focus, new ArrayList<EditableDisplay>(members), property, direction,
				state.session, settings(), permissions);

		if (outcome == PropertyEditor.Outcome.CHANGED && property.isTransform()) {
			// Moving into someone's claim counts as building there.
			for (FabricDisplay member : members) {
				if (!Protection.canBuild(player, center(member))) {
					for (int i = 0; i < members.size(); i++) members.get(i).setTransformation(before.get(i));
					notice(player, Lang.tr("protection.denied_target", "You can't build there.").withStyle(ChatFormatting.RED));
					return;
				}
			}
		}
		if (outcome == PropertyEditor.Outcome.LIMIT) {
			notice(player, Lang.tr("wrench.limit", "%s: survival limit reached", Lang.of(property.message())).withStyle(ChatFormatting.RED));
		}
	}

	private static List<FabricDisplay> members(ServerPlayer player, FabricDisplay focus) {
		DisplayMeta meta = focus.meta();
		if (meta == null || meta.groupId() == null || !state(player).session.groupMode()) {
			return List.of(focus);
		}
		List<FabricDisplay> out = new ArrayList<>();
		out.add(focus);
		for (Display part : DisplayStore.group(focus.entity(), meta.groupId())) {
			if (part != focus.entity()) out.add(new FabricDisplay(part));
		}
		return out;
	}

	private static void place(ServerPlayer player, BlockHitResult hit, PermissionView permissions) {
		ServerLevel level = player.level();
		Settings settings = settings();
		ItemStack source = player.getOffhandItem();
		if (ModItems.type(source) != null) return; // don't turn our own tools into displays

		BlockState blockState = null;
		BlockPos pos;
		if (source.getItem() instanceof BlockItem blockItem) {
			BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.OFF_HAND, source, hit);
			pos = context.getClickedPos();
			blockState = blockItem.getBlock().getStateForPlacement(context);
			if (blockState == null) blockState = blockItem.getBlock().defaultBlockState();
			String id = BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).toString();
			if (!permissions.has(Permissions.RESTRICTED_BLOCKS) && settings.survival.restrictedBlocks.contains(id)) {
				notice(player, Lang.tr("wrench.restricted_block", "%s can't be placed as a display.", blockState.getBlock().getName()).withStyle(ChatFormatting.RED));
				return;
			}
		} else {
			pos = hit.getBlockPos().relative(hit.getDirection());
		}
		if (!Protection.canBuild(player, Vec3.atCenterOf(pos))) {
			notice(player, Lang.tr("protection.denied", "You can't build here.").withStyle(ChatFormatting.RED));
			return;
		}
		if (!permissions.has(Permissions.BYPASS_CHUNK_LIMIT)
				&& DisplayStore.countInChunk(level, pos.getX(), pos.getZ()) >= settings.survival.maxDisplaysPerChunk) {
			notice(player, Lang.tr("wrench.chunk_full", "This chunk already has %s displays.", settings.survival.maxDisplaysPerChunk).withStyle(ChatFormatting.RED));
			return;
		}

		Display display;
		Matrix4f matrix = new Matrix4f();
		if (blockState != null) {
			Display.BlockDisplay block = DisplayTypes.BLOCK.create(level, EntitySpawnReason.COMMAND);
			if (block == null) return;
			block.setBlockState(blockState);
			display = block;
		} else {
			Display.ItemDisplay item = DisplayTypes.ITEM.create(level, EntitySpawnReason.COMMAND);
			if (item == null) return;
			item.setItemStack(source.copyWithCount(1));
			matrix.translation(0.5f, 0.5f, 0.5f); // item displays are centred; keep the entity at the block corner
			display = item;
		}
		display.setPos(pos.getX(), pos.getY(), pos.getZ());
		display.setTransformation(new Transformation(matrix));

		boolean consume = !player.isCreative() && !permissions.has(Permissions.BYPASS_COST);
		Vector3f home = DisplayTransform.center(matrix, FabricDisplay.kindOf(display));
		String refund = consume ? DisplayStore.encodeItem(level.registryAccess(), source.copyWithCount(1)) : null;
		DisplayStore.set(display, new DisplayMeta(player.getUUID(), new float[] {home.x, home.y, home.z}, null, refund));
		level.addFreshEntity(display);
		if (consume) source.shrink(1);

		Compat.swingMainHand(player);
		var sound = blockState != null ? blockState.getSoundType().getPlaceSound() : SoundEvents.ITEM_FRAME_ADD_ITEM;
		level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0f, 1.0f);
	}

	private static void remove(ServerPlayer player, FabricDisplay focus, PermissionView permissions) {
		Component denied = denyReason(player, focus, permissions);
		if (denied != null) {
			notice(player, denied.copy().withStyle(ChatFormatting.RED));
			return;
		}
		for (FabricDisplay member : members(player, focus)) {
			DisplayMeta meta = member.meta();
			if (meta != null && meta.refund() != null) {
				ItemStack refund = DisplayStore.decodeItem(player.level().registryAccess(), meta.refund());
				if (!refund.isEmpty()) {
					Compat.giveOrDrop(player, refund);
				}
			}
			member.entity().discard();
		}
		state(player).target = null;
		Compat.swingMainHand(player);
		player.level().playSound(null, BlockPos.containing(center(focus)), SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0f, 1.0f);
	}

	// --- feedback ------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		long now = server.getTickCount();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!holdsWrench(player)) {
				PlayerState state = STATES.get(player.getUUID());
				if (state != null) state.target = null;
				continue;
			}
			Display target = findTarget(player);
			if (target != null && now % OUTLINE_INTERVAL == 0) {
				outline(player, new FabricDisplay(target));
			}
			if (now % HUD_INTERVAL == 0) {
				refreshHud(player);
			}
		}
	}

	private static void notice(ServerPlayer player, Component message) {
		PlayerState state = state(player);
		state.notice = message;
		state.noticeUntil = player.level().getServer().getTickCount() + 30;
		player.sendSystemMessage(message, true);
	}

	private static void refreshHud(ServerPlayer player) {
		PlayerState state = state(player);
		if (state.notice != null && player.level().getServer().getTickCount() < state.noticeUntil) {
			player.sendSystemMessage(state.notice, true);
			return;
		}
		state.notice = null;
		PermissionView permissions = FabricPermissions.of(player);
		Component line;
		if (state.target == null) {
			EditProperty property = state.session.property();
			line = Lang.tr("wrench.hud_idle", "Wrench · %s · look at a display", Lang.of(property.message())).withStyle(ChatFormatting.GRAY);
		} else {
			FabricDisplay focus = new FabricDisplay(state.target);
			Component denied = denyReason(player, focus, permissions);
			if (denied != null) {
				line = denied.copy().withStyle(ChatFormatting.RED);
			} else {
				EditProperty property = state.session.property(available(player, focus, permissions));
				MutableComponent text = Lang.of(PropertyEditor.describe(focus, property, state.session, settings(), permissions));
				boolean group = focus.meta() != null && focus.meta().groupId() != null;
				if (group) {
					text.append(state.session.groupMode() ? Lang.tr("wrench.hud_group", "  [group]") : Lang.tr("wrench.hud_part", "  [part]"));
				}
				line = text.withStyle(ChatFormatting.GOLD);
			}
		}
		player.sendSystemMessage(line, true);
	}

	/** Dust-particle box around the target (or its whole group), visible only to this player. */
	private static void outline(ServerPlayer player, FabricDisplay focus) {
		PermissionView permissions = FabricPermissions.of(player);
		Vec3 origin = focus.entity().position();
		List<FabricDisplay> members = members(player, focus);
		float[] box = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
				Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
		for (FabricDisplay member : members) {
			float[] b = Bounds.of(member.kind(), DisplayTargeting.worldMatrix(member.entity(), origin));
			for (int a = 0; a < 3; a++) {
				box[a] = Math.min(box[a], b[a]);
				box[a + 3] = Math.max(box[a + 3], b[a + 3]);
			}
		}
		int color = denyReason(player, focus, permissions) != null ? COLOR_LOCKED : members.size() > 1 ? COLOR_GROUP : COLOR_EDIT;
		DustParticleOptions dust = new DustParticleOptions(color, 0.4f);
		double[][] corners = new double[8][];
		for (int i = 0; i < 8; i++) {
			corners[i] = new double[] {
					origin.x + ((i & 1) == 0 ? box[0] : box[3]),
					origin.y + ((i & 2) == 0 ? box[1] : box[4]),
					origin.z + ((i & 4) == 0 ? box[2] : box[5])};
		}
		int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
		ServerLevel level = player.level();
		for (int[] edge : edges) {
			double[] a = corners[edge[0]];
			double[] b = corners[edge[1]];
			double length = Math.sqrt(Math.pow(b[0] - a[0], 2) + Math.pow(b[1] - a[1], 2) + Math.pow(b[2] - a[2], 2));
			int points = (int) Math.max(2, Math.min(8, Math.ceil(length * 4)));
			for (int p = 0; p <= points; p++) {
				double t = p / (double) points;
				level.sendParticles(player, dust, true, false,
						a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t, 1, 0, 0, 0, 0);
			}
		}
	}

	private static Vec3 center(FabricDisplay display) {
		Vector3f local = DisplayTransform.center(display.transformation(), display.kind());
		return display.entity().position().add(local.x, local.y, local.z);
	}
}
