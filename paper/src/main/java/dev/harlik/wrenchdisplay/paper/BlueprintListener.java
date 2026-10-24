package dev.harlik.wrenchdisplay.paper;

import dev.harlik.wrenchdisplay.common.DisplayMeta;
import dev.harlik.wrenchdisplay.common.PermissionView;
import dev.harlik.wrenchdisplay.common.Permissions;
import dev.harlik.wrenchdisplay.common.Settings;
import dev.harlik.wrenchdisplay.common.blueprint.BlueprintParser;
import dev.harlik.wrenchdisplay.common.blueprint.BlueprintValidator;
import dev.harlik.wrenchdisplay.common.edit.DisplayTransform;
import dev.harlik.wrenchdisplay.common.math.Matrices;
import dev.harlik.wrenchdisplay.common.text.Message;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Everything the blueprint does on a Paper server: right-click the air (or sneak + right-click) to paste,
 * right-click a block to place, sneak + scroll to rotate, and a preview only the holder sees.
 */
final class BlueprintListener implements Listener {
	/** Dialog submissions can't carry more than about this many characters; see the Fabric handler for why. */
	static final int VANILLA_PROTOCOL_LIMIT = 16000;
	private static final int USE_DEBOUNCE_TICKS = 2;
	private static final int HUD_INTERVAL = 20;
	private static final int PREVIEW_COLOR = 0x3AB3DA;

	private final WrenchDisplayPlugin plugin;
	private final BlueprintStore store;
	private final Map<UUID, PlayerState> states = new ConcurrentHashMap<>();
	private ClientModChannel clientMod;

	BlueprintListener(WrenchDisplayPlugin plugin, BlueprintStore store) {
		this.plugin = plugin;
		this.store = store;
	}

	private static final class PlayerState {
		long lastUseTick = -1;
		UUID previewBlueprint;
		Block previewAnchor;
		int previewRotation = -1;
		List<Display> preview = List.of();
	}

	void setClientModChannel(ClientModChannel clientMod) {
		this.clientMod = clientMod;
	}

	private PlayerState state(Player player) {
		return states.computeIfAbsent(player.getUniqueId(), id -> new PlayerState());
	}

	private Settings settings() {
		return plugin.settings();
	}

	// --- input ---------------------------------------------------------------------------------

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
	public void onInteract(PlayerInteractEvent event) {
		Player player = event.getPlayer();
		ItemStack stack = player.getInventory().getItemInMainHand();
		if (!PaperItems.isBlueprint(stack)) return;
		Action action = event.getAction();
		if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
		event.setCancelled(true);
		if (event.getHand() != EquipmentSlot.HAND) return;
		PlayerState state = state(player);
		long now = player.getServer().getCurrentTick();
		if (state.lastUseTick >= 0 && now - state.lastUseTick < USE_DEBOUNCE_TICKS) return;
		state.lastUseTick = now;

		PermissionView permissions = PaperPermissions.of(player);
		if (!permissions.has(Permissions.USE_BLUEPRINT)) {
			player.sendActionBar(PaperLang.tr("blueprint.no_permission", "You can't use blueprints here.").color(NamedTextColor.RED));
			return;
		}
		UUID id = PaperItems.blueprintId(stack);
		if (player.isSneaking() || id == null) {
			openPasteDialog(player);
			return;
		}
		Block anchor = action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null
				? event.getClickedBlock().getRelative(event.getBlockFace()) : targetAnchor(player);
		if (anchor != null) place(player, stack, id, anchor, permissions);
	}

	/** Sneak + scroll with a filled blueprint rotates it. */
	@EventHandler(ignoreCancelled = true)
	public void onHeld(PlayerItemHeldEvent event) {
		Player player = event.getPlayer();
		ItemStack stack = player.getInventory().getItemInMainHand();
		if (!player.isSneaking() || !PaperItems.isBlueprint(stack) || PaperItems.blueprintId(stack) == null) return;
		int forward = Math.floorMod(event.getNewSlot() - event.getPreviousSlot(), 9);
		PaperItems.setRotation(stack, PaperItems.rotation(stack) + (forward <= 4 ? 1 : -1));
		event.setCancelled(true);
		player.sendActionBar(PaperLang.tr("blueprint.rotation", "Blueprint rotation: %s°", PaperItems.rotation(stack) * 90)
				.color(NamedTextColor.AQUA));
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		clearPreview(event.getPlayer());
		states.remove(event.getPlayer().getUniqueId());
	}

