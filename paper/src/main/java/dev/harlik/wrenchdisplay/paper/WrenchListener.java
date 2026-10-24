package dev.harlik.wrenchdisplay.paper;

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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Everything the wrench does on a Paper server: + / − on the targeted display with right/left-click, sneak +
 * click or sneak + scroll to pick the property, and right-click a block with an item in the off hand to place it.
 */
final class WrenchListener implements Listener {
	/** A block right-click can arrive as more than one interact event; act on one of them. */
	private static final int USE_DEBOUNCE_TICKS = 2;
	private static final int OUTLINE_INTERVAL = 4;
	private static final int HUD_INTERVAL = 10;
	private static final int COLOR_EDIT = 0xFFAA00;
	private static final int COLOR_GROUP = 0x3AB3DA;
	private static final int COLOR_LOCKED = 0xE04040;

	private final WrenchDisplayPlugin plugin;
	private final Map<UUID, PlayerState> states = new ConcurrentHashMap<>();

	WrenchListener(WrenchDisplayPlugin plugin) {
		this.plugin = plugin;
	}

	private static final class PlayerState {
		final WrenchSession session = new WrenchSession();
		/** Server tick of the last handled right-click; -1 before the first one. */
		long lastUseTick = -1;
		Display target;
		Component notice;
		long noticeUntil;
	}

	private PlayerState state(Player player) {
		return states.computeIfAbsent(player.getUniqueId(), id -> new PlayerState());
	}

	private Settings settings() {
		return plugin.settings();
	}

	static boolean holdsWrench(Player player) {
		return PaperItems.isWrench(player.getInventory().getItemInMainHand());
	}

	// --- input ---------------------------------------------------------------------------------

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
	public void onInteract(PlayerInteractEvent event) {
		Player player = event.getPlayer();
		if (!holdsWrench(player)) return;
		event.setCancelled(true); // never place, mine or use anything with the wrench out
		if (event.getHand() != EquipmentSlot.HAND) return; // the off hand holds what gets placed
		Action action = event.getAction();
		if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
			PlayerState state = state(player);
			long now = player.getServer().getCurrentTick();
			if (state.lastUseTick >= 0 && now - state.lastUseTick < USE_DEBOUNCE_TICKS) return;
			state.lastUseTick = now;
			onRightClick(player, action == Action.RIGHT_CLICK_BLOCK ? event.getClickedBlock() : null, event);
		} else if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
			onLeftClick(player);
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onBreak(BlockBreakEvent event) {
		if (holdsWrench(event.getPlayer())) event.setCancelled(true);
	}

	/** Sneak + scroll cycles the property instead of changing the hotbar slot. */
	@EventHandler(ignoreCancelled = true)
	public void onHeld(PlayerItemHeldEvent event) {
		Player player = event.getPlayer();
		if (!player.isSneaking() || !holdsWrench(player)) return;
		int size = 9;
		int forward = Math.floorMod(event.getNewSlot() - event.getPreviousSlot(), size);
		cycle(player, findTarget(player), forward <= size / 2 ? +1 : -1);
		event.setCancelled(true);
		refreshHud(player);
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		states.remove(event.getPlayer().getUniqueId());
	}

	private void onRightClick(Player player, Block clicked, PlayerInteractEvent event) {
		PermissionView permissions = PaperPermissions.of(player);
		if (!permissions.has(Permissions.USE_WRENCH)) {
			notice(player, PaperLang.tr("wrench.no_permission", "You can't use the wrench here.").color(NamedTextColor.RED));
			return;
		}
		Display target = findTarget(player);
		if (player.isSneaking()) {
			cycle(player, target, +1);
		} else if (target != null) {
			if (state(player).session.property(available(player, new PaperDisplay(target), permissions)) == EditProperty.REMOVE) {
				remove(player, new PaperDisplay(target), permissions);
			} else {
				adjust(player, target, +1);
			}
		} else if (clicked != null && !player.getInventory().getItemInOffHand().isEmpty()) {
			Vector hit = event.getInteractionPoint() == null ? null : event.getInteractionPoint().toVector();
			boolean upperHalf = hit != null && hit.getY() - clicked.getY() > 0.5;
			place(player, clicked, event.getBlockFace(), upperHalf, permissions);
		}
		refreshHud(player);
	}

