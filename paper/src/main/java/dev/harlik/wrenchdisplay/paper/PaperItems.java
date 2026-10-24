package dev.harlik.wrenchdisplay.paper;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.persistence.PersistentDataContainerView;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;

/**
 * The wrench (a stick) and the blueprint (a paper), marked in their persistent data; the resource pack supplies
 * the models through {@code item_model}. Their vanilla uses are blocked in {@link VanillaUseBlocker}.
 */
final class PaperItems {
	static final String WRENCH = "wrench";
	static final String BLUEPRINT = "blueprint";
	private static final NamespacedKey TYPE = new NamespacedKey(WrenchDisplayPlugin.NAMESPACE, "type");
	private static final NamespacedKey BLUEPRINT_ID = new NamespacedKey(WrenchDisplayPlugin.NAMESPACE, "blueprint");
	private static final NamespacedKey ROTATION = new NamespacedKey(WrenchDisplayPlugin.NAMESPACE, "rotation");

	private PaperItems() {
	}

	static ItemStack wrench() {
		return create(Material.STICK, WRENCH, "Wrench");
	}

	static ItemStack blueprint() {
		return create(Material.PAPER, BLUEPRINT, "Blueprint");
	}

	/** The {@code wrench-display:type} marker, or null for any other item. */
	static String type(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !(stack.getType() == Material.STICK || stack.getType() == Material.PAPER)) {
			return null;
		}
		return stack.getPersistentDataContainer().get(TYPE, PersistentDataType.STRING);
	}

	static boolean isTool(ItemStack stack) {
		return type(stack) != null;
	}

	static boolean isWrench(ItemStack stack) {
		return WRENCH.equals(type(stack));
	}

	static boolean isBlueprint(ItemStack stack) {
		return BLUEPRINT.equals(type(stack));
	}

	/** The stored blueprint this item points at, or null while it is blank. */
	static UUID blueprintId(ItemStack stack) {
		String id = stack.getPersistentDataContainer().get(BLUEPRINT_ID, PersistentDataType.STRING);
		if (id == null) return null;
		try {
			return UUID.fromString(id);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	static int rotation(ItemStack stack) {
		PersistentDataContainerView data = stack.getPersistentDataContainer();
		return Math.floorMod(data.getOrDefault(ROTATION, PersistentDataType.INTEGER, 0), 4);
	}

	static void setRotation(ItemStack stack, int quarterTurns) {
		stack.editPersistentDataContainer(data -> data.set(ROTATION, PersistentDataType.INTEGER, Math.floorMod(quarterTurns, 4)));
	}

	static void fillBlueprint(ItemStack stack, UUID id, List<Component> summary) {
		stack.editPersistentDataContainer(data -> {
			data.set(BLUEPRINT_ID, PersistentDataType.STRING, id.toString());
			data.set(ROTATION, PersistentDataType.INTEGER, 0);
		});
		stack.setData(DataComponentTypes.LORE, ItemLore.lore(summary));
	}

	private static ItemStack create(Material base, String type, String fallbackName) {
		ItemStack stack = ItemStack.of(base);
		stack.setData(DataComponentTypes.ITEM_MODEL, Key.key(WrenchDisplayPlugin.NAMESPACE, type));
		stack.setData(DataComponentTypes.ITEM_NAME, Component.translatable("item." + WrenchDisplayPlugin.NAMESPACE + "." + type, fallbackName));
		stack.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
		stack.editPersistentDataContainer(data -> data.set(TYPE, PersistentDataType.STRING, type));
		return stack;
	}
}
