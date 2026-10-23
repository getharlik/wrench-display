package dev.harlik.wrenchdisplay.common.blueprint;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.harlik.wrenchdisplay.common.blueprint.BlueprintPart.BlockSpec;
import dev.harlik.wrenchdisplay.common.blueprint.BlueprintPart.DisplayProps;
import dev.harlik.wrenchdisplay.common.blueprint.BlueprintPart.ItemSpec;
import dev.harlik.wrenchdisplay.common.blueprint.BlueprintPart.TextSpec;
import dev.harlik.wrenchdisplay.common.edit.DisplayKind;
import dev.harlik.wrenchdisplay.common.math.Matrices;
import dev.harlik.wrenchdisplay.common.snbt.SnbtWriter;
import org.joml.AxisAngle4f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Turns scanned summon commands into a flat list of {@link BlueprintPart}s. */
final class PartExtractor {
	private static final Set<String> TEXT_BOOLEAN_KEYS = Set.of("bold", "italic", "underlined", "strikethrough", "obfuscated", "interpret");

	private final List<BlueprintPart> parts = new ArrayList<>();
	private final Double[] absoluteBase = new Double[3];

	List<BlueprintPart> extract(List<CommandScanner.Summon> summons) {
		for (CommandScanner.Summon summon : summons) {
			double[] offset = {
					resolve(summon.x(), 0), resolve(summon.y(), 1), resolve(summon.z(), 2)
			};
			visit(summon.type(), summon.nbt(), offset);
		}
		return parts;
	}

	/** Relative coordinates are offsets from the paste origin; absolute ones are rebased onto the first absolute command. */
	private double resolve(CommandScanner.Coordinate coordinate, int axis) {
		if (coordinate.relative()) {
			return coordinate.value();
		}
		if (absoluteBase[axis] == null) {
			absoluteBase[axis] = coordinate.value();
		}
		return coordinate.value() - absoluteBase[axis];
	}

	@SuppressWarnings("unchecked")
	private void visit(String type, Map<String, Object> nbt, double[] offset) {
		BlueprintPart part = toPart(type, nbt, offset);
		if (part != null) {
			parts.add(part);
		}
		// Passengers ride at their vehicle's position (display entities have no passenger offset).
		if (nbt.get("Passengers") instanceof List<?> passengers) {
			for (Object passenger : passengers) {
				if (passenger instanceof Map<?, ?> map && map.get("id") instanceof String id) {
					String passengerType = id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
					if (passengerType.endsWith("_display")) {
						visit(passengerType, (Map<String, Object>) map, offset);
					}
				}
			}
		}
	}

	private BlueprintPart toPart(String type, Map<String, Object> nbt, double[] offset) {
		BlockSpec block = null;
		ItemSpec item = null;
		TextSpec text = null;
		DisplayKind kind;
		switch (type) {
			case "block_display" -> {
				block = blockSpec(nbt.get("block_state"));
				if (block == null) {
					return null; // empty carrier, like BDEngine's root entity
				}
				kind = DisplayKind.BLOCK;
			}
			case "item_display" -> {
				item = itemSpec(nbt.get("item"));
				if (item == null) {
					return null;
				}
				kind = DisplayKind.ITEM;
			}
			case "text_display" -> {
				text = textSpec(nbt);
				if (text == null) {
					return null;
				}
				kind = DisplayKind.TEXT;
			}
			default -> {
				return null;
			}
		}
		Matrix4f matrix = new Matrix4f().translation((float) offset[0], (float) offset[1], (float) offset[2])
				.mul(transformation(nbt.get("transformation")));
		return new BlueprintPart(kind, Matrices.toRowMajor(matrix), block, item, text, displayProps(nbt));
	}

	// --- transformation ------------------------------------------------------------------------

	@SuppressWarnings("unchecked")
	static Matrix4f transformation(Object value) {
		if (value instanceof List<?> list && list.size() == 16) {
			float[] rowMajor = new float[16];
			for (int i = 0; i < 16; i++) {
				rowMajor[i] = asFloat(list.get(i), 0);
			}
			return Matrices.fromRowMajor(rowMajor);
		}
		if (value instanceof Map<?, ?> map) {
			Map<String, Object> t = (Map<String, Object>) map;
			float[] translation = vector(t.get("translation"), 0);
			float[] scale = vector(t.get("scale"), 1);
			return new Matrix4f()
					.translate(translation[0], translation[1], translation[2])
					.rotate(rotation(t.get("left_rotation")))
					.scale(scale[0], scale[1], scale[2])
					.rotate(rotation(t.get("right_rotation")));
		}
		return new Matrix4f();
	}

