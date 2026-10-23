package dev.harlik.wrenchdisplay.common.snbt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lenient SNBT reader producing plain Java values.
 *
 * <p>Compounds become {@code LinkedHashMap<String, Object>}, lists become {@code List<Object>},
 * typed arrays become {@code byte[]}/{@code int[]}/{@code long[]}, numbers keep their suffix type
 * ({@code 1b} → Byte, {@code 1s} → Short, {@code 1} → Integer, {@code 1L} → Long, {@code 1f} → Float,
 * {@code 1.0}/{@code 1d} → Double), {@code true}/{@code false} become Boolean and anything else unquoted
 * is a String. It accepts the dialects exported by display editors: JSON-style quoted keys, single- or
 * double-quoted strings, scientific notation and empty typed arrays such as {@code [I;]}.
 */
public final class SnbtReader {
	private final String src;
	private int pos;

	private SnbtReader(String src, int start) {
		this.src = src;
		this.pos = start;
	}

	public static Object parse(String src) {
		SnbtReader reader = new SnbtReader(src, 0);
		Object value = reader.readValue();
		reader.skipWhitespace();
		if (reader.pos != src.length()) {
			throw reader.error("Unexpected trailing data");
		}
		return value;
	}

	/** Reads one value starting at {@code start}; the returned end index points just past it. */
	public static Result parseAt(String src, int start) {
		SnbtReader reader = new SnbtReader(src, start);
		Object value = reader.readValue();
		return new Result(value, reader.pos);
	}

	public record Result(Object value, int end) {
	}

	private Object readValue() {
		skipWhitespace();
		if (pos >= src.length()) {
			throw error("Expected a value but reached the end");
		}
		char c = src.charAt(pos);
		return switch (c) {
			case '{' -> readCompound();
			case '[' -> readListOrArray();
			case '"', '\'' -> readQuoted();
			default -> readUnquotedValue();
		};
	}

	private Map<String, Object> readCompound() {
		expect('{');
		Map<String, Object> map = new LinkedHashMap<>();
		skipWhitespace();
		if (peek() == '}') {
			pos++;
			return map;
		}
		while (true) {
			skipWhitespace();
			String key = peek() == '"' || peek() == '\'' ? readQuoted() : readUnquotedWord();
			if (key.isEmpty()) {
				throw error("Expected a key");
			}
			skipWhitespace();
			expect(':');
			map.put(key, readValue());
			skipWhitespace();
			char c = next();
			if (c == '}') {
				return map;
			}
			if (c != ',') {
				pos--;
				throw error("Expected ',' or '}'");
			}
			skipWhitespace();
			// Tolerate a trailing comma before the closing brace.
			if (peek() == '}') {
				pos++;
				return map;
			}
		}
	}

	private Object readListOrArray() {
		expect('[');
		skipWhitespace();
		if (pos + 1 < src.length() && src.charAt(pos + 1) == ';') {
			char type = Character.toUpperCase(src.charAt(pos));
			if (type == 'B' || type == 'I' || type == 'L') {
				pos += 2;
				return readTypedArray(type);
			}
		}
		List<Object> list = new ArrayList<>();
		if (peek() == ']') {
			pos++;
			return list;
		}
		while (true) {
			list.add(readValue());
			skipWhitespace();
			char c = next();
			if (c == ']') {
				return list;
			}
			if (c != ',') {
				pos--;
				throw error("Expected ',' or ']'");
			}
			skipWhitespace();
			if (peek() == ']') {
				pos++;
				return list;
			}
		}
	}

	private Object readTypedArray(char type) {
		List<Number> values = new ArrayList<>();
		skipWhitespace();
		if (peek() != ']') {
			while (true) {
				int start = pos;
				Object value = readValue();
				if (!(value instanceof Number number)) {
					pos = start;
					throw error("Expected a number in typed array");
				}
				values.add(number);
				skipWhitespace();
				char c = next();
				if (c == ']') {
					break;
				}
				if (c != ',') {
					pos--;
					throw error("Expected ',' or ']'");
				}
			}
		} else {
			pos++;
		}
		switch (type) {
			case 'B' -> {
				byte[] out = new byte[values.size()];
				for (int i = 0; i < out.length; i++) out[i] = values.get(i).byteValue();
				return out;
			}
			case 'I' -> {
				int[] out = new int[values.size()];
				for (int i = 0; i < out.length; i++) out[i] = values.get(i).intValue();
				return out;
			}
			default -> {
				long[] out = new long[values.size()];
				for (int i = 0; i < out.length; i++) out[i] = values.get(i).longValue();
				return out;
			}
		}
	}

