package dev.harlik.wrenchdisplay.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Server configuration, stored as JSON on both platforms so one file format and one set of defaults
 * covers the mod and the plugin. Missing keys are filled with defaults and written back on load.
 */
public final class Settings {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	public Wrench wrench = new Wrench();
	public Survival survival = new Survival();
	public Blueprint blueprint = new Blueprint();
	public ResourcePack resourcePack = new ResourcePack();

	public static final class Wrench {
		/** How far players can target displays, in blocks. */
		public double reach = 5.0;
	}

	/** Limits for players without the matching bypass permission. */
	public static final class Survival {
		/** Max distance of a display's centre from the centre of the block it was placed in, per axis. */
		public double maxOffset = 1.0;
		public double minScale = 0.25;
		public double maxScale = 2.0;
		public int maxDisplaysPerChunk = 64;
		/** Block-state keys survival players may change; everything else needs {@link Permissions#FREE_BLOCK_STATES}. */
		public List<String> allowedBlockStateKeys = new ArrayList<>(List.of(
				"facing", "axis", "half", "type", "shape", "rotation", "open", "hinge", "attachment", "face",
				"lit", "candles", "waterlogged", "pickles", "layers", "eggs", "bites", "flower_amount", "segment_amount"));
		/** Blocks that need {@link Permissions#RESTRICTED_BLOCKS}; invisible, technical or misleading ones. */
		public List<String> restrictedBlocks = new ArrayList<>(List.of(
				"minecraft:barrier", "minecraft:light", "minecraft:structure_void", "minecraft:moving_piston",
				"minecraft:nether_portal", "minecraft:end_portal", "minecraft:end_gateway", "minecraft:end_portal_frame",
				"minecraft:command_block", "minecraft:chain_command_block", "minecraft:repeating_command_block",
				"minecraft:structure_block", "minecraft:jigsaw", "minecraft:spawner", "minecraft:trial_spawner",
				"minecraft:vault", "minecraft:bedrock", "minecraft:reinforced_deepslate", "minecraft:test_block",
				"minecraft:test_instance_block"));
	}

	public static final class Blueprint {
		/** Max paste length on the client-mod screen. Real BDEngine models run to 60 KB and more. */
		public int maxLength = 131072;
		/**
		 * Max paste length in the vanilla dialog. The protocol can't carry more than about 16000 characters
		 * (32 KiB of NBT at 2 bytes per character), so higher values are capped; bigger models need the client mod.
		 */
		public int vanillaMaxLength = 16000;
		public int maxParts = 256;
		public int maxPartsWithBypass = 2048;
		/** Survival: the whole model must fit in this box (blocks per axis). */
		public double maxModelSize = 5.0;
		/** Survival: no single part may be larger than this on any axis. */
		public double maxPartSize = 3.0;
	}

	public static final class ResourcePack {
		/**
		 * Push this pack to players on join (the zip from {@code ./gradlew :resourcepack:assemble}, hosted somewhere
		 * public). Leave empty to skip, e.g. when server.properties already sends a pack.
		 */
		public String url = "";
		public String sha1 = "";
		public boolean required = false;
		public String prompt = "Wrench Display textures";
	}

	public static Settings load(Path file) throws IOException {
		Settings defaults = new Settings();
		if (!Files.exists(file)) {
			save(file, defaults);
			return defaults;
		}
		JsonObject stored = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
		JsonObject merged = GSON.toJsonTree(defaults).getAsJsonObject();
		deepMerge(merged, stored);
		Settings settings = GSON.fromJson(merged, Settings.class);
		save(file, settings);
		return settings;
	}

	public static void save(Path file, Settings settings) throws IOException {
		Files.createDirectories(file.toAbsolutePath().getParent());
		Files.writeString(file, GSON.toJson(settings), StandardCharsets.UTF_8);
	}

	private static void deepMerge(JsonObject target, JsonObject source) {
		for (String key : source.keySet()) {
			if (target.has(key) && target.get(key).isJsonObject() && source.get(key).isJsonObject()) {
				deepMerge(target.getAsJsonObject(key), source.getAsJsonObject(key));
			} else if (target.has(key)) {
				target.add(key, source.get(key));
			}
		}
	}
}
