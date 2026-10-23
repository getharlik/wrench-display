package dev.harlik.wrenchdisplay.fabric.blueprint;

import dev.harlik.wrenchdisplay.common.blueprint.BlueprintPart;
import dev.harlik.wrenchdisplay.common.blueprint.BlueprintValidator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;
import java.util.Optional;

/** Default states and outline shapes from the block registry, for {@link BlueprintValidator}. */
public final class FabricBlockInfo implements BlueprintValidator.BlockInfo {
	public static final FabricBlockInfo INSTANCE = new FabricBlockInfo();

	private FabricBlockInfo() {
	}

	@Override
	public String defaultValue(String blockId, String key) {
		Optional<Block> block = block(blockId);
		if (block.isEmpty()) return null;
		Property<?> property = block.get().getStateDefinition().getProperty(key);
		return property == null ? null : name(block.get().defaultBlockState(), property);
	}

	private static <T extends Comparable<T>> String name(BlockState state, Property<T> property) {
		return property.getName(state.getValue(property));
	}

	@Override
	public float[] shape(String blockId, Map<String, String> properties) {
		if (block(blockId).isEmpty()) return null;
		BlockState state = PreparedBlueprint.blockState(new BlueprintPart.BlockSpec(blockId, properties));
		VoxelShape shape = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
		if (shape.isEmpty()) return null;
		AABB box = shape.bounds();
		return new float[] {(float) box.minX, (float) box.minY, (float) box.minZ, (float) box.maxX, (float) box.maxY, (float) box.maxZ};
	}

	private static Optional<Block> block(String blockId) {
		Identifier id = Identifier.tryParse(blockId);
		return id == null ? Optional.empty() : BuiltInRegistries.BLOCK.getOptional(id);
	}
}
