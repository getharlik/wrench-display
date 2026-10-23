package dev.harlik.wrenchdisplay.common.snbt;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Writes values produced by {@link SnbtReader} back to SNBT that vanilla's parser accepts.
 * Platforms hand this string to their own NBT/item parsers, so the core never touches Minecraft classes.
 */
public final class SnbtWriter {
	private SnbtWriter() {
	}

	public static String write(Object value) {
		StringBuilder sb = new StringBuilder();
		append(sb, value, false);
		return sb.toString();
	}

	/** Same as {@link #write} but with compound keys sorted, for stable comparison keys. */
	public static String writeCanonical(Object value) {
		StringBuilder sb = new StringBuilder();
		append(sb, value, true);
		return sb.toString();
	}

	@SuppressWarnings("unchecked")
	private static void append(StringBuilder sb, Object value, boolean sorted) {
		if (value instanceof Map<?, ?> map) {
			Map<String, Object> entries = (Map<String, Object>) map;
			if (sorted) {
				entries = new TreeMap<>(entries);
			}
			sb.append('{');
			boolean first = true;
			for (Map.Entry<String, Object> entry : entries.entrySet()) {
				if (!first) sb.append(',');
				first = false;
				appendKey(sb, entry.getKey());
				sb.append(':');
				append(sb, entry.getValue(), sorted);
			}
			sb.append('}');
		} else if (value instanceof List<?> list) {
			sb.append('[');
			for (int i = 0; i < list.size(); i++) {
				if (i > 0) sb.append(',');
				append(sb, list.get(i), sorted);
			}
			sb.append(']');
		} else if (value instanceof int[] ints) {
			sb.append("[I;");
			for (int i = 0; i < ints.length; i++) {
				if (i > 0) sb.append(',');
				sb.append(ints[i]);
			}
			sb.append(']');
		} else if (value instanceof long[] longs) {
			sb.append("[L;");
			for (int i = 0; i < longs.length; i++) {
				if (i > 0) sb.append(',');
				sb.append(longs[i]).append('L');
			}
			sb.append(']');
		} else if (value instanceof byte[] bytes) {
			sb.append("[B;");
			for (int i = 0; i < bytes.length; i++) {
				if (i > 0) sb.append(',');
				sb.append(bytes[i]).append('b');
			}
			sb.append(']');
		} else if (value instanceof Byte b) {
			sb.append(b).append('b');
		} else if (value instanceof Short s) {
			sb.append(s).append('s');
		} else if (value instanceof Long l) {
			sb.append(l).append('L');
		} else if (value instanceof Float f) {
			sb.append(f).append('f');
		} else if (value instanceof Double d) {
			sb.append(d).append('d');
		} else if (value instanceof Number n) {
			sb.append(n);
		} else if (value instanceof Boolean bool) {
			sb.append(bool ? "true" : "false");
		} else {
			appendString(sb, String.valueOf(value));
		}
	}

	private static void appendKey(StringBuilder sb, String key) {
		if (!key.isEmpty() && key.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.' || c == '+')) {
			sb.append(key);
		} else {
			appendString(sb, key);
		}
	}

	private static void appendString(StringBuilder sb, String s) {
		sb.append('"');
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '"' -> sb.append("\\\"");
				case '\\' -> sb.append("\\\\");
				case '\n' -> sb.append("\\n");
				default -> sb.append(c);
			}
		}
		sb.append('"');
	}
}
