package dev.harlik.wrenchdisplay.common.blueprint;

import dev.harlik.wrenchdisplay.common.edit.DisplayKind;

import java.util.Map;

/**
 * One display entity of a blueprint, already flattened out of the passenger tree.
 *
 * @param kind      which display entity to spawn
 * @param matrix    row-major 4×4 transformation relative to the paste origin ("~ ~ ~" of the command),
 *                  with the summon offset already folded into the translation. May contain shear.
 * @param block     set for {@link DisplayKind#BLOCK}
 * @param item      set for {@link DisplayKind#ITEM}
 * @param text      set for {@link DisplayKind#TEXT}
 * @param props     common display properties (null fields = entity default)
 */
public record BlueprintPart(DisplayKind kind, float[] matrix, BlockSpec block, ItemSpec item, TextSpec text, DisplayProps props) {
	/** @param properties block state properties as strings, e.g. {@code candles -> "1"} */
	public record BlockSpec(String id, Map<String, String> properties) {
	}

	/**
	 * @param componentsSnbt sanitized data components as SNBT ({@code {"minecraft:profile":{...}}}), or null
	 * @param legacyTagSnbt  pre-1.20.5 {@code tag:{...}} as SNBT, or null; platforms run it through their data fixer
	 * @param matchKey       how survival matching compares this item against the inventory, see {@link MaterialKey}
	 */
	public record ItemSpec(String id, int count, String componentsSnbt, String legacyTagSnbt, String matchKey) {
	}

	/**
	 * @param textJson   the text component as JSON
	 * @param lineWidth  rounded to an int (editors sometimes export floats)
	 */
	public record TextSpec(String textJson, Integer lineWidth, Integer background, Integer textOpacity,
			Boolean shadow, Boolean seeThrough, Boolean defaultBackground, String alignment) {
	}

	public record DisplayProps(Integer brightnessBlock, Integer brightnessSky, Boolean glowing, Integer glowColor,
			String billboard, Float viewRange, Float shadowRadius, Float shadowStrength, String itemTransform) {
		public static final DisplayProps DEFAULT = new DisplayProps(null, null, null, null, null, null, null, null, null);

		public boolean hasBrightnessOverride() {
			return brightnessBlock != null || brightnessSky != null;
		}

		public boolean isGlowing() {
			return Boolean.TRUE.equals(glowing);
		}
	}
}
