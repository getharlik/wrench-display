package dev.harlik.wrenchdisplay.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.harlik.wrenchdisplay.common.Permissions;
import dev.harlik.wrenchdisplay.common.Settings;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public final class WrenchDisplayPlugin extends JavaPlugin implements Listener {
	/** Same namespace as the Fabric mod, so the resource pack and its translations are shared. */
	static final String NAMESPACE = "wrench-display";

	private Settings settings = new Settings();
	private WrenchListener wrench;
	private BlueprintListener blueprint;

	@Override
	public void onEnable() {
		reloadSettings();
		BlueprintStore store = new BlueprintStore(getDataFolder().toPath(), getLogger());
		wrench = new WrenchListener(this);
		blueprint = new BlueprintListener(this, store);
		getServer().getPluginManager().registerEvents(wrench, this);
		getServer().getPluginManager().registerEvents(blueprint, this);
		getServer().getPluginManager().registerEvents(new VanillaUseBlocker(), this);
		ClientModChannel clientMod = new ClientModChannel(this, blueprint);
		blueprint.setClientModChannel(clientMod);
		clientMod.register();
		getServer().getPluginManager().registerEvents(this, this);
		registerRecipes();
		registerCommands();
		// After a reload, players already online need their per-player tasks too.
		Bukkit.getOnlinePlayers().forEach(this::startTicking);
	}

	@Override
	public void onDisable() {
		if (blueprint != null) blueprint.clearAllPreviews();
	}

	Settings settings() {
		return settings;
	}

	/** @return false if the file could not be read; the previous settings stay active then */
	boolean reloadSettings() {
		try {
			settings = Settings.load(getDataFolder().toPath().resolve("config.json"));
			return true;
		} catch (IOException | RuntimeException e) {
			getSLF4JLogger().error("Could not load config.json: {}", e.getMessage());
			return false;
		}
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		startTicking(event.getPlayer());
		sendResourcePack(event.getPlayer());
	}

	/** Per-player task on the player's own scheduler, so it runs on the right thread on Folia too. */
	private void startTicking(Player player) {
		player.getScheduler().runAtFixedRate(this, task -> {
			long tick = Bukkit.getCurrentTick();
			wrench.tick(player, tick);
			blueprint.tick(player, tick);
		}, () -> blueprint.clearPreview(player), 1L, 1L);
	}

	private void sendResourcePack(Player player) {
		Settings.ResourcePack pack = settings.resourcePack;
		if (pack.url == null || pack.url.isBlank()) return;
		try {
			ResourcePackInfo info = ResourcePackInfo.resourcePackInfo()
					.id(UUID.nameUUIDFromBytes(pack.url.getBytes(StandardCharsets.UTF_8)))
					.uri(URI.create(pack.url))
					.hash(pack.sha1 == null ? "" : pack.sha1)
					.build();
			ResourcePackRequest.Builder request = ResourcePackRequest.resourcePackRequest().packs(info).required(pack.required).replace(false);
			if (pack.prompt != null && !pack.prompt.isBlank()) request.prompt(Component.text(pack.prompt));
			player.sendResourcePacks(request.build());
		} catch (IllegalArgumentException e) {
			getSLF4JLogger().warn("resourcePack.url is not a valid URL: {}", pack.url);
		}
	}

	private void registerRecipes() {
		ShapedRecipe wrenchRecipe = new ShapedRecipe(new NamespacedKey(NAMESPACE, "wrench"), PaperItems.wrench());
		wrenchRecipe.shape("  N", " I ", "S  ");
		wrenchRecipe.setIngredient('N', Material.NETHERITE_INGOT);
		wrenchRecipe.setIngredient('I', Material.IRON_INGOT);
		wrenchRecipe.setIngredient('S', Material.STICK);
		ShapedRecipe blueprintRecipe = new ShapedRecipe(new NamespacedKey(NAMESPACE, "blueprint"), PaperItems.blueprint());
		blueprintRecipe.shape(" L ", "FPI", " D ");
		blueprintRecipe.setIngredient('L', Material.LAPIS_LAZULI);
		blueprintRecipe.setIngredient('F', Material.FEATHER);
		blueprintRecipe.setIngredient('P', Material.PAPER);
		blueprintRecipe.setIngredient('I', Material.INK_SAC);
		blueprintRecipe.setIngredient('D', Material.DIAMOND_BLOCK);
		for (ShapedRecipe recipe : List.of(wrenchRecipe, blueprintRecipe)) {
			Bukkit.removeRecipe(recipe.getKey());
			Bukkit.addRecipe(recipe);
		}
	}

	/** {@code /wrenchdisplay give <players> wrench|blueprint} and {@code /wrenchdisplay reload}. */
	private void registerCommands() {
		getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(
				Commands.literal("wrenchdisplay")
						.requires(source -> source.getSender().hasPermission(Permissions.ADMIN))
						.then(Commands.literal("give")
								.then(Commands.argument("targets", ArgumentTypes.players())
										.then(Commands.literal(PaperItems.WRENCH).executes(ctx -> give(ctx, PaperItems::wrench)))
										.then(Commands.literal(PaperItems.BLUEPRINT).executes(ctx -> give(ctx, PaperItems::blueprint)))))
						.then(Commands.literal("reload").executes(ctx -> {
							boolean ok = reloadSettings();
							ctx.getSource().getSender().sendMessage(ok
									? PaperLang.tr("command.reloaded", "Wrench Display config reloaded")
									: PaperLang.tr("command.reload_failed", "Could not read the config; see the server log"));
							return ok ? Command.SINGLE_SUCCESS : 0;
						}))
						.build(),
				"Wrench Display tools and config"));
	}

	private int give(CommandContext<CommandSourceStack> ctx, Supplier<ItemStack> item) throws CommandSyntaxException {
		List<Player> targets = ctx.getArgument("targets", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource());
		for (Player player : targets) {
			player.getInventory().addItem(item.get()).values().forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
		}
		ctx.getSource().getSender().sendMessage(PaperLang.tr("command.gave", "Gave %s to %s player(s)", item.get().effectiveName(), targets.size()));
		return targets.size();
	}
}
