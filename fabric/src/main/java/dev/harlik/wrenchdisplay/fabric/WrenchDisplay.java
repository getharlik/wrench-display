package dev.harlik.wrenchdisplay.fabric;

import dev.harlik.wrenchdisplay.common.Settings;
import dev.harlik.wrenchdisplay.fabric.blueprint.BlueprintHandler;
import dev.harlik.wrenchdisplay.fabric.wrench.WrenchHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

public class WrenchDisplay implements ModInitializer {
	public static final String MOD_ID = "wrench-display";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static Settings settings = new Settings();

	@Override
	public void onInitialize() {
		reloadSettings();
		DisplayStore.register();
		WrenchHandler.register();
		BlueprintHandler.register();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> WrenchCommand.register(dispatcher));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendResourcePack(handler.player));
	}

	/** Pushes the configured resource pack; vanilla clients need it to see the wrench and blueprint models. */
	private static void sendResourcePack(ServerPlayer player) {
		Settings.ResourcePack pack = settings.resourcePack;
		if (pack.url == null || pack.url.isBlank()) {
			return;
		}
		Optional<Component> prompt = pack.prompt == null || pack.prompt.isBlank() ? Optional.empty() : Optional.of(Component.literal(pack.prompt));
		// Same id for the same URL, so the client replaces the pack instead of stacking copies.
		UUID id = UUID.nameUUIDFromBytes(pack.url.getBytes(StandardCharsets.UTF_8));
		player.connection.send(new ClientboundResourcePackPushPacket(id, pack.url, pack.sha1 == null ? "" : pack.sha1, pack.required, prompt));
	}

	public static Settings settings() {
		return settings;
	}

	/** @return false if the file could not be read; the previous settings stay active then */
	public static boolean reloadSettings() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json");
		try {
			settings = Settings.load(file);
			return true;
		} catch (IOException | RuntimeException e) {
			LOGGER.error("Could not load {}: {}", file, e.getMessage());
			return false;
		}
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
