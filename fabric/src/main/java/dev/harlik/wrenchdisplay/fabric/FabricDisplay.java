package dev.harlik.wrenchdisplay.fabric;

import com.mojang.math.Transformation;
import dev.harlik.wrenchdisplay.common.DisplayMeta;
import dev.harlik.wrenchdisplay.common.edit.DisplayKind;
import dev.harlik.wrenchdisplay.common.edit.EditableDisplay;
import dev.harlik.wrenchdisplay.fabric.mixin.DisplayAccessor;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** A vanilla display entity seen through the shared edit logic. */
public final class FabricDisplay implements EditableDisplay {
	/** Ticks over which the client interpolates an edit, so steps look smooth instead of jumping. */
	private static final int EDIT_INTERPOLATION_TICKS = 2;

	private final Display entity;
	private final DisplayKind kind;
	private final DisplayMeta meta;

	public FabricDisplay(Display entity) {
		this.entity = entity;
		this.kind = kindOf(entity);
		this.meta = DisplayStore.get(entity);
	}

	public static DisplayKind kindOf(Display entity) {
		if (entity instanceof Display.BlockDisplay) return DisplayKind.BLOCK;
		if (entity instanceof Display.ItemDisplay) return DisplayKind.ITEM;
		return DisplayKind.TEXT;
	}

	public Display entity() {
		return entity;
	}

	public DisplayMeta meta() {
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
		return DisplayAccessor.wrenchDisplay$createTransformation(entity.getEntityData()).getMatrixCopy();
	}

	@Override
	public void setTransformation(Matrix4f matrix) {
		entity.setTransformationInterpolationDelay(0);
		entity.setTransformationInterpolationDuration(EDIT_INTERPOLATION_TICKS);
		entity.setTransformation(new Transformation(matrix));
	}

	// --- block state ---------------------------------------------------------------------------

	@Override
	public List<String> stateKeys() {
		if (!(entity instanceof Display.BlockDisplay block)) return List.of();
		List<String> keys = new ArrayList<>();
		for (Property<?> property : block.getBlockState().getProperties()) {
			keys.add(property.getName());
		}
		return keys;
	}

	@Override
	public List<String> stateValues(String key) {
		Property<?> property = property(key);
		return property == null ? List.of() : names(property);
	}

	@Override
	public String stateValue(String key) {
		Property<?> property = property(key);
		return property == null ? null : valueName(((Display.BlockDisplay) entity).getBlockState(), property);
	}

	@Override
	public boolean setStateValue(String key, String value) {
		Property<?> property = property(key);
		if (property == null) return false;
		Display.BlockDisplay block = (Display.BlockDisplay) entity;
		Optional<BlockState> next = with(block.getBlockState(), property, value);
		next.ifPresent(block::setBlockState);
		return next.isPresent();
	}

	private Property<?> property(String key) {
		if (!(entity instanceof Display.BlockDisplay block)) return null;
		for (Property<?> property : block.getBlockState().getProperties()) {
			if (property.getName().equals(key)) return property;
		}
		return null;
	}

	private static <T extends Comparable<T>> List<String> names(Property<T> property) {
		List<String> out = new ArrayList<>();
		for (T value : property.getPossibleValues()) out.add(property.getName(value));
		return out;
	}

	private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
		return property.getName(state.getValue(property));
	}

	private static <T extends Comparable<T>> Optional<BlockState> with(BlockState state, Property<T> property, String value) {
		return property.getValue(value).map(v -> state.setValue(property, v));
	}

	// --- item ----------------------------------------------------------------------------------

	@Override
	public String itemTransform() {
		return entity instanceof Display.ItemDisplay item ? item.getItemTransform().getSerializedName() : null;
	}

	@Override
	public void setItemTransform(String mode) {
		if (!(entity instanceof Display.ItemDisplay item)) return;
		for (ItemDisplayContext context : ItemDisplayContext.values()) {
			if (context.getSerializedName().equals(mode)) {
				item.setItemTransform(context);
				return;
			}
		}
	}

	// --- shared properties ---------------------------------------------------------------------

	@Override
	public String billboard() {
		return entity.getBillboardConstraints().getSerializedName();
	}

	@Override
	public void setBillboard(String mode) {
		for (Display.BillboardConstraints constraints : Display.BillboardConstraints.values()) {
			if (constraints.getSerializedName().equals(mode)) {
				entity.setBillboardConstraints(constraints);
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
		Brightness brightness = entity.getBrightnessOverride();
		return brightness == null ? null : new int[] {brightness.block(), brightness.sky()};
	}

	@Override
	public void setBrightness(int[] blockAndSky) {
		entity.setBrightnessOverride(blockAndSky == null ? null : new Brightness(blockAndSky[0], blockAndSky[1]));
	}

	@Override
	public boolean glowing() {
		return entity.hasGlowingTag();
	}

	@Override
	public void setGlowing(boolean glowing) {
		entity.setGlowingTag(glowing);
	}

	@Override
	public int glowColor() {
		return entity.getGlowColorOverride();
	}

	@Override
	public void setGlowColor(int rgb) {
		entity.setGlowColorOverride(rgb);
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
