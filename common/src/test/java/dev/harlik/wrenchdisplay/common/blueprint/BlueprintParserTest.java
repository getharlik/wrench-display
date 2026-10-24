package dev.harlik.wrenchdisplay.common.blueprint;

import dev.harlik.wrenchdisplay.common.edit.DisplayKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class BlueprintParserTest {
	private static final float EPS = 1e-5f;

	static String fixture(String name) {
		try (var in = BlueprintParserTest.class.getResourceAsStream("/models/" + name)) {
			assertNotNull(in, "missing fixture " + name);
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Every .txt in the fixture folder, so more exported models can simply be dropped in. */
	static Stream<String> allFixtures() throws IOException, URISyntaxException {
		Path dir = Path.of(BlueprintParserTest.class.getResource("/models").toURI());
		try (Stream<Path> files = Files.list(dir)) {
			return files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".txt")).sorted().toList().stream();
		}
	}

	static BlueprintModel parseOk(String text) {
		BlueprintParser.Result result = BlueprintParser.parse(text, BlueprintParser.Limits.UNLIMITED);
		assertTrue(result.isOk(), () -> "parse failed: " + result.error().toFallbackString() + " at " + result.errorPosition());
		return result.model();
	}

	@ParameterizedTest
	@MethodSource("allFixtures")
	void everyFixtureParses(String name) {
		BlueprintModel model = parseOk(fixture(name));
		assertFalse(model.parts().isEmpty());
		for (BlueprintPart part : model.parts()) {
			assertEquals(16, part.matrix().length);
			for (float v : part.matrix()) {
				assertTrue(Float.isFinite(v), name + " has a non-finite matrix value");
			}
		}
	}

	@Test
	void deskPartCountsAndKinds() {
		BlueprintModel model = parseOk(fixture("desk.txt"));
		Map<DisplayKind, Long> kinds = model.parts().stream().collect(Collectors.groupingBy(BlueprintPart::kind, Collectors.counting()));
		assertEquals(8L, kinds.get(DisplayKind.BLOCK));
		assertEquals(8L, kinds.get(DisplayKind.ITEM));
		assertEquals(2L, kinds.get(DisplayKind.TEXT));
	}

	@Test
	void summonOffsetIsFoldedIntoTranslation() {
		BlueprintPart first = parseOk(fixture("desk.txt")).parts().get(0);
		// transformation [1,0,0,0.75, 0,..,1.25, 0,..,0.5, 0,0,0,1] summoned at ~-0.5 ~-0.5 ~-0.5
		assertEquals(0.25f, first.matrix()[3], EPS);
		assertEquals(0.75f, first.matrix()[7], EPS);
		assertEquals(0f, first.matrix()[11], EPS);
		// the rotation part (tipped 15° about X) is kept untouched
		assertEquals(0.9659258f, first.matrix()[5], EPS);
		assertEquals(0.258819f, first.matrix()[9], EPS);
	}

	@Test
	void blockStatesUseIdAndPropertiesDialect() {
		List<BlueprintPart> blocks = parseOk(fixture("desk.txt")).parts().stream().filter(p -> p.kind() == DisplayKind.BLOCK).toList();
		BlueprintPart.BlockSpec candle = blocks.get(0).block();
		assertEquals("minecraft:brown_candle", candle.id());
		assertEquals(Map.of("candles", "1", "lit", "false", "waterlogged", "false"), candle.properties());
		assertTrue(blocks.stream().anyMatch(p -> p.block().id().equals("minecraft:torch") && p.block().properties().isEmpty()));
		assertTrue(blocks.stream().anyMatch(p -> p.block().properties().equals(Map.of("facing", "west", "half", "upper", "waterlogged", "false"))));
	}

	@Test
	void emptyProfileIdIsDropped() {
		List<BlueprintPart.ItemSpec> heads = parseOk(fixture("desk.txt")).parts().stream()
				.filter(p -> p.kind() == DisplayKind.ITEM && p.item().id().equals("minecraft:player_head"))
				.map(BlueprintPart::item).toList();
		assertEquals(5, heads.size());
		for (BlueprintPart.ItemSpec head : heads) {
			assertFalse(head.componentsSnbt().contains("[I;]"), "empty uuid must be removed: " + head.componentsSnbt());
		}
		// heads with a valid uuid keep it
		assertTrue(heads.get(0).componentsSnbt().contains("[I;12345,-67890,13579,-24680]"));
	}

	@Test
	void headsAreMatchedBySkin() {
		Map<MaterialKey, Integer> materials = parseOk(fixture("desk.txt")).materials();
		Map<MaterialKey, Integer> heads = materials.entrySet().stream()
				.filter(e -> e.getKey().id().equals("minecraft:player_head"))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		assertEquals(3, heads.size(), "5 heads use 3 distinct skins");
		assertEquals(5, heads.values().stream().mapToInt(Integer::intValue).sum());
		assertTrue(heads.keySet().stream().allMatch(k -> k.matchKey().startsWith("skin:")));
		// three heads share one skin: with and without uuid, and with a different timestamp in the texture data
		assertEquals(3, heads.get(MaterialKey.item("minecraft:player_head",
				"skin:4f1303722ffb058bdff28d8194c8501db2e131da3309a8e7a1e1c54ad137f2a6")));
		// plain items have no match key and text parts cost nothing
		assertEquals(2, materials.get(MaterialKey.item("minecraft:tripwire_hook", null)));
		assertEquals(1, materials.get(MaterialKey.item("minecraft:creeper_banner_pattern", null)));
		assertEquals(8, materials.entrySet().stream().filter(e -> e.getKey().type() == MaterialKey.Type.BLOCK)
				.mapToInt(Map.Entry::getValue).sum());
	}

	@Test
	void textDisplaysKeepJsonAndRoundFloatLineWidth() {
		List<BlueprintPart.TextSpec> texts = parseOk(fixture("desk.txt")).parts().stream()
				.filter(p -> p.kind() == DisplayKind.TEXT).map(BlueprintPart::text).toList();
		BlueprintPart.TextSpec text = texts.get(0);
		assertEquals(221, text.lineWidth());
		assertEquals(0, text.background());
		assertTrue(text.textJson().startsWith("[{\"text\":\"●\",\"color\":\"#F2B33D\",\"bold\":true"), text.textJson());
		assertTrue(text.textJson().contains("\"font\":\"minecraft:uniform\""));
	}

	@Test
	void multipleCommandsMergeIntoOneModel() {
		int single = parseOk(fixture("desk.txt")).parts().size();
		int small = parseOk(fixture("two_heads.txt")).parts().size();
		assertEquals(2, small);
		BlueprintModel merged = parseOk(fixture("desk.txt") + "\n\n" + fixture("two_heads.txt"));
		assertEquals(single + small, merged.parts().size());
	}

	@Test
	void boundsCoverTheModel() {
		float[] size = parseOk(fixture("desk.txt")).size();
		for (float s : size) {
			assertTrue(s > 1 && s < 10, "unexpected model size " + s);
		}
	}

	@Test
	void decomposedTransformAxisAngleAndLegacyItems() {
		String cmd = "execute as @p run summon minecraft:item_display ~1 ~ ~ {item:{id:\"diamond\",Count:3b,tag:{display:{Name:'\"x\"'}}},"
				+ "transformation:{translation:[0f,1f,0f],left_rotation:{angle:1.5707964f,axis:[0f,1f,0f]},scale:[2f,2f,2f],right_rotation:[0f,0f,0f,1f]},"
				+ "brightness:{block:15,sky:15},Glowing:1b,glow_color_override:16711680,billboard:\"center\",item_display:\"head\"}";
		BlueprintPart part = parseOk(cmd).parts().get(0);
		assertEquals("minecraft:diamond", part.item().id());
		assertEquals(3, part.item().count());
		assertNotNull(part.item().legacyTagSnbt());
		assertTrue(part.item().matchKey().startsWith("tag:"));
		float[] m = part.matrix();
		// rotate 90° about Y then scale 2: x axis maps to -z
		assertEquals(0, m[0], EPS);
		assertEquals(-2, m[8], EPS);
		assertEquals(1, m[3], EPS);
		assertEquals(1, m[7], EPS);
		assertEquals(15, part.props().brightnessBlock());
		assertTrue(part.props().isGlowing());
		assertEquals(16711680, part.props().glowColor());
		assertEquals("center", part.props().billboard());
		assertEquals("head", part.props().itemTransform());
	}

	@Test
	void legacyJsonStringText() {
		BlueprintPart part = parseOk("summon text_display ~ ~ ~ {text:'{\"text\":\"Hi\",\"bold\":true}',line_width:100}").parts().get(0);
		assertEquals("{\"text\":\"Hi\",\"bold\":true}", part.text().textJson());
		assertEquals(100, part.text().lineWidth());
	}

	@Test
	void absoluteCoordinatesAreRebased() {
		BlueprintModel model = parseOk("summon block_display 100 64 -20 {block_state:{Name:\"minecraft:stone\"}}\n"
				+ "summon block_display 101 64 -20 {block_state:{Name:\"minecraft:dirt\"}}");
		assertEquals(0, model.parts().get(0).matrix()[3], EPS);
		assertEquals(1, model.parts().get(1).matrix()[3], EPS);
	}

	@Test
	void errors() {
		BlueprintParser.Limits limits = new BlueprintParser.Limits(1000, 1);
		assertFalse(BlueprintParser.parse("  ", limits).isOk());
		assertEquals("No summon command for a block, item or text display was found",
				BlueprintParser.parse("say hello", limits).error().toFallbackString());
		assertTrue(BlueprintParser.parse("x".repeat(1001), limits).error().toFallbackString().contains("limit is 1000"));
		assertTrue(BlueprintParser.parse(fixture("two_heads.txt"), new BlueprintParser.Limits(100_000, 1)).error().toFallbackString()
				.contains("2 parts; the limit is 1"));
		assertTrue(BlueprintParser.parse("summon block_display ^ ^ ^ {}", limits).error().toFallbackString().contains("Local coordinates"));
		assertEquals("The commands contain no visible displays",
				BlueprintParser.parse("summon block_display ~ ~ ~ {Passengers:[]}", limits).error().toFallbackString());

		BlueprintParser.Result broken = BlueprintParser.parse("summon block_display ~ ~ ~ {block_state:{Name:\"stone\"", limits);
		assertFalse(broken.isOk());
		assertTrue(broken.errorPosition() > 0);
	}
}
