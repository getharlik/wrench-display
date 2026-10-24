package dev.harlik.wrenchdisplay.fabric.compat;

import eu.pb4.common.protection.api.CommonProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
//? if >=26.3 {
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.component.SwingAnimation;
//?}

import java.nio.file.Path;

/**
 * Small Minecraft API differences between the supported versions, in one place so the rest of the mod reads the
 * same on every version. Lines marked {@code //? if} are switched by Stonecutter per version.
 */
public final class Compat {
	private Compat() {
	}

	/** Plays the arm swing for everyone watching. */
	public static void swingMainHand(ServerPlayer player) {
		//? if >=26.3 {
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		//?} else {
		/*player.swing(InteractionHand.MAIN_HAND, true);
		*///?}
	}

	/** Puts the stack into the inventory, dropping what doesn't fit. */
	public static void giveOrDrop(ServerPlayer player, ItemStack stack) {
		//? if >=26.3 {
		player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
		//?} else {
		/*player.getInventory().placeItemBackInInventory(stack);
		*///?}
	}

	/** Claim check through Common Protection API (2.x takes a NameAndId, 1.x a GameProfile). */
	public static boolean canPlaceBlock(ServerPlayer player, BlockPos pos) {
		//? if >=26.1 {
		return CommonProtection.canPlaceBlock(player.level(), pos, player.nameAndId(), player);
		//?} else {
		/*return CommonProtection.canPlaceBlock(player.level(), pos, player.getGameProfile(), player);
		*///?}
	}

	/** The world's {@code data} folder. */
	public static Path worldDataDir(MinecraftServer server) {
		//? if >=26.1 {
		return server.getWorldPath(LevelResource.DATA);
		//?} else {
		/*return server.getWorldPath(LevelResource.ROOT).resolve("data");
		*///?}
	}

	/**
	 * Makes the item unusable as furnace fuel. On 26.3+ fuel is an item component; older versions look it up in a
	 * table instead, which {@code FuelValuesMixin} handles.
	 */
	public static void removeFuel(ItemStack stack) {
		//? if >=26.3 {
		stack.remove(DataComponents.COOKING_FUEL);
		//?}
	}
}
