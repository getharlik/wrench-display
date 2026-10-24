package dev.harlik.wrenchdisplay.paper;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Claim checks. Placing, editing and removing a display counts as building at the block its visual centre is
 * in. The check fires a test {@link BlockPlaceEvent} that is never carried out, which every common protection
 * plugin (WorldGuard, GriefPrevention, Lands, Towny, …) already answers.
 */
final class Protection {
	private Protection() {
	}

	static boolean canBuild(Player player, Location point) {
		Block block = point.getBlock();
		Block against = block.getRelative(0, -1, 0);
		BlockPlaceEvent test = new BlockPlaceEvent(block, block.getState(), against, ItemStack.of(Material.STONE),
				player, true, EquipmentSlot.HAND);
		Bukkit.getPluginManager().callEvent(test);
		return !test.isCancelled() && test.canBuild();
	}
}
