package dev.harlik.wrenchdisplay.paper;

import dev.harlik.wrenchdisplay.common.DisplayMeta;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/** {@link DisplayMeta} in the display entity's persistent data, plus refund item (de)serialization. */
final class DisplayStore {
	private static final NamespacedKey META = new NamespacedKey(WrenchDisplayPlugin.NAMESPACE, "meta");

	private DisplayStore() {
	}

	static DisplayMeta get(Entity display) {
		return DisplayMeta.deserialize(display.getPersistentDataContainer().get(META, PersistentDataType.STRING));
	}

	static void set(Entity display, DisplayMeta meta) {
		display.getPersistentDataContainer().set(META, PersistentDataType.STRING, meta.serialize());
	}

	/** All parts of a group; they are spawned at the same position, so they are found around the given part. */
	static List<Display> group(Display part, UUID groupId) {
		List<Display> out = new ArrayList<>();
		for (Display other : part.getWorld().getNearbyEntitiesByType(Display.class, part.getLocation(), 0.01)) {
			DisplayMeta meta = get(other);
			if (meta != null && groupId.equals(meta.groupId())) out.add(other);
		}
		return out;
	}

	/** Player-placed displays in a chunk, for the per-chunk limit. */
	static int countInChunk(Chunk chunk) {
		int count = 0;
		for (Entity entity : chunk.getEntities()) {
			if (entity instanceof Display && get(entity) != null) count++;
		}
		return count;
	}

	static String encodeItem(ItemStack stack) {
		return Base64.getEncoder().encodeToString(stack.serializeAsBytes());
	}

	/** Returns null for missing or unreadable data. */
	static ItemStack decodeItem(String data) {
		if (data == null || data.isEmpty()) return null;
		try {
			return ItemStack.deserializeBytes(Base64.getDecoder().decode(data));
		} catch (RuntimeException e) {
			return null;
		}
	}
}