	// --- pasting -------------------------------------------------------------------------------

	int vanillaMaxLength() {
		return Math.max(1, Math.min(settings().blueprint.vanillaMaxLength, VANILLA_PROTOCOL_LIMIT));
	}

	/** The client mod's textured screen when the player has it, otherwise the vanilla dialog. */
	private void openPasteDialog(Player player) {
		if (clientMod != null && ClientModChannel.hasClientMod(player)) {
			clientMod.openPaste(player, settings().blueprint.maxLength);
			return;
		}
		if (!PasteDialog.AVAILABLE) {
			error(player, PaperLang.tr("blueprint.no_dialog",
					"This server can't show the paste window (Paper 1.21.7 or newer needed); the Wrench Display client mod works instead."));
			return;
		}
		int max = vanillaMaxLength();
		PasteDialog.open(plugin, player, max, text -> submitPaste(player, text, max));
	}

	/** Shared by the vanilla dialog and the client-mod screen. */
	void submitPaste(Player player, String text, int maxLength) {
		ItemStack stack = player.getInventory().getItemInMainHand();
		if (!PaperItems.isBlueprint(stack)) {
			error(player, PaperLang.tr("blueprint.hold_to_save", "Hold the blueprint in your main hand to save the paste."));
			return;
		}
		PermissionView permissions = PaperPermissions.of(player);
		if (!permissions.has(Permissions.USE_BLUEPRINT)) return;
		Settings settings = settings();
		int maxParts = permissions.has(Permissions.BYPASS_BLUEPRINT_SIZE) ? settings.blueprint.maxPartsWithBypass : settings.blueprint.maxParts;
		BlueprintParser.Result result = BlueprintParser.parse(text, new BlueprintParser.Limits(maxLength, maxParts));
		if (!result.isOk()) {
			error(player, PaperLang.tr("blueprint.unreadable", "Couldn't read the paste: %s.", PaperLang.of(result.error())));
			if (text.length() >= maxLength - 1) {
				error(player, PaperLang.tr("blueprint.cut_off", "The paste may have been cut off at %s characters; bigger models need the client mod.", maxLength));
			}
			return;
		}
		PreparedBlueprint prepared = PreparedBlueprint.prepare(result.model());
		List<Message> problems = new ArrayList<>(prepared.problems());
		problems.addAll(BlueprintValidator.validate(result.model(), settings, permissions, PaperBlockInfo.INSTANCE));
		if (!problems.isEmpty()) {
			error(player, PaperLang.tr("blueprint.unusable", "This model can't be used:"));
			problems.forEach(p -> bullet(player, PaperLang.of(p)));
			return;
		}
		UUID id;
		try {
			id = store.save(text, player.getUniqueId(), prepared);
		} catch (IOException e) {
			plugin.getSLF4JLogger().error("Could not save a blueprint", e);
			error(player, PaperLang.tr("blueprint.save_failed", "The server couldn't save the blueprint."));
			return;
		}
		PaperItems.fillBlueprint(stack, id, summary(prepared));
		player.sendMessage(PaperLang.tr("blueprint.saved", "Blueprint saved: %s parts. Right-click a block to place it, sneak + scroll to rotate.",
				prepared.parts().size()).color(NamedTextColor.AQUA));
	}

