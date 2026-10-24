package dev.harlik.wrenchdisplay.fabric;

import dev.harlik.wrenchdisplay.common.text.Message;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.Optional;

/**
 * Translatable text with English fallbacks: players with the resource pack (or the client mod) see their own
 * language, everyone else sees English. Keys live in {@code assets/wrench-display/lang/*.json}.
 */
public final class Lang {
	private Lang() {
	}

	/** {@code key} is relative to {@code wrench-display.}, e.g. {@code "wrench.no_permission"}. */
	public static MutableComponent tr(String key, String fallback, Object... args) {
		return of(Message.of(key, fallback, args));
	}

	public static MutableComponent of(Message message) {
		Object[] args = message.args().stream().map(Lang::argument).toArray();
		return Component.translatableWithFallback(message.key(), message.fallback(), args);
	}

	/** The game's own (translated) name of an item. */
	public static MutableComponent item(Item item) {
		return Component.translatable(item.getDescriptionId());
	}

	private static Object argument(Object arg) {
		if (arg instanceof Message message) return of(message);
		if (arg instanceof Message.BlockName block) {
			Optional<Block> found = Optional.ofNullable(Identifier.tryParse(block.id())).flatMap(BuiltInRegistries.BLOCK::getOptional);
			return found.<Object>map(Block::getName).orElse(block.id());
		}
		if (arg instanceof Message.ItemName item) {
			Optional<Item> found = Optional.ofNullable(Identifier.tryParse(item.id())).flatMap(BuiltInRegistries.ITEM::getOptional);
			return found.<Object>map(Lang::item).orElse(item.id());
		}
		if (arg instanceof Component component) return component;
		return String.valueOf(arg);
	}
}
