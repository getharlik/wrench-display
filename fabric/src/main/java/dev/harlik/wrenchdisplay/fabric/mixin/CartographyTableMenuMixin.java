package dev.harlik.wrenchdisplay.fabric.mixin;

import dev.harlik.wrenchdisplay.fabric.ModItems;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The cartography table checks for paper directly instead of using a recipe; a blueprint must not extend maps. */
@Mixin(CartographyTableMenu.class)
public abstract class CartographyTableMenuMixin {
	@Shadow
	@Final
	private ResultContainer resultContainer;

	@Inject(method = "setupResultSlot", at = @At("HEAD"), cancellable = true)
	private void wrenchDisplay$rejectTools(ItemStack map, ItemStack additional, ItemStack result, CallbackInfo ci) {
		if (ModItems.isTool(additional)) {
			resultContainer.removeItemNoUpdate(0);
			ci.cancel();
		}
	}
}
