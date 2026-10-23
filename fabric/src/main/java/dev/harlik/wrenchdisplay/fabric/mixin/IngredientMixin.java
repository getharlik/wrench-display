package dev.harlik.wrenchdisplay.fabric.mixin;

import dev.harlik.wrenchdisplay.fabric.ModItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The tools are a stick and a paper underneath; no recipe (crafting, crafter, smithing, …) may use them. */
@Mixin(Ingredient.class)
public abstract class IngredientMixin {
	@Inject(method = "test(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
	private void wrenchDisplay$rejectTools(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (ModItems.isTool(stack)) {
			cir.setReturnValue(false);
		}
	}
}
