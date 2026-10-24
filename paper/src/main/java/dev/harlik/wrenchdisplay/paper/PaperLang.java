package dev.harlik.wrenchdisplay.paper;

import dev.harlik.wrenchdisplay.common.text.Message;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * Translatable text with English fallbacks: players with the resource pack see their own language, everyone
 * else sees English. Same keys as the Fabric mod ({@code assets/wrench-display/lang/*.json}).
 */
final class PaperLang {
	private PaperLang() {
	}

	/** {@code key} is relative to {@code wrench-display.}, e.g. {@code "wrench.no_permission"}. */
	static Component tr(String key, String fallback, Object... args) {
		return of(Message.of(key, fallback, args));
	}

	static Component of(Message message) {
		List<ComponentLike> args = new ArrayList<>();
		for (Object arg : message.args()) {
			args.add(argument(arg));
		}
		return Component.translatable(message.key(), message.fallback(), args);
	}

	/** The game's own (translated) name of a material. */
	static Component material(Material material) {
		return Component.translatable(material.translationKey());
	}

	private static ComponentLike argument(Object arg) {
		if (arg instanceof Message message) return of(message);
		if (arg instanceof Message.BlockName block) {
			Material material = Material.matchMaterial(block.id());
			return material == null ? Component.text(block.id()) : material(material);
		}
		if (arg instanceof Message.ItemName item) {
			Material material = Material.matchMaterial(item.id());
			return material == null ? Component.text(item.id()) : material(material);
		}
		if (arg instanceof ComponentLike component) return component;
		return Component.text(String.valueOf(arg));
	}
}