	private void onLeftClick(Player player) {
		if (!PaperPermissions.of(player).has(Permissions.USE_WRENCH)) return;
		Display target = findTarget(player);
		if (player.isSneaking()) {
			cycle(player, target, -1);
		} else if (target != null) {
			adjust(player, target, -1);
		}
		refreshHud(player);
	}

	// --- actions -------------------------------------------------------------------------------

	private Display findTarget(Player player) {
		DisplayTargeting.Hit hit = DisplayTargeting.find(player, settings().wrench.reach);
		Display target = hit == null ? null : hit.display();
		state(player).target = target;
		return target;
	}

	private List<EditProperty> available(Player player, PaperDisplay display, PermissionView permissions) {
		boolean inGroup = display.meta() != null && display.meta().groupId() != null;
		boolean groupMode = inGroup && state(player).session.groupMode();
		return EditProperty.available(display.kind(), inGroup, groupMode, permissions);
	}

	private void cycle(Player player, Display target, int direction) {
		PermissionView permissions = PaperPermissions.of(player);
		List<EditProperty> available = target != null
				? available(player, new PaperDisplay(target), permissions)
				: EditProperty.available(DisplayKind.BLOCK, false, false, permissions);
		state(player).session.cycle(available, direction);
	}

	/** Why the player may not change this display, or null if they may. */
	private Component denyReason(Player player, PaperDisplay display, PermissionView permissions) {
		DisplayMeta meta = display.meta();
		if (meta == null) {
			if (!permissions.has(Permissions.EDIT_FOREIGN)) return PaperLang.tr("wrench.foreign", "This display wasn't placed with a wrench.");
		} else if (!meta.owner().equals(player.getUniqueId()) && !permissions.has(Permissions.EDIT_OTHERS)) {
			return PaperLang.tr("wrench.not_owner", "This display belongs to someone else.");
		}
		if (!Protection.canBuild(player, center(display))) {
			return PaperLang.tr("protection.denied", "You can't build here.");
		}
		return null;
	}

	private void adjust(Player player, Display target, int direction) {
		PermissionView permissions = PaperPermissions.of(player);
		PaperDisplay focus = new PaperDisplay(target);
		Component denied = denyReason(player, focus, permissions);
		if (denied != null) {
			notice(player, denied.color(NamedTextColor.RED));
			return;
		}
		PlayerState state = state(player);
		EditProperty property = state.session.property(available(player, focus, permissions));
		if (property == EditProperty.REMOVE) {
			notice(player, PaperLang.tr("wrench.remove_hint", "Right-click to remove").color(NamedTextColor.YELLOW));
			return;
		}
		List<PaperDisplay> members = members(player, focus);
		List<Matrix4f> before = new ArrayList<>();
		members.forEach(m -> before.add(m.transformation()));

		PropertyEditor.Outcome outcome = PropertyEditor.adjust(focus, new ArrayList<EditableDisplay>(members), property, direction,
				state.session, settings(), permissions);

		if (outcome == PropertyEditor.Outcome.CHANGED && property.isTransform()) {
			for (PaperDisplay member : members) {
				if (!Protection.canBuild(player, center(member))) {
					for (int i = 0; i < members.size(); i++) members.get(i).setTransformation(before.get(i));
					notice(player, PaperLang.tr("protection.denied_target", "You can't build there.").color(NamedTextColor.RED));
					return;
				}
			}
		}
		if (outcome == PropertyEditor.Outcome.LIMIT) {
			notice(player, PaperLang.tr("wrench.limit", "%s: survival limit reached", PaperLang.of(property.message())).color(NamedTextColor.RED));
		}
	}

	private List<PaperDisplay> members(Player player, PaperDisplay focus) {
		DisplayMeta meta = focus.meta();
		if (meta == null || meta.groupId() == null || !state(player).session.groupMode()) {
			return List.of(focus);
		}
		List<PaperDisplay> out = new ArrayList<>();
		out.add(focus);
		for (Display part : DisplayStore.group(focus.entity(), meta.groupId())) {
			if (!part.equals(focus.entity())) out.add(new PaperDisplay(part));
		}
		return out;
	}

