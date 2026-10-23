package dev.harlik.wrenchdisplay.fabric.blueprint;

import com.google.gson.JsonParser;
import com.mojang.math.Transformation;
import com.mojang.serialization.JsonOps;
import dev.harlik.wrenchdisplay.common.blueprint.BlueprintModel;
import dev.harlik.wrenchdisplay.common.blueprint.BlueprintPart;
import dev.harlik.wrenchdisplay.common.edit.DisplayKind;
import dev.harlik.wrenchdisplay.common.math.Matrices;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A parsed blueprint turned into Minecraft objects once (block states, item stacks, text), so the preview
 * and placement don't rebuild them every tick.
 */
public final class PreparedBlueprint {
	private final BlueprintModel model;
	private final List<Part> parts;
	private final List<String> problems;

	public record Part(BlueprintPart source, BlockState block, ItemStack item, Component text) {
		public DisplayKind kind() {
			return source.kind();
		}
	}

	private PreparedBlueprint(BlueprintModel model, List<Part> parts, List<String> problems) {
		this.model = model;
		this.parts = parts;
		this.problems = problems;
	}

	public BlueprintModel model() {
		return model;
	}

	public List<Part> parts() {
		return parts;
	}

	/** Parts that could not be built (unknown block or item ids, invalid data); empty when everything is fine. */
	public List<String> problems() {
		return problems;
	}

	public static PreparedBlueprint prepare(BlueprintModel model, HolderLookup.Provider registries) {
		List<Part> parts = new ArrayList<>();
		List<String> problems = new ArrayList<>();
		for (int i = 0; i < model.parts().size(); i++) {
			BlueprintPart part = model.parts().get(i);
			try {
				switch (part.kind()) {
					case BLOCK -> parts.add(new Part(part, blockState(part.block()), null, null));
					case ITEM -> parts.add(new Part(part, null, itemStack(part.item(), registries), null));
					case TEXT -> parts.add(new Part(part, null, null, text(part.text(), registries)));
				}
			} catch (IllegalArgumentException e) {
				problems.add("Part " + (i + 1) + ": " + e.getMessage());
			}
		}
		return new PreparedBlueprint(model, parts, problems);
	}

	static BlockState blockState(BlueprintPart.BlockSpec spec) {
		Identifier id = Identifier.tryParse(spec.id());
		Optional<Block> block = id == null ? Optional.empty() : BuiltInRegistries.BLOCK.getOptional(id);
		if (block.isEmpty()) {
			throw new IllegalArgumentException("unknown block " + spec.id());
		}
		BlockState state = block.get().defaultBlockState();
		for (Map.Entry<String, String> entry : spec.properties().entrySet()) {
			Property<?> property = block.get().getStateDefinition().getProperty(entry.getKey());
			if (property != null) {
				state = with(state, property, entry.getValue());
			}
		}
		return state;
	}

