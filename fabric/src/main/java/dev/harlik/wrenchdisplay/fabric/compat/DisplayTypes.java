package dev.harlik.wrenchdisplay.fabric.compat;

import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
//? if >=26.2 {
import net.minecraft.world.entity.EntityTypes;
//?}

/** The display entity types; 26.2 moved the constants from {@code EntityType} to {@code EntityTypes}. */
public final class DisplayTypes {
	//? if >=26.2 {
	public static final EntityType<Display.BlockDisplay> BLOCK = EntityTypes.BLOCK_DISPLAY;
	public static final EntityType<Display.ItemDisplay> ITEM = EntityTypes.ITEM_DISPLAY;
	public static final EntityType<Display.TextDisplay> TEXT = EntityTypes.TEXT_DISPLAY;
	//?} else {
	/*public static final EntityType<Display.BlockDisplay> BLOCK = EntityType.BLOCK_DISPLAY;
	public static final EntityType<Display.ItemDisplay> ITEM = EntityType.ITEM_DISPLAY;
	public static final EntityType<Display.TextDisplay> TEXT = EntityType.TEXT_DISPLAY;
	*///?}

	private DisplayTypes() {
	}
}
