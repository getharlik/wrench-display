package dev.harlik.wrenchdisplay.paper;

import com.destroystokyo.paper.profile.ProfileProperty;
import dev.harlik.wrenchdisplay.common.blueprint.HeadTextures;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

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
	record Cost(Material item, int count, String matchKey, Predicate<ItemStack> matches) {
		Component label() {
			if (item == Material.AIR) return PaperLang.tr("material.unobtainable", "an item that can't be obtained");
			Component name = PaperLang.material(item);
			if (matchKey != null && matchKey.startsWith("skin:")) return PaperLang.tr("material.custom_skin", "%s (custom skin)", name);
			if (matchKey != null && matchKey.startsWith("components:")) return PaperLang.tr("material.custom_data", "%s (with custom data)", name);
			return name;
		}

		/** Costs that need the same items share this key. */
		String group() {
			return item.getKey() + "|" + matchKey;
		}
	}

	/** Costs per part, in part order (text parts cost nothing and get null). */
	static List<Cost> costs(PreparedBlueprint blueprint) {
		List<Cost> out = new ArrayList<>();
		for (PreparedBlueprint.Part part : blueprint.parts()) {
			out.add(switch (part.kind()) {
				case BLOCK -> {
					Material item = part.block().getPlacementMaterial();
					Material paid = item.isItem() ? item : Material.AIR;
					yield new Cost(paid, 1, null, stack -> stack.getType() == paid);
				}
				case ITEM -> {
					ItemStack expected = part.item();
					String key = part.source().item().matchKey();
					Predicate<ItemStack> matches;
					if (key == null || key.startsWith("tag:")) {
						matches = stack -> stack.getType() == expected.getType();
					} else if (key.startsWith("skin:")) {
						String skin = key.substring("skin:".length());
						matches = stack -> stack.getType() == Material.PLAYER_HEAD && skin.equals(skinKey(stack));
					} else {
						matches = expected::isSimilar;
					}
					yield new Cost(expected.getType(), expected.getAmount(), key, matches);
				}
				case TEXT -> null;
			});
		}
		return out;
	}

	/** What's missing, e.g. "3× Player Head (custom skin)"; empty when everything is there. */
	static List<Component> missing(PlayerInventory inventory, List<Cost> costs) {
		Map<String, Integer> needed = new LinkedHashMap<>();
		Map<String, Cost> byGroup = new LinkedHashMap<>();
		for (Cost cost : costs) {
			if (cost == null) continue;
			needed.merge(cost.group(), cost.count(), Integer::sum);
			byGroup.putIfAbsent(cost.group(), cost);
		}
		List<Component> out = new ArrayList<>();
		needed.forEach((group, count) -> {
			Cost cost = byGroup.get(group);
			int have = cost.item() == Material.AIR ? 0 : count(inventory, cost.matches());
			if (have < count) out.add(PaperLang.tr("material.amount", "%s× %s", count - have, cost.label()));
		});
		return out;
	}

	/** Removes every part's cost; returns per part what was taken (null for free parts). */
	static List<ItemStack> take(PlayerInventory inventory, List<Cost> costs) {
		List<ItemStack> taken = new ArrayList<>();
		for (Cost cost : costs) {
			if (cost == null) {
				taken.add(null);
				continue;
			}
			ItemStack refund = null;
			int left = cost.count();
			for (int slot = 0; slot < inventory.getSize() && left > 0; slot++) {
				ItemStack stack = inventory.getItem(slot);
				if (stack == null || stack.isEmpty() || PaperItems.isTool(stack) || !cost.matches().test(stack)) continue;
				int n = Math.min(left, stack.getAmount());
				if (refund == null) refund = stack.asQuantity(n);
				else refund.add(n);
				stack.subtract(n);
				left -= n;
			}
			taken.add(refund);
		}
		return taken;
	}

	private static int count(PlayerInventory inventory, Predicate<ItemStack> matches) {
		int total = 0;
		for (ItemStack stack : inventory.getContents()) {
			if (stack != null && !stack.isEmpty() && !PaperItems.isTool(stack) && matches.test(stack)) total += stack.getAmount();
		}
		return total;
	}

	static String skinKey(ItemStack head) {
		ResolvableProfile profile = head.getData(DataComponentTypes.PROFILE);
		if (profile == null) return null;
		for (ProfileProperty property : profile.properties()) {
			if (property.getName().equals("textures")) {
				String key = HeadTextures.skinKeyFromTextures(property.getValue());
				if (key != null) return key;
			}
		}
		return null;
	}
}