	private void place(Player player, Block clicked, BlockFace face, boolean upperHalf, PermissionView permissions) {
		Settings settings = settings();
		ItemStack source = player.getInventory().getItemInOffHand();
		if (PaperItems.isTool(source)) return; // don't turn our own tools into displays

		BlockData blockData = null;
		Block target = clicked.getRelative(face);
		if (clicked.isReplaceable()) target = clicked;
		if (source.getType().isBlock() && source.getType() != Material.AIR) {
			blockData = Placement.orient(source.getType(), player, face, upperHalf);
			String id = source.getType().getKey().toString();
			if (!permissions.has(Permissions.RESTRICTED_BLOCKS) && settings.survival.restrictedBlocks.contains(id)) {
				notice(player, PaperLang.tr("wrench.restricted_block", "%s can't be placed as a display.", PaperLang.material(source.getType()))
						.color(NamedTextColor.RED));
				return;
			}
		}
		Location corner = target.getLocation();
		if (!Protection.canBuild(player, corner.clone().add(0.5, 0.5, 0.5))) {
			notice(player, PaperLang.tr("protection.denied", "You can't build here.").color(NamedTextColor.RED));
			return;
		}
		if (!permissions.has(Permissions.BYPASS_CHUNK_LIMIT)
				&& DisplayStore.countInChunk(target.getChunk()) >= settings.survival.maxDisplaysPerChunk) {
			notice(player, PaperLang.tr("wrench.chunk_full", "This chunk already has %s displays.", settings.survival.maxDisplaysPerChunk)
					.color(NamedTextColor.RED));
			return;
		}

		boolean consume = player.getGameMode() != GameMode.CREATIVE && !permissions.has(Permissions.BYPASS_COST);
		ItemStack single = source.asOne();
		Matrix4f matrix = blockData != null ? new Matrix4f() : new Matrix4f().translation(0.5f, 0.5f, 0.5f);
		DisplayKind kind = blockData != null ? DisplayKind.BLOCK : DisplayKind.ITEM;
		Vector3f home = DisplayTransform.center(matrix, kind);
		DisplayMeta meta = new DisplayMeta(player.getUniqueId(), new float[] {home.x, home.y, home.z}, null,
				consume ? DisplayStore.encodeItem(single) : null);
		BlockData finalBlock = blockData;
		corner.setYaw(0);
		corner.setPitch(0);
		Display display = finalBlock != null
				? player.getWorld().spawn(corner, BlockDisplay.class, d -> {
					d.setBlock(finalBlock);
					DisplayStore.set(d, meta);
				})
				: player.getWorld().spawn(corner, ItemDisplay.class, d -> {
					d.setItemStack(single);
					d.setTransformationMatrix(matrix);
					DisplayStore.set(d, meta);
				});
		if (consume) source.subtract();

		player.swingMainHand();
		Sound sound = finalBlock != null ? finalBlock.getSoundGroup().getPlaceSound() : Sound.ENTITY_ITEM_FRAME_ADD_ITEM;
		player.getWorld().playSound(display.getLocation(), sound, 1.0f, 1.0f);
	}

	private void remove(Player player, PaperDisplay focus, PermissionView permissions) {
		Component denied = denyReason(player, focus, permissions);
		if (denied != null) {
			notice(player, denied.color(NamedTextColor.RED));
			return;
		}
		Location sound = center(focus);
		for (PaperDisplay member : members(player, focus)) {
			DisplayMeta meta = member.meta();
			ItemStack refund = meta == null ? null : DisplayStore.decodeItem(meta.refund());
			if (refund != null && !refund.isEmpty()) {
				player.getInventory().addItem(refund).values()
						.forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
			}
			member.entity().remove();
		}
		state(player).target = null;
		player.swingMainHand();
		player.getWorld().playSound(sound, Sound.ENTITY_ITEM_FRAME_REMOVE_ITEM, 1.0f, 1.0f);
	}

