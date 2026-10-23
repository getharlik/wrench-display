package dev.harlik.wrenchdisplay.fabric.mixin;

import dev.harlik.wrenchdisplay.fabric.ModItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Villagers buy sticks and paper; they must not accept the tools as payment. */
@Mixin(ItemCost.class)
public abstract class ItemCostMixin {
	@Inject(method = "test(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
	private void wrenchDisplay$rejectTools(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (ModItems.isTool(stack)) {
			cir.setReturnValue(false);
		}
	}
}