	private static float[] vector(Object value, float fallback) {
		float[] out = {fallback, fallback, fallback};
		if (value instanceof List<?> list && list.size() == 3) {
			for (int i = 0; i < 3; i++) out[i] = asFloat(list.get(i), fallback);
		}
		return out;
	}

	private static Quaternionf rotation(Object value) {
		if (value instanceof List<?> list && list.size() == 4) {
			return new Quaternionf(asFloat(list.get(0), 0), asFloat(list.get(1), 0), asFloat(list.get(2), 0), asFloat(list.get(3), 1)).normalize();
		}
		if (value instanceof Map<?, ?> map) {
			float[] axis = vector(map.get("axis"), 0);
			float angle = asFloat(map.get("angle"), 0);
			if (axis[0] != 0 || axis[1] != 0 || axis[2] != 0) {
				return new Quaternionf(new AxisAngle4f(angle, axis[0], axis[1], axis[2]).normalize());
			}
		}
		return new Quaternionf();
	}

	// --- block ---------------------------------------------------------------------------------

	private static BlockSpec blockSpec(Object value) {
		if (!(value instanceof Map<?, ?> state)) {
			return null;
		}
		Object idValue = state.get("Name") != null ? state.get("Name") : state.get("id");
		if (!(idValue instanceof String id) || id.isEmpty()) {
			return null;
		}
		id = namespaced(id);
		if (id.equals("minecraft:air") || id.equals("minecraft:cave_air") || id.equals("minecraft:void_air")) {
			return null;
		}
		Object propsValue = state.get("Properties") != null ? state.get("Properties") : state.get("properties");
		Map<String, String> properties = new LinkedHashMap<>();
		if (propsValue instanceof Map<?, ?> props) {
			props.forEach((k, v) -> properties.put(String.valueOf(k), String.valueOf(v)));
		}
		return new BlockSpec(id, properties);
	}

	// --- item ----------------------------------------------------------------------------------

