package dev.harlik.wrenchdisplay.paper;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BlockType;
import org.bukkit.block.data.BlockData;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Block-state access without server internals, working on the {@code material[key=value,…]} text form. */
final class BlockStates {
	private static final Map<String, List<String>> VALUES = new ConcurrentHashMap<>();

	private BlockStates() {
	}

	/** {@code minecraft:oak_stairs[facing=north,half=bottom]} → {facing=north, half=bottom}, in the block's order. */
	static Map<String, String> properties(BlockData data) {
		Map<String, String> out = new LinkedHashMap<>();
		String text = data.getAsString(false);
		int open = text.indexOf('[');
		if (open < 0 || !text.endsWith("]")) return out;
		for (String pair : text.substring(open + 1, text.length() - 1).split(",")) {
			int eq = pair.indexOf('=');
			if (eq > 0) out.put(pair.substring(0, eq), pair.substring(eq + 1));
		}
		return out;
	}

	/** The values {@code key} can take on this block, in the block's own order (collected from all its states). */
	static List<String> values(Material material, String key) {
		return VALUES.computeIfAbsent(material.getKey() + "|" + key, k -> {
			Set<String> seen = new LinkedHashSet<>();
			BlockType type = material.asBlockType();
			if (type != null) {
				for (BlockData state : type.createBlockDataStates()) {
					String value = properties(state).get(key);
					if (value != null) seen.add(value);
				}
			}
			return List.copyOf(seen);
		});
	}

	/** The same block with one property changed, or null if the block rejects the value. */
	static BlockData with(BlockData data, String key, String value) {
		Map<String, String> properties = properties(data);
		properties.put(key, value);
		StringBuilder text = new StringBuilder(data.getMaterial().getKey().toString()).append('[');
		properties.forEach((k, v) -> text.append(k).append('=').append(v).append(','));
		text.setLength(text.length() - 1);
		try {
			return Bukkit.createBlockData(text.append(']').toString());
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