	private static List<Component> summary(PreparedBlueprint prepared) {
		List<Component> lines = new ArrayList<>();
		float[] size = prepared.model().size();
		lines.add(PaperLang.tr("blueprint.lore.size", "%s parts · %s×%s×%s blocks", prepared.parts().size(),
				decimal(size[0]), decimal(size[1]), decimal(size[2])).color(NamedTextColor.GRAY));
		Map<String, Integer> totals = new LinkedHashMap<>();
		Map<String, Materials.Cost> byGroup = new LinkedHashMap<>();
		for (Materials.Cost cost : Materials.costs(prepared)) {
			if (cost == null) continue;
			totals.merge(cost.group(), cost.count(), Integer::sum);
			byGroup.putIfAbsent(cost.group(), cost);
		}
		totals.entrySet().stream()
				.sorted((a, b) -> b.getValue() - a.getValue())
				.limit(4)
				.forEach(e -> lines.add(PaperLang.tr("material.amount", "%s× %s", e.getValue(), byGroup.get(e.getKey()).label())
						.color(NamedTextColor.DARK_GRAY)));
		if (totals.size() > 4) {
			lines.add(PaperLang.tr("blueprint.lore.more", "…and %s more", totals.size() - 4).color(NamedTextColor.DARK_GRAY));
		}
		return lines;
	}

	// --- placing -------------------------------------------------------------------------------

	private void place(Player player, ItemStack stack, UUID id, Block anchor, PermissionView permissions) {
		PreparedBlueprint prepared = store.load(id);
		if (prepared == null) {
			error(player, PaperLang.tr("blueprint.unknown", "This blueprint isn't stored on this server. Sneak + right-click to paste it again."));
			return;
		}
		Settings settings = settings();
		List<Message> problems = new ArrayList<>(prepared.problems());
		problems.addAll(BlueprintValidator.validate(prepared.model(), settings, permissions, PaperBlockInfo.INSTANCE));
		if (!problems.isEmpty()) {
			error(player, PaperLang.tr("blueprint.cannot_place", "You can't place this blueprint:"));
			problems.forEach(p -> bullet(player, PaperLang.of(p)));
			return;
		}
		Location origin = anchor.getLocation().add(0.5, 0.5, 0.5);
		int rotation = PaperItems.rotation(stack);

		// Check claims for every part before anything is spawned or paid for.
		for (Vector3f center : centers(prepared, rotation)) {
			if (!Protection.canBuild(player, origin.clone().add(center.x, center.y, center.z))) {
				error(player, PaperLang.tr("protection.denied_model", "Part of the model would be in an area you can't build in."));
				return;
			}
		}
		int count = prepared.parts().size();
		if (!permissions.has(Permissions.BYPASS_CHUNK_LIMIT)
				&& DisplayStore.countInChunk(anchor.getChunk()) + count > settings.survival.maxDisplaysPerChunk) {
			error(player, PaperLang.tr("blueprint.chunk_full", "This chunk can't hold %s more displays (limit %s).", count, settings.survival.maxDisplaysPerChunk));
			return;
		}

		boolean consume = player.getGameMode() != GameMode.CREATIVE && !permissions.has(Permissions.BYPASS_COST);
		List<ItemStack> taken = null;
		if (consume) {
			List<Materials.Cost> costs = Materials.costs(prepared);
			List<Component> missing = Materials.missing(player.getInventory(), costs);
			if (!missing.isEmpty()) {
				error(player, PaperLang.tr("blueprint.missing_materials", "Missing materials:"));
				missing.stream().limit(8).forEach(m -> bullet(player, m));
				if (missing.size() > 8) bullet(player, PaperLang.tr("blueprint.lore.more", "…and %s more", missing.size() - 8));
				return;
			}
			taken = Materials.take(player.getInventory(), costs);
		}

		UUID groupId = UUID.randomUUID();
		List<ItemStack> refunds = taken;
		int[] index = {0};
		prepared.spawn(anchor.getWorld(), origin, rotation, display -> {
			int i = index[0]++;
			Vector3f home = DisplayTransform.center(PaperDisplay.matrix(display), PaperDisplay.kindOf(display));
			ItemStack refund = refunds == null ? null : refunds.get(i);
			DisplayStore.set(display, new DisplayMeta(player.getUniqueId(), new float[] {home.x, home.y, home.z}, groupId,
					refund == null || refund.isEmpty() ? null : DisplayStore.encodeItem(refund)));
		});
		clearPreview(player);
		anchor.getWorld().playSound(origin, Sound.ITEM_BOOK_PUT, 1.0f, 1.0f);
		player.sendActionBar(PaperLang.tr("blueprint.placed", "Placed %s parts. Use the wrench to adjust them.", count).color(NamedTextColor.AQUA));
	}