	@SuppressWarnings("unchecked")
	private static ItemSpec itemSpec(Object value) {
		if (!(value instanceof Map<?, ?> rawItem) || !(rawItem.get("id") instanceof String rawId) || rawId.isEmpty()) {
			return null;
		}
		String id = namespaced(rawId);
		if (id.equals("minecraft:air")) {
			return null;
		}
		Map<String, Object> item = (Map<String, Object>) rawItem;
		int count = Math.max(1, asInt(item.get("count") != null ? item.get("count") : item.get("Count"), 1));

		String componentsSnbt = null;
		String legacySnbt = null;
		String matchKey = null;
		if (item.get("components") instanceof Map<?, ?> rawComponents && !rawComponents.isEmpty()) {
			Map<String, Object> components = sanitizeComponents((Map<String, Object>) rawComponents);
			componentsSnbt = SnbtWriter.write(components);
			String skin = HeadTextures.skinKey(components.get("minecraft:profile"));
			matchKey = skin != null ? "skin:" + skin : "components:" + SnbtWriter.writeCanonical(components);
		}
		if (item.get("tag") instanceof Map<?, ?> rawTag && !rawTag.isEmpty()) {
			Map<String, Object> tag = new LinkedHashMap<>((Map<String, Object>) rawTag);
			if (tag.get("SkullOwner") instanceof Map<?, ?> owner) {
				Map<String, Object> cleanOwner = new LinkedHashMap<>((Map<String, Object>) owner);
				dropInvalidUuid(cleanOwner, "Id");
				tag.put("SkullOwner", cleanOwner);
				if (matchKey == null) {
					String skin = HeadTextures.legacySkinKey(cleanOwner);
					if (skin != null) {
						matchKey = "skin:" + skin;
					}
				}
			}
			legacySnbt = SnbtWriter.write(tag);
			if (matchKey == null) {
				matchKey = "tag:" + SnbtWriter.writeCanonical(tag);
			}
		}
		return new ItemSpec(id, count, componentsSnbt, legacySnbt, matchKey);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> sanitizeComponents(Map<String, Object> raw) {
		Map<String, Object> out = new LinkedHashMap<>();
		raw.forEach((key, value) -> {
			String name = key.startsWith("!") || key.contains(":") ? key : "minecraft:" + key;
			if (name.equals("minecraft:profile") && value instanceof Map<?, ?> profile) {
				Map<String, Object> cleanProfile = new LinkedHashMap<>((Map<String, Object>) profile);
				dropInvalidUuid(cleanProfile, "id");
				value = cleanProfile;
			}
			out.put(name, value);
		});
		return out;
	}

	/** Editors sometimes export {@code id:[I;]}; a UUID must be exactly four ints, otherwise vanilla rejects the item. */
	private static void dropInvalidUuid(Map<String, Object> map, String key) {
		Object id = map.get(key);
		if (id != null && !(id instanceof int[] ints && ints.length == 4) && !(id instanceof String)) {
			map.remove(key);
		}
	}

	// --- text ----------------------------------------------------------------------------------

	private static TextSpec textSpec(Map<String, Object> nbt) {
		Object text = nbt.get("text");
		if (text == null) {
			return null;
		}
		Integer lineWidth = nbt.get("line_width") instanceof Number n ? (int) Math.round(n.doubleValue()) : null;
		return new TextSpec(
				textJson(text),
				lineWidth,
				nbt.get("background") instanceof Number n ? n.intValue() : null,
				nbt.get("text_opacity") instanceof Number n ? n.intValue() : null,
				asBoolean(nbt.get("shadow")),
				asBoolean(nbt.get("see_through")),
				asBoolean(nbt.get("default_background")),
				nbt.get("alignment") instanceof String s ? s : null);
	}

	/** Text may be a JSON string (pre-1.21.5 exports), an SNBT compound/list, or plain text. */
	static String textJson(Object text) {
		if (text instanceof String s) {
			String trimmed = s.trim();
			if (trimmed.startsWith("{") || trimmed.startsWith("[") || trimmed.startsWith("\"")) {
				try {
					return JsonParser.parseString(trimmed).toString();
				} catch (RuntimeException ignored) {
					// not JSON: fall through to literal text
				}
			}
			return new JsonPrimitive(s).toString();
		}
		return toJson(text, null).toString();
	}

	private static JsonElement toJson(Object value, String key) {
		if (value instanceof Map<?, ?> map) {
			JsonObject object = new JsonObject();
			map.forEach((k, v) -> object.add(String.valueOf(k), toJson(v, String.valueOf(k))));
			return object;
		}
		if (value instanceof List<?> list) {
			JsonArray array = new JsonArray();
			list.forEach(v -> array.add(toJson(v, null)));
			return array;
		}
		if (value instanceof Boolean b) {
			return new JsonPrimitive(b);
		}
		if (value instanceof Number n) {
			return key != null && TEXT_BOOLEAN_KEYS.contains(key) ? new JsonPrimitive(n.intValue() != 0) : new JsonPrimitive(n);
		}
		if (value instanceof String s) {
			return new JsonPrimitive(s);
		}
		return JsonNull.INSTANCE;
	}

	// --- shared display properties -------------------------------------------------------------

	private static DisplayProps displayProps(Map<String, Object> nbt) {
		Integer block = null;
		Integer sky = null;
		if (nbt.get("brightness") instanceof Map<?, ?> brightness) {
			block = brightness.get("block") instanceof Number n ? n.intValue() : null;
			sky = brightness.get("sky") instanceof Number n ? n.intValue() : null;
		}
		return new DisplayProps(
				block,
				sky,
				asBoolean(nbt.get("Glowing")),
				nbt.get("glow_color_override") instanceof Number n ? n.intValue() : null,
				nbt.get("billboard") instanceof String s ? s : null,
				nbt.get("view_range") instanceof Number n ? n.floatValue() : null,
				nbt.get("shadow_radius") instanceof Number n ? n.floatValue() : null,
				nbt.get("shadow_strength") instanceof Number n ? n.floatValue() : null,
				nbt.get("item_display") instanceof String s ? s : null);
	}

	// --- helpers -------------------------------------------------------------------------------

	static String namespaced(String id) {
		return id.contains(":") ? id : "minecraft:" + id;
	}

	private static float asFloat(Object value, float fallback) {
		return value instanceof Number n ? n.floatValue() : fallback;
	}

	private static int asInt(Object value, int fallback) {
		return value instanceof Number n ? n.intValue() : fallback;
	}

	private static Boolean asBoolean(Object value) {
		if (value instanceof Boolean b) {
			return b;
		}
		if (value instanceof Number n) {
			return n.intValue() != 0;
		}
		return null;
	}
}
