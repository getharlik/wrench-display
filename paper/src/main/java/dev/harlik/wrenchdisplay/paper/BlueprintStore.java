package dev.harlik.wrenchdisplay.paper;

import com.google.gson.Gson;
import dev.harlik.wrenchdisplay.common.blueprint.BlueprintParser;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Pasted blueprints live in {@code plugins/WrenchDisplay/blueprints/<id>.json.gz}; the item only carries the id.
 * Same format as the Fabric mod, so the files can be copied between servers.
 */
final class BlueprintStore {
	private static final Gson GSON = new Gson();

	private final Path dir;
	private final Logger logger;
	private final Map<UUID, PreparedBlueprint> cache = new ConcurrentHashMap<>();

	BlueprintStore(Path dataFolder, Logger logger) {
		this.dir = dataFolder.resolve("blueprints");
		this.logger = logger;
	}

	record Entry(String text, UUID author, long created) {
	}

	UUID save(String text, UUID author, PreparedBlueprint prepared) throws IOException {
		UUID id = UUID.randomUUID();
		Path file = dir.resolve(id + ".json.gz");
		Files.createDirectories(dir);
		try (Writer writer = new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(file)), StandardCharsets.UTF_8)) {
			GSON.toJson(new Entry(text, author, System.currentTimeMillis()), writer);
		}
		cache.put(id, prepared);
		return id;
	}

	/** Returns null for an unknown id (e.g. a blueprint item brought from another server). */
	PreparedBlueprint load(UUID id) {
		PreparedBlueprint cached = cache.get(id);
		if (cached != null) return cached;
		Path file = dir.resolve(id + ".json.gz");
		if (!Files.exists(file)) return null;
		try (Reader reader = new InputStreamReader(new GZIPInputStream(Files.newInputStream(file)), StandardCharsets.UTF_8)) {
			Entry entry = GSON.fromJson(reader, Entry.class);
			BlueprintParser.Result result = BlueprintParser.parse(entry.text(), BlueprintParser.Limits.UNLIMITED);
			if (!result.isOk()) {
				logger.warning("Stored blueprint " + id + " no longer parses: " + result.error().toFallbackString());
				return null;
			}
			PreparedBlueprint prepared = PreparedBlueprint.prepare(result.model());
			cache.put(id, prepared);
			return prepared;
		} catch (IOException | RuntimeException e) {
			logger.warning("Could not read blueprint " + id + ": " + e.getMessage());
			return null;
		}
	}
}
