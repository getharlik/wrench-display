package dev.harlik.wrenchdisplay.fabric.blueprint;

import dev.harlik.wrenchdisplay.common.DisplayMeta;
import dev.harlik.wrenchdisplay.common.PermissionView;
import dev.harlik.wrenchdisplay.common.Permissions;
import dev.harlik.wrenchdisplay.common.Settings;
import dev.harlik.wrenchdisplay.common.blueprint.BlueprintParser;
import dev.harlik.wrenchdisplay.common.blueprint.BlueprintValidator;
import dev.harlik.wrenchdisplay.common.edit.DisplayTransform;
import dev.harlik.wrenchdisplay.fabric.DisplayStore;
import dev.harlik.wrenchdisplay.fabric.FabricDisplay;
import dev.harlik.wrenchdisplay.fabric.FabricPermissions;
import dev.harlik.wrenchdisplay.fabric.ModItems;
import dev.harlik.wrenchdisplay.fabric.Protection;
import dev.harlik.wrenchdisplay.fabric.WrenchDisplay;
import dev.harlik.wrenchdisplay.fabric.mixin.DisplayAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Everything the blueprint does on a Fabric server.
 *
 * <ul>
 *   <li>right-click the air (or sneak + right-click anywhere): open the paste window</li>
 *   <li>right-click a block with a filled blueprint: place the model there</li>
 *   <li>sneak + scroll: rotate the model by 90°</li>
 * </ul>
 * While a filled blueprint is held, a glowing preview only its holder can see follows the targeted block.
 */
public final class BlueprintHandler {
	public static final Identifier PASTE_ACTION = WrenchDisplay.id("blueprint_paste");
	/**
	 * Dialog submissions are NBT under a 32 KiB budget that charges 2 bytes per character, so the vanilla
	 * paste window can't take more than about this many characters; bigger models need the client mod.
	 */
	public static final int VANILLA_PROTOCOL_LIMIT = 16000;
	private static final String INPUT_KEY = "paste";
	private static final int USE_DEBOUNCE_TICKS = 2;
	private static final int HUD_INTERVAL = 20;
	private static final int PREVIEW_COLOR = 0x3AB3DA;

	private static final Map<UUID, PlayerState> STATES = new HashMap<>();

	private BlueprintHandler() {
	}

	private static final class PlayerState {
		/** Server tick of the last handled right-click; -1 before the first one. */
		long lastUseTick = -1;
		UUID previewBlueprint;
		BlockPos previewAnchor;
		int previewRotation = -1;
		int[] previewIds = new int[0];
	}

