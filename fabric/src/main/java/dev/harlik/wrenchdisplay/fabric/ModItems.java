package dev.harlik.wrenchdisplay.fabric;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;
import java.util.UUID;

/**
 * The wrench and blueprint are vanilla items marked with components, so vanilla clients can join; the
 * resource pack (or the client mod) supplies the models through {@code item_model}.
 *
 * <p>The wrench is a stick and the blueprint a paper underneath, so they still look right without the pack.
 * Their vanilla uses are blocked: fuel through the removed {@code cooking_fuel} component, recipes, the
 * recipe book, villager trades and the cartography table through mixins.
 */
public final class ModItems {
	public static final String TAG = "wrench_display";
	public static final String WRENCH = "wrench";
	public static final String BLUEPRINT = "blueprint";

	private ModItems() {
	}

	public static ItemStack wrench() {
		return create(Items.STICK, WRENCH, "Wrench");
	}

	public static ItemStack blueprint() {
		return create(Items.PAPER, BLUEPRINT, "Blueprint");
	}

	public static boolean isTool(ItemStack stack) {
		return type(stack) != null;
	}

	public static boolean isWrench(ItemStack stack) {
		return WRENCH.equals(type(stack));
	}

	public static boolean isBlueprint(ItemStack stack) {
		return BLUEPRINT.equals(type(stack));
	}

	/** The {@code wrench_display.type} marker, or null for any other item. */
	public static String type(ItemStack stack) {
		if (stack.isEmpty() || !(stack.is(Items.STICK) || stack.is(Items.PAPER))) {
			return null;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		return data.copyTag().getCompound(TAG).flatMap(tag -> tag.getString("type")).orElse(null);
	}

	/** The stored blueprint this item points at, or null while it is blank. */
	public static UUID blueprintId(ItemStack stack) {
		return marker(stack).getString("id").map(id -> {
			try {
				return UUID.fromString(id);
			} catch (IllegalArgumentException e) {
				return null;
			}
		}).orElse(null);
	}

	/** Quarter turns (0–3) the blueprint is placed with. */
	public static int rotation(ItemStack stack) {
		return Math.floorMod(marker(stack).getIntOr("rot", 0), 4);
	}

	public static void setRotation(ItemStack stack, int quarterTurns) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> root.getCompound(TAG)
				.ifPresent(tag -> tag.putInt("rot", Math.floorMod(quarterTurns, 4))));
	}

	/** Points a blueprint item at a stored blueprint and lists what it contains. */
	public static void fillBlueprint(ItemStack stack, UUID id, List<Component> summary) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> root.getCompound(TAG).ifPresent(tag -> {
			tag.putString("id", id.toString());
			tag.putInt("rot", 0);
		}));
		stack.set(DataComponents.LORE, new ItemLore(summary));
	}

	private static CompoundTag marker(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? new CompoundTag() : data.copyTag().getCompound(TAG).orElseGet(CompoundTag::new);
	}

	private static ItemStack create(Item base, String type, String fallbackName) {
		ItemStack stack = new ItemStack(base);
		stack.set(DataComponents.ITEM_MODEL, WrenchDisplay.id(type));
		stack.set(DataComponents.ITEM_NAME, Component.translatableWithFallback("item." + WrenchDisplay.MOD_ID + "." + type, fallbackName));
		CompoundTag marker = new CompoundTag();
		marker.putString("type", type);
		CompoundTag root = new CompoundTag();
		root.put(TAG, marker);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
		stack.set(DataComponents.MAX_STACK_SIZE, 1);
		stack.remove(DataComponents.COOKING_FUEL);
		return stack;
	}
}