	private String readQuoted() {
		char quote = next();
		StringBuilder sb = new StringBuilder();
		while (pos < src.length()) {
			char c = src.charAt(pos++);
			if (c == '\\') {
				if (pos >= src.length()) {
					break;
				}
				char escaped = src.charAt(pos++);
				switch (escaped) {
					case 'n' -> sb.append('\n');
					case 't' -> sb.append('\t');
					case 'r' -> sb.append('\r');
					case 'b' -> sb.append('\b');
					case 'f' -> sb.append('\f');
					case 'u' -> {
						if (pos + 4 > src.length()) {
							throw error("Truncated unicode escape");
						}
						sb.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
						pos += 4;
					}
					default -> sb.append(escaped);
				}
			} else if (c == quote) {
				return sb.toString();
			} else {
				sb.append(c);
			}
		}
		throw error("Unterminated string");
	}

	private Object readUnquotedValue() {
		int start = pos;
		String word = readUnquotedWord();
		if (word.isEmpty()) {
			throw error("Unexpected character '" + src.charAt(start) + "'");
		}
		if (word.equalsIgnoreCase("true")) {
			return Boolean.TRUE;
		}
		if (word.equalsIgnoreCase("false")) {
			return Boolean.FALSE;
		}
		Number number = parseNumber(word);
		return number != null ? number : word;
	}

	private String readUnquotedWord() {
		int start = pos;
		while (pos < src.length() && isUnquotedChar(src.charAt(pos))) {
			pos++;
		}
		return src.substring(start, pos);
	}

	private static boolean isUnquotedChar(char c) {
		return c >= '0' && c <= '9' || c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z'
				|| c == '_' || c == '-' || c == '.' || c == '+';
	}

	static Number parseNumber(String word) {
		if (word.isEmpty()) {
			return null;
		}
		char last = Character.toLowerCase(word.charAt(word.length() - 1));
		String body = word.substring(0, word.length() - 1);
		try {
			switch (last) {
				case 'b':
					return isInteger(body) ? Byte.valueOf((byte) Long.parseLong(body)) : null;
				case 's':
					return isInteger(body) ? Short.valueOf((short) Long.parseLong(body)) : null;
				case 'l':
					return isInteger(body) ? Long.valueOf(Long.parseLong(body)) : null;
				case 'f':
					return isDecimal(body) ? Float.valueOf(Float.parseFloat(body)) : null;
				case 'd':
					return isDecimal(body) ? Double.valueOf(Double.parseDouble(body)) : null;
				default:
					if (isInteger(word)) {
						long value = Long.parseLong(word);
						return value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE ? (Number) (int) value : (Number) value;
					}
					return isDecimal(word) ? Double.valueOf(Double.parseDouble(word)) : null;
			}
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static boolean isInteger(String s) {
		return s.matches("[-+]?\\d+");
	}

	private static boolean isDecimal(String s) {
		return s.matches("[-+]?(\\d+\\.?\\d*|\\.\\d+)([eE][-+]?\\d+)?");
	}

	private void skipWhitespace() {
		while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
			pos++;
		}
	}

	private char peek() {
		return pos < src.length() ? src.charAt(pos) : '\0';
	}

	private char next() {
		if (pos >= src.length()) {
			throw error("Unexpected end of input");
		}
		return src.charAt(pos++);
	}

	private void expect(char c) {
		if (peek() != c) {
			throw error("Expected '" + c + "'");
		}
		pos++;
	}

	private SnbtException error(String message) {
		return new SnbtException(message, Math.min(pos, src.length()));
	}
}