	/** Each part's visual centre relative to the origin, after rotation, without spawning anything. */
	private static List<Vector3f> centers(PreparedBlueprint prepared, int rotation) {
		Matrix4f turn = new Matrix4f().rotationY((float) (-Math.PI / 2 * rotation));
		List<Vector3f> out = new ArrayList<>();
		for (PreparedBlueprint.Part part : prepared.parts()) {
			Matrix4f m = new Matrix4f(turn).mul(Matrices.fromRowMajor(part.source().matrix()));
			out.add(DisplayTransform.center(m, part.kind()));
		}
		return out;
	}

	// --- preview -------------------------------------------------------------------------------

	/** Runs every tick for each online player, on that player's thread (Folia-safe). */
	void tick(Player player, long tick) {
		ItemStack stack = player.getInventory().getItemInMainHand();
		UUID id = PaperItems.isBlueprint(stack) ? PaperItems.blueprintId(stack) : null;
		if (id == null) {
			clearPreview(player);
			if (PaperItems.isBlueprint(stack) && tick % HUD_INTERVAL == 0) {
				player.sendActionBar(PaperLang.tr("blueprint.hud_blank", "Blank blueprint · right-click to paste a model").color(NamedTextColor.GRAY));
			}
			return;
		}
		updatePreview(player, id, PaperItems.rotation(stack));
		if (tick % HUD_INTERVAL == 0) {
			player.sendActionBar(PaperLang.tr("blueprint.hud", "Blueprint · rotation %s° · right-click to place · sneak + scroll to rotate",
					PaperItems.rotation(stack) * 90).color(NamedTextColor.AQUA));
		}
	}

	private static Block targetAnchor(Player player) {
		AttributeInstance range = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
		RayTraceResult hit = player.rayTraceBlocks(range == null ? 4.5 : range.getValue(), FluidCollisionMode.NEVER);
		if (hit == null || hit.getHitBlock() == null || hit.getHitBlockFace() == null) return null;
		return hit.getHitBlock().getRelative(hit.getHitBlockFace());
	}

	private void updatePreview(Player player, UUID id, int rotation) {
		PlayerState state = state(player);
		Block anchor = targetAnchor(player);
		if (anchor == null) {
			clearPreview(player);
			return;
		}
		if (anchor.equals(state.previewAnchor) && rotation == state.previewRotation && id.equals(state.previewBlueprint)) {
			return;
		}
		PreparedBlueprint prepared = store.load(id);
		clearPreview(player);
		if (prepared == null) return;
		// Real entities that are never saved and that only this player is shown.
		state.preview = prepared.spawn(anchor.getWorld(), anchor.getLocation().add(0.5, 0.5, 0.5), rotation, display -> {
			display.setPersistent(false);
			display.setVisibleByDefault(false);
			display.setGlowing(true);
			display.setGlowColorOverride(Color.fromRGB(PREVIEW_COLOR));
		});
		state.preview.forEach(display -> player.showEntity(plugin, display));
		state.previewAnchor = anchor;
		state.previewRotation = rotation;
		state.previewBlueprint = id;
	}

	void clearPreview(Player player) {
		PlayerState state = states.get(player.getUniqueId());
		if (state == null || state.preview.isEmpty()) return;
		state.preview.forEach(Display::remove);
		state.preview = List.of();
		state.previewAnchor = null;
		state.previewRotation = -1;
		state.previewBlueprint = null;
	}

	/** Removes every preview, e.g. when the plugin is disabled. */
	void clearAllPreviews() {
		states.values().forEach(state -> state.preview.forEach(Display::remove));
		states.clear();
	}

	// --- helpers -------------------------------------------------------------------------------

	private static void error(Player player, Component message) {
		player.sendMessage(message.color(NamedTextColor.RED));
	}

	private static void bullet(Player player, Component line) {
		player.sendMessage(PaperLang.tr("bullet", " • %s", line).color(NamedTextColor.RED));
	}

	private static String decimal(float value) {
		return String.format(Locale.ROOT, "%.1f", value);
	}
}