	private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, String value) {
		return property.getValue(value).map(v -> state.setValue(property, v)).orElse(state);
	}

	/**
	 * Builds the item from id + sanitized components. Pre-1.20.5 {@code tag:{}} data is not converted; such
	 * parts use the plain item.
	 */
	static ItemStack itemStack(BlueprintPart.ItemSpec spec, HolderLookup.Provider registries) {
		Identifier id = Identifier.tryParse(spec.id());
		if (id == null || BuiltInRegistries.ITEM.getOptional(id).isEmpty()) {
			throw new IllegalArgumentException("unknown item " + spec.id());
		}
		CompoundTag tag = new CompoundTag();
		tag.putString("id", spec.id());
		tag.putInt("count", Math.min(99, spec.count()));
		if (spec.componentsSnbt() != null) {
			try {
				tag.put("components", TagParser.parseCompoundFully(spec.componentsSnbt()));
			} catch (Exception e) {
				throw new IllegalArgumentException("unreadable item data for " + spec.id());
			}
		}
		return ItemStack.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag).result()
				.orElseThrow(() -> new IllegalArgumentException("invalid item data for " + spec.id()));
	}

	private static Component text(BlueprintPart.TextSpec spec, HolderLookup.Provider registries) {
		return ComponentSerialization.CODEC.parse(registries.createSerializationContext(JsonOps.INSTANCE), JsonParser.parseString(spec.textJson()))
				.result().orElseThrow(() -> new IllegalArgumentException("invalid text"));
	}

	/**
	 * Creates (but does not add) the display entities, all at {@code (x, y, z)} with the model turned by
	 * {@code quarterTurns} × 90° around the vertical axis through that point.
	 */
	public List<Display> create(ServerLevel level, double x, double y, double z, int quarterTurns) {
		Matrix4f rotation = new Matrix4f().rotationY((float) (-Math.PI / 2 * quarterTurns));
		List<Display> out = new ArrayList<>();
		for (Part part : parts) {
			Display display = switch (part.kind()) {
				case BLOCK -> {
					Display.BlockDisplay block = EntityTypes.BLOCK_DISPLAY.create(level, EntitySpawnReason.COMMAND);
					if (block != null) block.setBlockState(part.block());
					yield block;
				}
				case ITEM -> {
					Display.ItemDisplay item = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.COMMAND);
					if (item != null) {
						item.setItemStack(part.item().copy());
						String mode = part.source().props().itemTransform();
						if (mode != null) {
							for (ItemDisplayContext context : ItemDisplayContext.values()) {
								if (context.getSerializedName().equals(mode)) item.setItemTransform(context);
							}
						}
					}
					yield item;
				}
				case TEXT -> {
					Display.TextDisplay text = EntityTypes.TEXT_DISPLAY.create(level, EntitySpawnReason.COMMAND);
					if (text != null) applyText(text, part);
					yield text;
				}
			};
			if (display == null) continue;
			display.setPos(x, y, z);
			Matrix4f matrix = new Matrix4f(rotation).mul(Matrices.fromRowMajor(part.source().matrix()));
			display.setTransformation(new Transformation(matrix));
			applyProps(display, part.source().props());
			out.add(display);
		}
		return out;
	}

	private static void applyText(Display.TextDisplay display, Part part) {
		BlueprintPart.TextSpec spec = part.source().text();
		display.setText(part.text());
		if (spec.lineWidth() != null) display.setLineWidth(spec.lineWidth());
		if (spec.background() != null) display.setBackgroundColor(spec.background());
		if (spec.textOpacity() != null) display.setTextOpacity(spec.textOpacity().byteValue());
		byte flags = 0;
		if (Boolean.TRUE.equals(spec.shadow())) flags |= Display.TextDisplay.FLAG_SHADOW;
		if (Boolean.TRUE.equals(spec.seeThrough())) flags |= Display.TextDisplay.FLAG_SEE_THROUGH;
		if (Boolean.TRUE.equals(spec.defaultBackground())) flags |= Display.TextDisplay.FLAG_USE_DEFAULT_BACKGROUND;
		if ("left".equals(spec.alignment())) flags |= Display.TextDisplay.FLAG_ALIGN_LEFT;
		if ("right".equals(spec.alignment())) flags |= Display.TextDisplay.FLAG_ALIGN_RIGHT;
		display.setFlags(flags);
	}

	private static void applyProps(Display display, BlueprintPart.DisplayProps props) {
		if (props.hasBrightnessOverride()) {
			display.setBrightnessOverride(new Brightness(
					props.brightnessBlock() == null ? 0 : props.brightnessBlock(),
					props.brightnessSky() == null ? 0 : props.brightnessSky()));
		}
		if (props.isGlowing()) display.setGlowingTag(true);
		if (props.glowColor() != null) display.setGlowColorOverride(props.glowColor());
		if (props.viewRange() != null) display.setViewRange(props.viewRange());
		if (props.shadowRadius() != null) display.setShadowRadius(props.shadowRadius());
		if (props.shadowStrength() != null) display.setShadowStrength(props.shadowStrength());
		if (props.billboard() != null) {
			for (Display.BillboardConstraints constraints : Display.BillboardConstraints.values()) {
				if (constraints.getSerializedName().equals(props.billboard())) display.setBillboardConstraints(constraints);
			}
		}
	}
}
