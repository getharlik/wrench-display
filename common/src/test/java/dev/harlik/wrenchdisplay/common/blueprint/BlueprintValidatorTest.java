package dev.harlik.wrenchdisplay.common.blueprint;

import dev.harlik.wrenchdisplay.common.DisplayMeta;
import dev.harlik.wrenchdisplay.common.PermissionView;
import dev.harlik.wrenchdisplay.common.Permissions;
import dev.harlik.wrenchdisplay.common.Settings;
import dev.harlik.wrenchdisplay.common.text.Message;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.harlik.wrenchdisplay.common.blueprint.BlueprintParserTest.fixture;
import static dev.harlik.wrenchdisplay.common.blueprint.BlueprintParserTest.parseOk;
import static org.junit.jupiter.api.Assertions.*;

class BlueprintValidatorTest {
	private final Settings settings = new Settings();

	/** Rough visible shapes, as the platform would report them. */
	private static final BlueprintValidator.BlockInfo SHAPES = new BlueprintValidator.BlockInfo() {
		@Override
		public String defaultValue(String blockId, String key) {
			return null;
		}

		@Override
		public float[] shape(String blockId, Map<String, String> properties) {
			if (blockId.endsWith("candle")) return new float[] {7 / 16f, 0, 7 / 16f, 9 / 16f, 6 / 16f, 9 / 16f};
			if (blockId.endsWith("torch")) return new float[] {6 / 16f, 0, 6 / 16f, 10 / 16f, 10 / 16f, 10 / 16f};
			return null;
		}
	};

	private static List<String> english(List<Message> messages) {
		return messages.stream().map(Message::toFallbackString).toList();
	}

	@Test
	void textPartsNeedPermission() {
		List<String> problems = english(BlueprintValidator.validate(parseOk(fixture("desk.txt")), settings,
				node -> !node.equals(Permissions.TEXT), SHAPES));
		assertTrue(problems.stream().anyMatch(p -> p.contains("text displays need permission")), problems.toString());
	}

	@Test
	void realShapesKeepStretchedCandlesWithinLimits() {
		PermissionView textOnly = node -> node.equals(Permissions.TEXT);
		List<String> withShapes = english(BlueprintValidator.validate(parseOk(fixture("desk.txt")), settings, textOnly, SHAPES));
		assertEquals(List.of(), withShapes);
		List<String> fullCubes = english(BlueprintValidator.validate(parseOk(fixture("desk.txt")), settings, textOnly, BlueprintValidator.BlockInfo.NONE));
		assertTrue(fullCubes.stream().anyMatch(p -> p.contains("blocks long")), "a full cube stretched 5× is too long: " + fullCubes);
	}

	@Test
	void sizeLimitAndBypass() {
		BlueprintModel wide = parseOk("summon block_display ~ ~ ~ {block_state:{Name:\"stone\"}}\nsummon block_display ~7 ~ ~ {block_state:{Name:\"stone\"}}");
		assertTrue(english(BlueprintValidator.validate(wide, settings, PermissionView.DEFAULTS, SHAPES)).get(0).startsWith("Model is 8.0×1.0×1.0"));
		assertEquals(List.of(), english(BlueprintValidator.validate(wide, settings, node -> node.equals(Permissions.BYPASS_BLUEPRINT_SIZE), SHAPES)));
	}

	@Test
	void restrictedBlocksGlowAndStates() {
		BlueprintModel model = parseOk(String.join("\n",
				"summon block_display ~ ~ ~ {block_state:{Name:\"minecraft:barrier\"}}",
				"summon block_display ~ ~ ~ {block_state:{Name:\"stone\"},Glowing:1b}",
				"summon block_display ~ ~ ~ {block_state:{Name:\"oak_leaves\",Properties:{distance:\"7\",persistent:\"false\",waterlogged:\"false\"}}}",
				"summon block_display ~ ~ ~ {block_state:{Name:\"oak_leaves\",Properties:{distance:\"1\",persistent:\"true\"}}}"));
		BlueprintValidator.BlockInfo leafDefaults = (block, key) -> switch (key) {
			case "distance" -> "7";
			case "persistent" -> "false";
			default -> null;
		};
		List<String> problems = english(BlueprintValidator.validate(model, settings, PermissionView.DEFAULTS, leafDefaults));
		assertEquals(List.of(
				"Part 1: barrier is restricted",
				"Part 2: glowing needs permission",
				"Part 4: block state distance: 1 needs permission"), problems);
		assertEquals(List.of(), english(BlueprintValidator.validate(model, settings, PermissionView.ALL, leafDefaults)));
	}

	@Test
	void displayMetaRoundTrips() {
		DisplayMeta meta = new DisplayMeta(UUID.randomUUID(), new float[] {0.5f, 0.5f, 0.5f}, UUID.randomUUID(), "{id:\"minecraft:stone\"}");
		DisplayMeta again = DisplayMeta.deserialize(meta.serialize());
		assertEquals(meta.owner(), again.owner());
		assertEquals(meta.groupId(), again.groupId());
		assertEquals(meta.refund(), again.refund());
		assertArrayEquals(meta.home(), again.home());
		assertNull(DisplayMeta.deserialize("not json"));
		assertNull(DisplayMeta.deserialize(null));
	}

	@Test
	void settingsFillMissingKeys() throws Exception {
		Path file = Files.createTempFile("wrench-display", ".json");
		Files.writeString(file, "{\"survival\":{\"maxOffset\":2.5},\"unknown\":1}");
		Settings loaded = Settings.load(file);
		assertEquals(2.5, loaded.survival.maxOffset);
		assertEquals(64, loaded.survival.maxDisplaysPerChunk);
		assertTrue(Files.readString(file).contains("maxDisplaysPerChunk"), "defaults are written back");
		Files.delete(file);
	}
}
