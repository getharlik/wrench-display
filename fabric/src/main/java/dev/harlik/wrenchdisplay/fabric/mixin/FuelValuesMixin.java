package dev.harlik.wrenchdisplay.fabric.mixin;

import dev.harlik.wrenchdisplay.fabric.ModItems;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Before 26.3, furnaces look fuel up in this table; the wrench is a stick underneath and must not burn. On 26.3+ the
 * class doesn't exist (fuel is an item component, removed in {@code Compat.removeFuel}), so the mixin is skipped.
 */
@Pseudo
@Mixin(targets = "net.minecraft.world.level.block.entity.FuelValues")
public abstract class FuelValuesMixin {
	@Inject(method = "isFuel", at = @At("HEAD"), cancellable = true, require = 0)
	private void wrenchDisplay$notFuel(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (ModItems.isTool(stack)) cir.setReturnValue(false);
	}

	@Inject(method = "burnDuration", at = @At("HEAD"), cancellable = true, require = 0)
	private void wrenchDisplay$noBurnTime(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
		if (ModItems.isTool(stack)) cir.setReturnValue(0);
	}
}
