package dev.harlik.wrenchdisplay.paper;

import dev.harlik.wrenchdisplay.common.blueprint.BlueprintModel;
import dev.harlik.wrenchdisplay.common.blueprint.BlueprintPart;
import dev.harlik.wrenchdisplay.common.edit.DisplayKind;
import dev.harlik.wrenchdisplay.common.math.Matrices;
import dev.harlik.wrenchdisplay.common.snbt.SnbtReader;
import dev.harlik.wrenchdisplay.common.snbt.SnbtWriter;
import dev.harlik.wrenchdisplay.common.text.Message;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A parsed blueprint turned into Bukkit objects once (block data, item stacks, text), so the preview and
 * placement don't rebuild them every tick.
 */
final class PreparedBlueprint {
	private final BlueprintModel model;
	private final List<Part> parts;
	private final List<Message> problems;

	record Part(BlueprintPart source, BlockData block, ItemStack item, Component text) {
		DisplayKind kind() {
			return source.kind();
		}
	}

	private PreparedBlueprint(BlueprintModel model, List<Part> parts, List<Message> problems) {
		this.model = model;
		this.parts = parts;
		this.problems = problems;
	}

	BlueprintModel model() {
		return model;
	}

	List<Part> parts() {
		return parts;
	}

	/** Parts that could not be built (unknown ids, invalid data); empty when everything is fine. */
	List<Message> problems() {
		return problems;
	}

	private static final class PartProblem extends IllegalArgumentException {
		final Message message;

		PartProblem(Message message) {
			super(message.toFallbackString());
			this.message = message;
		}
	}

	static PreparedBlueprint prepare(BlueprintModel model) {
		List<Part> parts = new ArrayList<>();
		List<Message> problems = new ArrayList<>();
		for (int i = 0; i < model.parts().size(); i++) {
			BlueprintPart part = model.parts().get(i);
			try {
				switch (part.kind()) {
					case BLOCK -> parts.add(new Part(part, blockData(part.block()), null, null));
					case ITEM -> parts.add(new Part(part, null, itemStack(part.item()), null));
					case TEXT -> parts.add(new Part(part, null, null, text(part.text())));
				}
			} catch (PartProblem e) {
				problems.add(Message.of("problem.part", "Part %s: %s", i + 1, e.message));
			}
		}
		return new PreparedBlueprint(model, parts, problems);
	}

	static BlockData blockData(BlueprintPart.BlockSpec spec) {
		StringBuilder text = new StringBuilder(spec.id());
		if (!spec.properties().isEmpty()) {
			text.append('[');
			spec.properties().forEach((k, v) -> text.append(k).append('=').append(v).append(','));
			text.setLength(text.length() - 1);
			text.append(']');
		}
		try {
			return Bukkit.createBlockData(text.toString());
		} catch (IllegalArgumentException e) {
			// Unknown property values: fall back to the block's default state.
			try {
				return Bukkit.createBlockData(spec.id());
			} catch (IllegalArgumentException unknown) {
				throw new PartProblem(Message.of("problem.unknown_block", "unknown block %s", spec.id()));
			}
		}
	}

	/** Item command syntax {@code id[component=value,…]} built from the sanitized components. */
	@SuppressWarnings("unchecked")
	static ItemStack itemStack(BlueprintPart.ItemSpec spec) {
		StringBuilder text = new StringBuilder(spec.id());
		if (spec.componentsSnbt() != null) {
			Map<String, Object> components = (Map<String, Object>) SnbtReader.parse(spec.componentsSnbt());
			if (!components.isEmpty()) {
				text.append('[');
				components.forEach((k, v) -> text.append(k).append('=').append(SnbtWriter.write(v)).append(','));
				text.setLength(text.length() - 1);
				text.append(']');
			}
		}
		ItemStack stack;
		try {
			stack = Bukkit.getItemFactory().createItemStack(text.toString());
		} catch (IllegalArgumentException e) {
			try {
				Bukkit.getItemFactory().createItemStack(spec.id());
			} catch (IllegalArgumentException unknown) {
				throw new PartProblem(Message.of("problem.unknown_item", "unknown item %s", spec.id()));
			}
			throw new PartProblem(Message.of("problem.item_data", "unreadable item data for %s", new Message.ItemName(spec.id())));
		}
		stack.setAmount(Math.max(1, Math.min(99, spec.count())));
		return stack;
	}

