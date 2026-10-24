package dev.harlik.wrenchdisplay.paper;

import com.destroystokyo.paper.event.inventory.PrepareResultEvent;
import io.papermc.paper.event.player.PlayerTradeEvent;
import org.bukkit.block.Crafter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CartographyInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.SmithingInventory;

/**
 * The tools are a stick and a paper underneath; none of the vanilla uses of those may accept them: crafting
 * (2×2 grid, crafting table, crafter), smithing, furnace fuel, villager trades and the cartography table.
 */
final class VanillaUseBlocker implements Listener {
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onCraft(PrepareItemCraftEvent event) {
		if (event.getRecipe() == null) return;
		for (ItemStack ingredient : event.getInventory().getMatrix()) {
			if (PaperItems.isTool(ingredient)) {
				event.getInventory().setResult(null);
				return;
			}
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onCrafter(CrafterCraftEvent event) {
		if (!(event.getBlock().getState() instanceof Crafter crafter)) return;
		for (ItemStack ingredient : crafter.getInventory().getContents()) {
			if (PaperItems.isTool(ingredient)) {
				event.setCancelled(true);
				return;
			}
		}
	}

	/** Smithing, cartography, anvils and grindstones share this event. */
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPrepareResult(PrepareResultEvent event) {
		if (event.getResult() == null) return;
		boolean cartography = event.getInventory() instanceof CartographyInventory;
		boolean smithing = event.getInventory() instanceof SmithingInventory;
		if (!cartography && !smithing) return;
		for (ItemStack input : event.getInventory().getContents()) {
			if (PaperItems.isTool(input)) {
				event.setResult(null);
				return;
			}
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onBurn(FurnaceBurnEvent event) {
		if (PaperItems.isTool(event.getFuel())) event.setCancelled(true);
	}

	@EventHandler(ignoreCancelled = true)
	public void onTrade(PlayerTradeEvent event) {
		MerchantRecipe trade = event.getTrade();
		for (ItemStack ingredient : event.getPlayer().getOpenInventory().getTopInventory().getContents()) {
			if (PaperItems.isTool(ingredient) && trade.getIngredients().stream().anyMatch(i -> i.getType() == ingredient.getType())) {
				event.setCancelled(true);
				return;
			}
		}
	}
}
