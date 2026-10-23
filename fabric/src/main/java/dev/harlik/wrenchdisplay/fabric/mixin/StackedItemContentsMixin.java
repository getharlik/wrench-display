package dev.harlik.wrenchdisplay.fabric.mixin;

import dev.harlik.wrenchdisplay.fabric.ModItems;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the recipe book from counting the tools as sticks/paper and moving them into the crafting grid. */
@Mixin(StackedItemContents.class)
public abstract class StackedItemContentsMixin {
	@Inject(method = {"accountStack(Lnet/minecraft/world/item/ItemStack;I)V", "accountSimpleStack"}, at = @At("HEAD"), cancellable = true)
	private void wrenchDisplay$skipTools(ItemStack stack, CallbackInfo ci) {
		if (ModItems.isTool(stack)) {
			ci.cancel();
		}
	}
}