	private static Component text(BlueprintPart.TextSpec spec) {
		try {
			return GsonComponentSerializer.gson().deserialize(spec.textJson());
		} catch (RuntimeException e) {
			throw new PartProblem(Message.of("problem.text_data", "unreadable text"));
		}
	}

	/**
	 * Spawns the parts at {@code origin} with the model turned by {@code quarterTurns} × 90° around the vertical
	 * axis through it. {@code setup} runs on each entity before it is added to the world.
	 */
	List<Display> spawn(World world, Location origin, int quarterTurns, Consumer<Display> setup) {
		Matrix4f rotation = new Matrix4f().rotationY((float) (-Math.PI / 2 * quarterTurns));
		Location at = origin.clone();
		at.setYaw(0);
		at.setPitch(0);
		List<Display> out = new ArrayList<>();
		for (Part part : parts) {
			Matrix4f matrix = new Matrix4f(rotation).mul(Matrices.fromRowMajor(part.source().matrix()));
			Consumer<Display> common = d -> {
				d.setTransformationMatrix(matrix);
				applyProps(d, part.source().props());
				setup.accept(d);
			};
			Display display = switch (part.kind()) {
				case BLOCK -> world.spawn(at, BlockDisplay.class, d -> {
					d.setBlock(part.block());
					common.accept(d);
				});
				case ITEM -> world.spawn(at, ItemDisplay.class, d -> {
					d.setItemStack(part.item().clone());
					String mode = part.source().props().itemTransform();
					if (mode != null) {
						for (ItemDisplay.ItemDisplayTransform transform : ItemDisplay.ItemDisplayTransform.values()) {
							if (transform.name().equalsIgnoreCase(mode)) d.setItemDisplayTransform(transform);
						}
					}
					common.accept(d);
				});
				case TEXT -> world.spawn(at, TextDisplay.class, d -> {
					applyText(d, part);
					common.accept(d);
				});
			};
			out.add(display);
		}
		return out;
	}

	private static void applyText(TextDisplay display, Part part) {
		BlueprintPart.TextSpec spec = part.source().text();
		display.text(part.text());
		if (spec.lineWidth() != null) display.setLineWidth(spec.lineWidth());
		if (spec.background() != null) display.setBackgroundColor(Color.fromARGB(spec.background()));
		if (spec.textOpacity() != null) display.setTextOpacity(spec.textOpacity().byteValue());
		if (spec.shadow() != null) display.setShadowed(spec.shadow());
		if (spec.seeThrough() != null) display.setSeeThrough(spec.seeThrough());
		if (spec.defaultBackground() != null) display.setDefaultBackground(spec.defaultBackground());
		if (spec.alignment() != null) {
			for (TextDisplay.TextAlignment alignment : TextDisplay.TextAlignment.values()) {
				if (alignment.name().equalsIgnoreCase(spec.alignment())) display.setAlignment(alignment);
			}
		}
	}

	private static void applyProps(Display display, BlueprintPart.DisplayProps props) {
		if (props.hasBrightnessOverride()) {
			display.setBrightness(new Display.Brightness(
					props.brightnessBlock() == null ? 0 : props.brightnessBlock(),
					props.brightnessSky() == null ? 0 : props.brightnessSky()));
		}
		if (props.isGlowing()) display.setGlowing(true);
		if (props.glowColor() != null) display.setGlowColorOverride(Color.fromRGB(props.glowColor() & 0xFFFFFF));
		if (props.viewRange() != null) display.setViewRange(props.viewRange());
		if (props.shadowRadius() != null) display.setShadowRadius(props.shadowRadius());
		if (props.shadowStrength() != null) display.setShadowStrength(props.shadowStrength());
		if (props.billboard() != null) {
			for (Display.Billboard billboard : Display.Billboard.values()) {
				if (billboard.name().equalsIgnoreCase(props.billboard())) display.setBillboard(billboard);
			}
		}
	}
}