	// --- feedback ------------------------------------------------------------------------------

	/** Runs every tick for each online player, on that player's thread (Folia-safe). */
	void tick(Player player, long tick) {
		if (!holdsWrench(player)) {
			PlayerState state = states.get(player.getUniqueId());
			if (state != null) state.target = null;
			return;
		}
		Display target = findTarget(player);
		if (target != null && tick % OUTLINE_INTERVAL == 0) {
			outline(player, new PaperDisplay(target));
		}
		if (tick % HUD_INTERVAL == 0) {
			refreshHud(player);
		}
	}

	private void notice(Player player, Component message) {
		PlayerState state = state(player);
		state.notice = message;
		state.noticeUntil = player.getServer().getCurrentTick() + 30;
		player.sendActionBar(message);
	}

	private void refreshHud(Player player) {
		PlayerState state = state(player);
		if (state.notice != null && player.getServer().getCurrentTick() < state.noticeUntil) {
			player.sendActionBar(state.notice);
			return;
		}
		state.notice = null;
		PermissionView permissions = PaperPermissions.of(player);
		Component line;
		if (state.target == null || !state.target.isValid()) {
			line = PaperLang.tr("wrench.hud_idle", "Wrench · %s · look at a display", PaperLang.of(state.session.property().message()))
					.color(NamedTextColor.GRAY);
		} else {
			PaperDisplay focus = new PaperDisplay(state.target);
			Component denied = denyReason(player, focus, permissions);
			if (denied != null) {
				line = denied.color(NamedTextColor.RED);
			} else {
				EditProperty property = state.session.property(available(player, focus, permissions));
				Component text = PaperLang.of(PropertyEditor.describe(focus, property, state.session, settings(), permissions));
				if (focus.meta() != null && focus.meta().groupId() != null) {
					text = text.append(state.session.groupMode()
							? PaperLang.tr("wrench.hud_group", "  [group]") : PaperLang.tr("wrench.hud_part", "  [part]"));
				}
				line = text.color(NamedTextColor.GOLD);
			}
		}
		player.sendActionBar(line);
	}

	/** Dust-particle box around the target (or its whole group), visible only to this player. */
	private void outline(Player player, PaperDisplay focus) {
		Location origin = focus.entity().getLocation();
		List<PaperDisplay> members = members(player, focus);
		float[] box = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
				Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
		for (PaperDisplay member : members) {
			float[] b = Bounds.of(member.kind(), DisplayTargeting.worldMatrix(member.entity(), origin));
			for (int a = 0; a < 3; a++) {
				box[a] = Math.min(box[a], b[a]);
				box[a + 3] = Math.max(box[a + 3], b[a + 3]);
			}
		}
		int color = denyReason(player, focus, PaperPermissions.of(player)) != null ? COLOR_LOCKED
				: members.size() > 1 ? COLOR_GROUP : COLOR_EDIT;
		Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(color), 0.4f);
		double[][] corners = new double[8][];
		for (int i = 0; i < 8; i++) {
			corners[i] = new double[] {
					origin.getX() + ((i & 1) == 0 ? box[0] : box[3]),
					origin.getY() + ((i & 2) == 0 ? box[1] : box[4]),
					origin.getZ() + ((i & 4) == 0 ? box[2] : box[5])};
		}
		int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
		for (int[] edge : edges) {
			double[] a = corners[edge[0]];
			double[] b = corners[edge[1]];
			double length = Math.sqrt(Math.pow(b[0] - a[0], 2) + Math.pow(b[1] - a[1], 2) + Math.pow(b[2] - a[2], 2));
			int points = (int) Math.max(2, Math.min(8, Math.ceil(length * 4)));
			for (int p = 0; p <= points; p++) {
				double t = p / (double) points;
				player.spawnParticle(Particle.DUST, a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t,
						1, 0, 0, 0, 0, dust, true);
			}
		}
	}

	private static Location center(PaperDisplay display) {
		Vector3f local = DisplayTransform.center(display.transformation(), display.kind());
		return display.entity().getLocation().add(local.x, local.y, local.z);
	}
}
