package dev.harlik.wrenchdisplay.fabric.blueprint;

import com.mojang.authlib.properties.Property;
import dev.harlik.wrenchdisplay.common.blueprint.HeadTextures;
import dev.harlik.wrenchdisplay.fabric.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** What a blueprint costs in survival, and taking it from the inventory. */
final class Materials {
	private Materials() {
	}

	/** One part's cost. {@code item} is AIR when the part can't be paid for (e.g. fire). */
	record Cost(Item item, int count, String matchKey, Predicate<ItemStack> matches) {
		String label() {
			if (item == Items.AIR) return "an item that can't be obtained";
			String name = BuiltInRegistries.ITEM.getKey(item).getPath().replace('_', ' ');
			return matchKey != null && matchKey.startsWith("skin:") ? name + " (custom skin)"
					: matchKey != null && matchKey.startsWith("components:") ? name + " (with custom data)" : name;
		}
	}

	/** Costs per part, in part order (text parts cost nothing and get null). */
	static List<Cost> costs(PreparedBlueprint blueprint) {
		List<Cost> out = new ArrayList<>();
		for (PreparedBlueprint.Part part : blueprint.parts()) {
			out.add(switch (part.kind()) {
				case BLOCK -> {
					Item item = part.block().getBlock().asItem();
					yield new Cost(item, 1, null, stack -> stack.is(item));
				}
				case ITEM -> {
					ItemStack expected = part.item();
					String key = part.source().item().matchKey();
					Predicate<ItemStack> matches;
					if (key == null || key.startsWith("tag:")) {
						matches = stack -> stack.is(expected.getItem());
					} else if (key.startsWith("skin:")) {
						String skin = key.substring("skin:".length());
						matches = stack -> stack.is(Items.PLAYER_HEAD) && skin.equals(skinKey(stack));
					} else {
						matches = stack -> ItemStack.isSameItemSameComponents(expected, stack);
					}
					yield new Cost(expected.getItem(), expected.getCount(), key, matches);
				}
				case TEXT -> null;
			});
		}
		return out;
	}

	/** Player-facing list of what's missing, e.g. "3× player head (custom skin)"; empty when everything is there. */
	static List<String> missing(Inventory inventory, List<Cost> costs) {
		Map<String, Integer> needed = new LinkedHashMap<>();
		Map<String, Cost> byLabel = new LinkedHashMap<>();
		for (Cost cost : costs) {
			if (cost == null) continue;
			String group = BuiltInRegistries.ITEM.getKey(cost.item()) + "|" + cost.matchKey();
			needed.merge(group, cost.count(), Integer::sum);
			byLabel.putIfAbsent(group, cost);
		}
		List<String> out = new ArrayList<>();
		needed.forEach((group, count) -> {
			Cost cost = byLabel.get(group);
			int have = cost.item() == Items.AIR ? 0 : count(inventory, cost.matches());
			if (have < count) out.add((count - have) + "× " + cost.label());
		});
		return out;
	}

	/**
	 * Removes the cost of every part. Call only after {@link #missing} came back empty.
	 *
	 * @return per part, what was taken (for the refund on removal); null for free parts
	 */
	static List<ItemStack> take(Inventory inventory, List<Cost> costs) {
		List<ItemStack> taken = new ArrayList<>();
		for (Cost cost : costs) {
			if (cost == null) {
				taken.add(null);
				continue;
			}
			ItemStack refund = ItemStack.EMPTY;
			int left = cost.count();
			for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
				ItemStack stack = inventory.getItem(slot);
				if (stack.isEmpty() || ModItems.isTool(stack) || !cost.matches().test(stack)) continue;
				int n = Math.min(left, stack.getCount());
				if (refund.isEmpty()) refund = stack.copyWithCount(n);
				else refund.grow(n);
				stack.shrink(n);
				left -= n;
			}
			taken.add(refund);
		}
		return taken;
	}

	private static int count(Inventory inventory, Predicate<ItemStack> matches) {
		int total = 0;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty() && !ModItems.isTool(stack) && matches.test(stack)) total += stack.getCount();
		}
		return total;
	}

	static String skinKey(ItemStack head) {
		ResolvableProfile profile = head.get(DataComponents.PROFILE);
		if (profile == null) return null;
		for (Property property : profile.partialProfile().properties().get("textures")) {
			String key = HeadTextures.skinKeyFromTextures(property.value());
			if (key != null) return key;
		}
		return null;
	}
}
