package dev.harlik.wrenchdisplay.paper;

import dev.harlik.wrenchdisplay.common.blueprint.BlueprintPart;
import dev.harlik.wrenchdisplay.common.blueprint.BlueprintValidator;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.VoxelShape;

import java.util.Map;

/** Default states and shapes from the server, for {@link BlueprintValidator}. */
final class PaperBlockInfo implements BlueprintValidator.BlockInfo {
	static final PaperBlockInfo INSTANCE = new PaperBlockInfo();

	private PaperBlockInfo() {
	}

	@Override
	public String defaultValue(String blockId, String key) {
		Material material = Material.matchMaterial(blockId);
		return material == null || !material.isBlock() ? null : BlockStates.properties(material.createBlockData()).get(key);
	}

	/** The block's collision shape inside its unit cube; null (full cube) when it has none. */
	@Override
	public float[] shape(String blockId, Map<String, String> properties) {
		Material material = Material.matchMaterial(blockId);
		if (material == null || !material.isBlock() || Bukkit.getWorlds().isEmpty()) return null;
		BlockData data = PreparedBlueprint.blockData(new BlueprintPart.BlockSpec(blockId, properties));
		Location anywhere = Bukkit.getWorlds().get(0).getSpawnLocation().toBlockLocation();
		VoxelShape shape = data.getCollisionShape(anywhere);
		BoundingBox union = null;
		for (BoundingBox box : shape.getBoundingBoxes()) {
			union = union == null ? box.clone() : union.union(box);
		}
		if (union == null) return null;
		return new float[] {(float) union.getMinX(), (float) union.getMinY(), (float) union.getMinZ(),
				(float) union.getMaxX(), (float) union.getMaxY(), (float) union.getMaxZ()};
	}
}
