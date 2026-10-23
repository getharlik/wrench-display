package dev.harlik.wrenchdisplay.fabric;

import com.mojang.serialization.Codec;
import dev.harlik.wrenchdisplay.common.DisplayMeta;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.UUID;

/** Persistent {@link DisplayMeta} on display entities, plus refund item (de)serialization. */
public final class DisplayStore {
	private static AttachmentType<String> META;

	private DisplayStore() {
	}

	static void register() {
		META = AttachmentRegistry.createPersistent(WrenchDisplay.id("meta"), Codec.STRING);
	}

	public static DisplayMeta get(Display display) {
		return DisplayMeta.deserialize(display.getAttached(META));
	}

	public static void set(Display display, DisplayMeta meta) {
		display.setAttached(META, meta.serialize());
	}

	/**
	 * All parts of a group. Parts of one group are spawned at the same position, so they are found around
	 * the given part's origin.
	 */
	public static List<Display> group(Display part, UUID groupId) {
		return part.level().getEntitiesOfClass(Display.class, new AABB(part.position(), part.position()).inflate(0.01),
				d -> {
					DisplayMeta meta = get(d);
					return meta != null && groupId.equals(meta.groupId());
				});
	}

	/** Player-placed displays in the chunk of {@code display}'s position, for the per-chunk limit. */
	public static int countInChunk(ServerLevel level, double x, double z) {
		int chunkX = ((int) Math.floor(x)) >> 4;
		int chunkZ = ((int) Math.floor(z)) >> 4;
		AABB box = new AABB(chunkX << 4, level.getMinY(), chunkZ << 4, (chunkX << 4) + 16, level.getMaxY() + 1, (chunkZ << 4) + 16);
		return level.getEntitiesOfClass(Display.class, box, d -> get(d) != null).size();
	}

	public static String encodeItem(HolderLookup.Provider registries, ItemStack stack) {
		return ItemStack.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), stack)
				.result().map(Tag::toString).orElse(null);
	}

	/** Returns {@link ItemStack#EMPTY} for missing or unreadable data. */
	public static ItemStack decodeItem(HolderLookup.Provider registries, String snbt) {
		if (snbt == null || snbt.isEmpty()) {
			return ItemStack.EMPTY;
		}
		try {
			Tag tag = TagParser.parseCompoundFully(snbt);
			return ItemStack.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag).result().orElse(ItemStack.EMPTY);
		} catch (Exception e) {
			WrenchDisplay.LOGGER.warn("Could not read a refund item: {}", e.getMessage());
			return ItemStack.EMPTY;
		}
	}
}
