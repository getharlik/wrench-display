package dev.harlik.wrenchdisplay.fabric.blueprint;

import com.google.gson.Gson;
import dev.harlik.wrenchdisplay.common.blueprint.BlueprintParser;
import dev.harlik.wrenchdisplay.fabric.WrenchDisplay;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Pasted blueprints live in the world folder ({@code data/wrench-display/blueprints/<id>.json.gz}); the item
 * only carries the id. The original paste is stored and re-parsed on load, so parser fixes apply to old
 * blueprints too.
 */
public final class BlueprintStore {
	private static final Gson GSON = new Gson();
	private static final Map<UUID, PreparedBlueprint> CACHE = new HashMap<>();

	private BlueprintStore() {
	}

	record Entry(String text, UUID author, long created) {
	}

	private static Path dir(MinecraftServer server) {
		return server.getWorldPath(LevelResource.DATA).resolve(WrenchDisplay.MOD_ID).resolve("blueprints");
	}

	public static UUID save(MinecraftServer server, String text, UUID author, PreparedBlueprint prepared) throws IOException {
		UUID id = UUID.randomUUID();
		Path file = dir(server).resolve(id + ".json.gz");
		Files.createDirectories(file.getParent());
		try (Writer writer = new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(file)), StandardCharsets.UTF_8)) {
			GSON.toJson(new Entry(text, author, System.currentTimeMillis()), writer);
		}
		CACHE.put(id, prepared);
		return id;
	}

	/** Returns null for an unknown id (e.g. a blueprint item brought from another server). */
	public static PreparedBlueprint load(MinecraftServer server, UUID id) {
		PreparedBlueprint cached = CACHE.get(id);
		if (cached != null) return cached;
		Path file = dir(server).resolve(id + ".json.gz");
		if (!Files.exists(file)) return null;
		try (Reader reader = new InputStreamReader(new GZIPInputStream(Files.newInputStream(file)), StandardCharsets.UTF_8)) {
			Entry entry = GSON.fromJson(reader, Entry.class);
			BlueprintParser.Result result = BlueprintParser.parse(entry.text(), BlueprintParser.Limits.UNLIMITED);
			if (!result.isOk()) {
				WrenchDisplay.LOGGER.warn("Stored blueprint {} no longer parses: {}", id, result.error());
				return null;
			}
			PreparedBlueprint prepared = PreparedBlueprint.prepare(result.model(), server.registryAccess());
			CACHE.put(id, prepared);
			return prepared;
		} catch (IOException | RuntimeException e) {
			WrenchDisplay.LOGGER.warn("Could not read blueprint {}: {}", id, e.getMessage());
			return null;
		}
	}

	public static void clearCache() {
		CACHE.clear();
	}
}
