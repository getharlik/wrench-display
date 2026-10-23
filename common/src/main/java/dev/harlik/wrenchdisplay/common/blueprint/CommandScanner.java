package dev.harlik.wrenchdisplay.common.blueprint;

import dev.harlik.wrenchdisplay.common.snbt.SnbtException;
import dev.harlik.wrenchdisplay.common.snbt.SnbtReader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds every {@code summon <display type> <x> <y> <z> {nbt}} in a paste. Anything around the commands
 * (a leading slash, {@code execute … run}, line breaks, several commands in a row, function file lines)
 * is ignored.
 */
public final class CommandScanner {
	private static final String COORD = "([~^][-+.0-9eE]*|[-+.0-9eE]+)";
	private static final Pattern SUMMON = Pattern.compile(
			"\\bsummon\\s+(?:minecraft:)?(block_display|item_display|text_display)\\s+" + COORD + "\\s+" + COORD + "\\s+" + COORD);

	private CommandScanner() {
	}

	public record Summon(String type, Coordinate x, Coordinate y, Coordinate z, Map<String, Object> nbt, int position) {
	}

	/** @param relative {@code ~} coordinate; absolute ones are rebased onto the first command by the extractor */
	public record Coordinate(double value, boolean relative) {
		static Coordinate parse(String token, int position) {
			if (token.startsWith("^")) {
				throw new BlueprintException("Local coordinates (^) are not supported", position);
			}
			boolean relative = token.startsWith("~");
			String number = relative ? token.substring(1) : token;
			try {
				return new Coordinate(number.isEmpty() ? 0 : Double.parseDouble(number), relative);
			} catch (NumberFormatException e) {
				throw new BlueprintException("Invalid coordinate '" + token + "'", position);
			}
		}
	}

	@SuppressWarnings("unchecked")
	public static List<Summon> scan(String text) {
		List<Summon> out = new ArrayList<>();
		Matcher matcher = SUMMON.matcher(text);
		int from = 0;
		while (from < text.length() && matcher.find(from)) {
			int position = matcher.start();
			Coordinate x = Coordinate.parse(matcher.group(2), position);
			Coordinate y = Coordinate.parse(matcher.group(3), position);
			Coordinate z = Coordinate.parse(matcher.group(4), position);
			int end = matcher.end();
			int brace = skipWhitespace(text, end);
			Map<String, Object> nbt = Map.of();
			if (brace < text.length() && text.charAt(brace) == '{') {
				try {
					SnbtReader.Result result = SnbtReader.parseAt(text, brace);
					nbt = (Map<String, Object>) result.value();
					end = result.end();
				} catch (SnbtException e) {
					throw new BlueprintException(e.getMessage(), e.position());
				}
			}
			out.add(new Summon(matcher.group(1), x, y, z, nbt, position));
			from = end;
		}
		return out;
	}

	private static int skipWhitespace(String text, int i) {
		while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
			i++;
		}
		return i;
	}
}