	public static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) ->
				player instanceof ServerPlayer sp ? onUse(sp, hand, hit) : InteractionResult.PASS);
		UseItemCallback.EVENT.register((player, level, hand) ->
				player instanceof ServerPlayer sp ? onUse(sp, hand, null) : InteractionResult.PASS);
		ServerTickEvents.END_SERVER_TICK.register(BlueprintHandler::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STATES.remove(handler.player.getUUID()));
	}

	private static PlayerState state(ServerPlayer player) {
		return STATES.computeIfAbsent(player.getUUID(), id -> new PlayerState());
	}

	private static Settings settings() {
		return WrenchDisplay.settings();
	}

	// --- input ---------------------------------------------------------------------------------

	private static InteractionResult onUse(ServerPlayer player, InteractionHand hand, BlockHitResult hit) {
		ItemStack stack = player.getMainHandItem();
		if (!ModItems.isBlueprint(stack)) return InteractionResult.PASS;
		if (hand == InteractionHand.OFF_HAND) return InteractionResult.FAIL;
		PlayerState state = state(player);
		long now = player.level().getServer().getTickCount();
		if (state.lastUseTick >= 0 && now - state.lastUseTick < USE_DEBOUNCE_TICKS) return InteractionResult.SUCCESS;
		state.lastUseTick = now;

		PermissionView permissions = FabricPermissions.of(player);
		if (!permissions.has(Permissions.USE_BLUEPRINT)) {
			player.sendSystemMessage(Component.literal("You can't use blueprints here.").withStyle(ChatFormatting.RED), true);
			return InteractionResult.FAIL;
		}
		UUID id = ModItems.blueprintId(stack);
		if (player.isShiftKeyDown() || id == null) {
			openPasteDialog(player);
		} else if (hit != null) {
			place(player, stack, id, hit, permissions);
		} else {
			// Right-click in the air with a filled blueprint: the preview shows where it would go.
			BlockHitResult target = targetBlock(player);
			if (target != null) place(player, stack, id, target, permissions);
		}
		return InteractionResult.SUCCESS;
	}

	/** Sneak + scroll with a filled blueprint rotates it. Returns true if the slot change must be cancelled. */
	public static boolean onHotbarScroll(ServerPlayer player, int newSlot) {
		ItemStack stack = player.getMainHandItem();
		if (!player.isShiftKeyDown() || !ModItems.isBlueprint(stack) || ModItems.blueprintId(stack) == null) return false;
		Inventory inventory = player.getInventory();
		int current = inventory.getSelectedSlot();
		int size = Inventory.getSelectionSize();
		if (newSlot == current || newSlot < 0 || newSlot >= size) return false;
		int direction = Math.floorMod(newSlot - current, size) <= size / 2 ? +1 : -1;
		ModItems.setRotation(stack, ModItems.rotation(stack) + direction);
		player.connection.send(new ClientboundSetHeldSlotPacket(current));
		player.sendSystemMessage(Component.literal("Blueprint rotation: " + ModItems.rotation(stack) * 90 + "°").withStyle(ChatFormatting.AQUA), true);
		return true;
	}

	// --- pasting -------------------------------------------------------------------------------

	public static int vanillaMaxLength() {
		return Math.max(1, Math.min(settings().blueprint.vanillaMaxLength, VANILLA_PROTOCOL_LIMIT));
	}

	static void openPasteDialog(ServerPlayer player) {
		int max = vanillaMaxLength();
		CommonDialogData common = new CommonDialogData(
				title(),
				Optional.empty(),
				true,
				false,
				DialogAction.CLOSE,
				List.of(new PlainMessage(Component.literal("Paste a /summon command exported from BDEngine or a similar tool. "
						+ "Several commands for one model can be pasted together.\nUp to " + max + " characters here; "
						+ "bigger models need the Wrench Display client mod."), 300)),
				List.of(new Input(INPUT_KEY, new TextInput(300, Component.literal("Command"), false, "", max,
						Optional.of(new TextInput.MultilineOptions(Optional.empty(), Optional.of(160)))))));
		ActionButton save = new ActionButton(new CommonButtonData(Component.literal("Save"), 150),
				Optional.of(new CustomAll(PASTE_ACTION, Optional.empty())));
		ActionButton cancel = new ActionButton(new CommonButtonData(Component.literal("Cancel"), 150), Optional.empty());
		player.openDialog(Holder.direct(new MultiActionDialog(common, List.of(save, cancel), Optional.empty(), 2)));
	}

	/**
	 * "Blueprint" with the blueprint icon in front, drawn from the resource pack's {@code wrench-display:gui}
	 * font; without the pack the icon character simply doesn't render.
	 */
	private static Component title() {
		return Component.empty()
				.append(Component.literal("\uE000 ").withStyle(style -> style.withFont(new FontDescription.Resource(WrenchDisplay.id("gui")))))
				.append(Component.literal("Blueprint"));
	}

	/** Called from the packet mixin for our dialog actions. */
	public static void onCustomClick(ServerPlayer player, Identifier id, Optional<Tag> payload) {
		if (!id.equals(PASTE_ACTION)) return;
		String text = payload.filter(tag -> tag instanceof CompoundTag)
				.flatMap(tag -> ((CompoundTag) tag).getString(INPUT_KEY)).orElse("");
		submitPaste(player, text, vanillaMaxLength());
	}

	/** Shared by the vanilla dialog and the client-mod screen. */
	public static void submitPaste(ServerPlayer player, String text, int maxLength) {
		ItemStack stack = player.getMainHandItem();
		if (!ModItems.isBlueprint(stack)) {
			error(player, "Hold the blueprint in your main hand to save the paste.");
			return;
		}
		PermissionView permissions = FabricPermissions.of(player);
		if (!permissions.has(Permissions.USE_BLUEPRINT)) return;
		Settings settings = settings();
		int maxParts = permissions.has(Permissions.BYPASS_BLUEPRINT_SIZE) ? settings.blueprint.maxPartsWithBypass : settings.blueprint.maxParts;
		BlueprintParser.Result result = BlueprintParser.parse(text, new BlueprintParser.Limits(maxLength, maxParts));
		if (!result.isOk()) {
			String hint = text.length() >= maxLength - 1
					? " The paste may have been cut off at " + maxLength + " characters; bigger models need the client mod." : "";
			error(player, "Couldn't read the paste: " + result.error() + "." + hint);
			return;
		}
		MinecraftServer server = player.level().getServer();
		PreparedBlueprint prepared = PreparedBlueprint.prepare(result.model(), server.registryAccess());
		List<String> problems = new ArrayList<>(prepared.problems());
		problems.addAll(BlueprintValidator.validate(result.model(), settings, permissions, FabricBlockInfo.INSTANCE));
		if (!problems.isEmpty()) {
			error(player, "This model can't be used:");
			problems.forEach(p -> player.sendSystemMessage(Component.literal(" • " + p).withStyle(ChatFormatting.RED)));
			return;
		}
		UUID id;
		try {
			id = BlueprintStore.save(server, text, player.getUUID(), prepared);
		} catch (IOException e) {
			WrenchDisplay.LOGGER.error("Could not save a blueprint", e);
			error(player, "The server couldn't save the blueprint.");
			return;
		}
		ModItems.fillBlueprint(stack, id, summary(prepared));
		player.sendSystemMessage(Component.literal("Blueprint saved: " + prepared.parts().size()
				+ " parts. Right-click a block to place it, sneak + scroll to rotate.").withStyle(ChatFormatting.AQUA));
	}

	private static List<Component> summary(PreparedBlueprint prepared) {
		List<Component> lines = new ArrayList<>();
		float[] size = prepared.model().size();
		lines.add(Component.literal(String.format(Locale.ROOT, "%d parts · %.1f×%.1f×%.1f blocks",
				prepared.parts().size(), size[0], size[1], size[2])).withStyle(ChatFormatting.GRAY));
		Map<String, Integer> totals = new LinkedHashMap<>();
		for (Materials.Cost cost : Materials.costs(prepared)) {
			if (cost != null) totals.merge(cost.label(), cost.count(), Integer::sum);
		}
		totals.entrySet().stream()
				.sorted((a, b) -> b.getValue() - a.getValue())
				.limit(4)
				.forEach(e -> lines.add(Component.literal(e.getValue() + "× " + e.getKey()).withStyle(ChatFormatting.DARK_GRAY)));
		if (totals.size() > 4) {
			lines.add(Component.literal("…and " + (totals.size() - 4) + " more").withStyle(ChatFormatting.DARK_GRAY));
		}
		return lines;
	}

	// --- placing -------------------------------------------------------------------------------

	/** Places the blueprint in {@code stack} against the clicked face, as a right-click would. */
	public static void place(ServerPlayer player, ItemStack stack, UUID id, BlockHitResult hit, PermissionView permissions) {
		ServerLevel level = player.level();
		PreparedBlueprint prepared = BlueprintStore.load(level.getServer(), id);
		if (prepared == null) {
			error(player, "This blueprint isn't stored on this server. Sneak + right-click to paste it again.");
			return;
		}
		Settings settings = settings();
		// Check with the placer's permissions: the blueprint may have been made by someone with more rights.
		List<String> problems = new ArrayList<>(prepared.problems());
		problems.addAll(BlueprintValidator.validate(prepared.model(), settings, permissions, FabricBlockInfo.INSTANCE));
		if (!problems.isEmpty()) {
			error(player, "You can't place this blueprint:");
			problems.forEach(p -> player.sendSystemMessage(Component.literal(" • " + p).withStyle(ChatFormatting.RED)));
			return;
		}

		BlockPos anchor = hit.getBlockPos().relative(hit.getDirection());
		int rotation = ModItems.rotation(stack);
		List<Display> parts = prepared.create(level, anchor.getX() + 0.5, anchor.getY() + 0.5, anchor.getZ() + 0.5, rotation);
		for (Display part : parts) {
			if (!Protection.canBuild(player, center(part))) {
				error(player, "Part of the model would be in an area you can't build in.");
				return;
			}
		}
		if (!permissions.has(Permissions.BYPASS_CHUNK_LIMIT)
				&& DisplayStore.countInChunk(level, anchor.getX(), anchor.getZ()) + parts.size() > settings.survival.maxDisplaysPerChunk) {
			error(player, "This chunk can't hold " + parts.size() + " more displays (limit " + settings.survival.maxDisplaysPerChunk + ").");
			return;
		}

		boolean consume = !player.isCreative() && !permissions.has(Permissions.BYPASS_COST);
		List<ItemStack> taken = null;
		if (consume) {
			List<Materials.Cost> costs = Materials.costs(prepared);
			List<String> missing = Materials.missing(player.getInventory(), costs);
			if (!missing.isEmpty()) {
				error(player, "Missing materials:");
				missing.stream().limit(8).forEach(m -> player.sendSystemMessage(Component.literal(" • " + m).withStyle(ChatFormatting.RED)));
				if (missing.size() > 8) player.sendSystemMessage(Component.literal(" …and " + (missing.size() - 8) + " more").withStyle(ChatFormatting.RED));
				return;
			}
			taken = Materials.take(player.getInventory(), costs);
		}

		UUID groupId = UUID.randomUUID();
		for (int i = 0; i < parts.size(); i++) {
			Display part = parts.get(i);
			Vector3f home = DisplayTransform.center(transformation(part), FabricDisplay.kindOf(part));
			ItemStack refund = taken == null ? null : taken.get(i);
			String encoded = refund == null || refund.isEmpty() ? null : DisplayStore.encodeItem(level.registryAccess(), refund);
			DisplayStore.set(part, new DisplayMeta(player.getUUID(), new float[] {home.x, home.y, home.z}, groupId, encoded));
			level.addFreshEntity(part);
		}
		clearPreview(player);
		level.playSound(null, anchor, SoundEvents.BOOK_PUT, SoundSource.BLOCKS, 1.0f, 1.0f);
		player.sendSystemMessage(Component.literal("Placed " + parts.size() + " parts. Use the wrench to adjust them.").withStyle(ChatFormatting.AQUA), true);
	}

	// --- preview -------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		long now = server.getTickCount();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ItemStack stack = player.getMainHandItem();
			UUID id = ModItems.isBlueprint(stack) ? ModItems.blueprintId(stack) : null;
			if (id == null) {
				clearPreview(player);
				if (ModItems.isBlueprint(stack) && now % HUD_INTERVAL == 0) {
					player.sendSystemMessage(Component.literal("Blank blueprint · right-click to paste a model").withStyle(ChatFormatting.GRAY), true);
				}
				continue;
			}
			updatePreview(player, id, ModItems.rotation(stack));
			if (now % HUD_INTERVAL == 0) {
				player.sendSystemMessage(Component.literal("Blueprint · rotation " + ModItems.rotation(stack) * 90
						+ "° · right-click to place · sneak + scroll to rotate").withStyle(ChatFormatting.AQUA), true);
			}
		}
	}

	private static BlockHitResult targetBlock(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1.0f).scale(player.blockInteractionRange()));
		BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.MISS ? null : hit;
	}

	private static void updatePreview(ServerPlayer player, UUID id, int rotation) {
		PlayerState state = state(player);
		BlockHitResult hit = targetBlock(player);
		BlockPos anchor = hit == null ? null : hit.getBlockPos().relative(hit.getDirection());
		if (anchor == null) {
			clearPreview(player);
			return;
		}
		if (anchor.equals(state.previewAnchor) && rotation == state.previewRotation && id.equals(state.previewBlueprint)) {
			return;
		}
		PreparedBlueprint prepared = BlueprintStore.load(player.level().getServer(), id);
		clearPreview(player);
		if (prepared == null) return;

		List<Display> parts = prepared.create(player.level(), anchor.getX() + 0.5, anchor.getY() + 0.5, anchor.getZ() + 0.5, rotation);
		int[] ids = new int[parts.size()];
		for (int i = 0; i < parts.size(); i++) {
			Display part = parts.get(i);
			part.setGlowingTag(true);
			part.setGlowColorOverride(PREVIEW_COLOR);
			ids[i] = part.getId();
			// Never added to the level: only this player is told the entities exist.
			player.connection.send(new ClientboundAddEntityPacket(part.getId(), part.getUUID(), part.getX(), part.getY(), part.getZ(),
					0, 0, part.getType(), 0, Vec3.ZERO, 0));
			List<SynchedEntityData.DataValue<?>> data = part.getEntityData().getNonDefaultValues();
			if (data != null) player.connection.send(new ClientboundSetEntityDataPacket(part.getId(), data));
		}
		state.previewIds = ids;
		state.previewAnchor = anchor;
		state.previewRotation = rotation;
		state.previewBlueprint = id;
	}

	private static void clearPreview(ServerPlayer player) {
		PlayerState state = STATES.get(player.getUUID());
		if (state == null || state.previewIds.length == 0) return;
		player.connection.send(new ClientboundRemoveEntitiesPacket(state.previewIds));
		state.previewIds = new int[0];
		state.previewAnchor = null;
		state.previewRotation = -1;
		state.previewBlueprint = null;
	}

	// --- helpers -------------------------------------------------------------------------------

	private static Matrix4f transformation(Display display) {
		return DisplayAccessor.wrenchDisplay$createTransformation(display.getEntityData()).getMatrixCopy();
	}

	private static Vec3 center(Display display) {
		Vector3f local = DisplayTransform.center(transformation(display), FabricDisplay.kindOf(display));
		return display.position().add(local.x, local.y, local.z);
	}

	private static void error(ServerPlayer player, String message) {
		player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
	}
}
