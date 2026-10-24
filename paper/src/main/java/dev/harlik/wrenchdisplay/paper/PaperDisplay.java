package dev.harlik.wrenchdisplay.paper;

import dev.harlik.wrenchdisplay.common.DisplayMeta;
import dev.harlik.wrenchdisplay.common.edit.DisplayKind;
import dev.harlik.wrenchdisplay.common.edit.EditableDisplay;
import org.bukkit.Color;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Transformation;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A Bukkit display entity seen through the shared edit logic. */
final class PaperDisplay implements EditableDisplay {
	/** Ticks over which the client interpolates an edit, so steps look smooth instead of jumping. */
	private static final int EDIT_INTERPOLATION_TICKS = 2;

	private final Display entity;
	private final DisplayKind kind;
	private final DisplayMeta meta;

	PaperDisplay(Display entity) {
		this.entity = entity;
		this.kind = kindOf(entity);
		this.meta = DisplayStore.get(entity);
	}

	static DisplayKind kindOf(Display entity) {
		if (entity instanceof BlockDisplay) return DisplayKind.BLOCK;
		if (entity instanceof ItemDisplay) return DisplayKind.ITEM;
		return DisplayKind.TEXT;
	}

	/** The full matrix; the entity stores it split into translation / rotation / scale / rotation. */
	static Matrix4f matrix(Display entity) {
		Transformation t = entity.getTransformation();
		return new Matrix4f()
				.translate(t.getTranslation())
				.rotate(t.getLeftRotation())
				.scale(t.getScale())
				.rotate(t.getRightRotation());
	}

	Display entity() {
		return entity;
	}

	DisplayMeta meta() {
		return meta;
	}

	@Override
	public DisplayKind kind() {
		return kind;
	}

	@Override
	public Vector3f home() {
		return meta == null ? null : meta.homeVector();
	}

	@Override
	public Matrix4f transformation() {
		return matrix(entity);
	}

	@Override
	public void setTransformation(Matrix4f matrix) {
		entity.setInterpolationDelay(0);
		entity.setInterpolationDuration(EDIT_INTERPOLATION_TICKS);
		entity.setTransformationMatrix(matrix);
	}

	// --- block state ---------------------------------------------------------------------------

	@Override
	public List<String> stateKeys() {
		return entity instanceof BlockDisplay block ? new ArrayList<>(BlockStates.properties(block.getBlock()).keySet()) : List.of();
	}

	@Override
	public List<String> stateValues(String key) {
		return entity instanceof BlockDisplay block ? BlockStates.values(block.getBlock().getMaterial(), key) : List.of();
	}

	@Override
	public String stateValue(String key) {
		return entity instanceof BlockDisplay block ? BlockStates.properties(block.getBlock()).get(key) : null;
	}

	@Override
	public boolean setStateValue(String key, String value) {
		if (!(entity instanceof BlockDisplay block)) return false;
		BlockData next = BlockStates.with(block.getBlock(), key, value);
		if (next == null) return false;
		block.setBlock(next);
		return true;
	}

	// --- item ----------------------------------------------------------------------------------

	@Override
	public String itemTransform() {
		return entity instanceof ItemDisplay item ? item.getItemDisplayTransform().name().toLowerCase(Locale.ROOT) : null;
	}

	@Override
	public void setItemTransform(String mode) {
		if (!(entity instanceof ItemDisplay item)) return;
		for (ItemDisplay.ItemDisplayTransform transform : ItemDisplay.ItemDisplayTransform.values()) {
			if (transform.name().equalsIgnoreCase(mode)) {
				item.setItemDisplayTransform(transform);
				return;
			}
		}
	}

	// --- shared properties ---------------------------------------------------------------------

	@Override
	public String billboard() {
		return entity.getBillboard().name().toLowerCase(Locale.ROOT);
	}

	@Override
	public void setBillboard(String mode) {
		for (Display.Billboard billboard : Display.Billboard.values()) {
			if (billboard.name().equalsIgnoreCase(mode)) {
				entity.setBillboard(billboard);
				return;
			}
		}
	}

	@Override
	public float shadowRadius() {
		return entity.getShadowRadius();
	}

	@Override
	public void setShadowRadius(float radius) {
		entity.setShadowRadius(radius);
	}

	@Override
	public float shadowStrength() {
		return entity.getShadowStrength();
	}

	@Override
	public void setShadowStrength(float strength) {
		entity.setShadowStrength(strength);
	}

	@Override
	public int[] brightness() {
		Display.Brightness brightness = entity.getBrightness();
		return brightness == null ? null : new int[] {brightness.getBlockLight(), brightness.getSkyLight()};
	}

	@Override
	public void setBrightness(int[] blockAndSky) {
		entity.setBrightness(blockAndSky == null ? null : new Display.Brightness(blockAndSky[0], blockAndSky[1]));
	}

	@Override
	public boolean glowing() {
		return entity.isGlowing();
	}

	@Override
	public void setGlowing(boolean glowing) {
		entity.setGlowing(glowing);
	}

	@Override
	public int glowColor() {
		Color color = entity.getGlowColorOverride();
		return color == null ? -1 : color.asRGB();
	}

	@Override
	public void setGlowColor(int rgb) {
		entity.setGlowColorOverride(rgb < 0 ? null : Color.fromRGB(rgb));
	}

	@Override
	public float viewRange() {
		return entity.getViewRange();
	}

	@Override
	public void setViewRange(float range) {
		entity.setViewRange(range);
	}
}
