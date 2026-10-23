package dev.harlik.wrenchdisplay.fabric;

import eu.pb4.common.protection.api.CommonProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Claim checks through Common Protection API. Placing, editing and removing a display counts as building
 * at the block its visual centre is in, plus vanilla spawn protection.
 */
public final class Protection {
	private Protection() {
	}

	public static boolean canBuild(ServerPlayer player, Vec3 point) {
		BlockPos pos = BlockPos.containing(point);
		if (!player.level().mayInteract(player, pos)) {
			return false;
		}
		return CommonProtection.canPlaceBlock(player.level(), pos, player.nameAndId(), player);
	}
}
