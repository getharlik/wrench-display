package dev.harlik.wrenchdisplay.paper;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Gate;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.Player;

/**
 * How a block placed by the wrench is oriented. The API has no "state for placement", so this follows vanilla
 * for the common cases; the wrench can change block states afterwards anyway.
 */
final class Placement {
	private Placement() {
	}

	/**
	 * @param clicked the face of the block that was clicked
	 * @param upperHalf whether the click was in the upper half of a side face
	 */
	static BlockData orient(Material material, Player player, BlockFace clicked, boolean upperHalf) {
		BlockData data = material.createBlockData();
		BlockFace facing = player.getFacing();
		boolean top = clicked == BlockFace.DOWN || (clicked != BlockFace.UP && upperHalf);

		if (data instanceof Orientable orientable) {
			orientable.setAxis(switch (clicked) {
				case EAST, WEST -> org.bukkit.Axis.X;
				case NORTH, SOUTH -> org.bukkit.Axis.Z;
				default -> org.bukkit.Axis.Y;
			});
		}
		if (data instanceof Slab slab) {
			slab.setType(top ? Slab.Type.TOP : Slab.Type.BOTTOM);
		}
		if (data instanceof Bisected bisected && !(data instanceof Door)) {
			bisected.setHalf(top ? Bisected.Half.TOP : Bisected.Half.BOTTOM);
		}
		if (data instanceof Directional directional) {
			BlockFace face;
			if (data instanceof Stairs || data instanceof Door || data instanceof Gate) {
				face = facing; // these face away from the player, like when placed in vanilla
			} else if (directional.getFaces().contains(BlockFace.UP)) {
				face = clicked; // rods, amethyst, …: pointing out of the clicked face
			} else {
				face = facing.getOppositeFace(); // chests, furnaces, …: facing the player
			}
			if (directional.getFaces().contains(face)) directional.setFacing(face);
		}
		if (data instanceof Rotatable rotatable) {
			rotatable.setRotation(yawToRotation(player.getLocation().getYaw() + 180));
		}
		return data;
	}

	private static final BlockFace[] ROTATIONS = {
			BlockFace.SOUTH, BlockFace.SOUTH_SOUTH_WEST, BlockFace.SOUTH_WEST, BlockFace.WEST_SOUTH_WEST,
			BlockFace.WEST, BlockFace.WEST_NORTH_WEST, BlockFace.NORTH_WEST, BlockFace.NORTH_NORTH_WEST,
			BlockFace.NORTH, BlockFace.NORTH_NORTH_EAST, BlockFace.NORTH_EAST, BlockFace.EAST_NORTH_EAST,
			BlockFace.EAST, BlockFace.EAST_SOUTH_EAST, BlockFace.SOUTH_EAST, BlockFace.SOUTH_SOUTH_EAST};

	private static BlockFace yawToRotation(float yaw) {
		int index = Math.floorMod(Math.round(yaw / 22.5f), 16);
		return ROTATIONS[index];
	}
}
