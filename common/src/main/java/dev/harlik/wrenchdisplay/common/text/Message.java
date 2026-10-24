package dev.harlik.wrenchdisplay.common.text;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Player-facing text as a translation key with an English fallback, so the core stays free of Minecraft classes.
 * Platforms turn it into their own translatable text; players without the resource pack see the fallback.
 *
 * <p>Arguments fill the {@code %s} placeholders and may be strings, numbers, nested messages, or
 * {@link BlockName} / {@link ItemName}, which platforms show with the game's own (translated) names.
 */
public record Message(String key, String fallback, List<Object> args) {
	public static final String PREFIX = "wrench-display.";

	public static Message of(String key, String fallback, Object... args) {
		return new Message(PREFIX + key, fallback, Arrays.asList(args));
	}

	/** A key that isn't ours, e.g. vanilla's {@code color.minecraft.red}. */
	public static Message vanilla(String key, String fallback) {
		return new Message(key, fallback, List.of());
	}

	/** A block from the registry, e.g. {@code minecraft:barrier}. */
	public record BlockName(String id) {
	}

	/** An item from the registry, e.g. {@code minecraft:player_head}. */
	public record ItemName(String id) {
	}

	/** The English text, with arguments filled in (ids shown as plain names). */
	public String toFallbackString() {
		Object[] filled = args.stream().map(Message::fallbackOf).toArray();
		return String.format(Locale.ROOT, fallback, filled);
	}

	private static Object fallbackOf(Object arg) {
		if (arg instanceof Message message) return message.toFallbackString();
		if (arg instanceof BlockName block) return humanize(path(block.id()));
		if (arg instanceof ItemName item) return humanize(path(item.id()));
		return String.valueOf(arg);
	}

	private static String path(String id) {
		int colon = id.indexOf(':');
		return colon < 0 ? id : id.substring(colon + 1);
	}

	/** {@code thirdperson_lefthand} → {@code thirdperson lefthand}; used for fallbacks of generated keys. */
	public static String humanize(String name) {
		return name.replace('_', ' ');
	}

	// --- keys for values that come from the game (block states, display modes) -------------------

	/** Block-state property name, e.g. {@code wrench-display.state.facing}. */
	public static Message stateKey(String key) {
		return of("state." + key, humanize(key));
	}

	/** Block-state value, shared across properties, e.g. {@code wrench-display.state_value.north}. */
	public static Message stateValue(String value) {
		return of("state_value." + value, humanize(value));
	}
}
