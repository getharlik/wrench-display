package dev.harlik.wrenchdisplay.common.blueprint;

import dev.harlik.wrenchdisplay.common.text.Message;

import java.util.List;

/**
 * Entry point: pasted text → {@link BlueprintModel}.
 * Several summon commands in one paste become one model (exporters split large models that way).
 */
public final class BlueprintParser {
	private BlueprintParser() {
	}

	public record Limits(int maxLength, int maxParts) {
		public static final Limits UNLIMITED = new Limits(Integer.MAX_VALUE, Integer.MAX_VALUE);
	}

	/** Either a model or a player-facing error message; {@code errorPosition} is -1 when it doesn't apply. */
	public record Result(BlueprintModel model, Message error, int errorPosition) {
		static Result ok(BlueprintModel model) {
			return new Result(model, null, -1);
		}

		static Result fail(Message error, int position) {
			return new Result(null, error, position);
		}

		public boolean isOk() {
			return model != null;
		}
	}

	public static Result parse(String text, Limits limits) {
		if (text == null || text.isBlank()) {
			return Result.fail(Message.of("paste.empty", "The paste is empty"), -1);
		}
		if (text.length() > limits.maxLength()) {
			return Result.fail(Message.of("paste.too_long", "The paste is %s characters long; the limit is %s", text.length(), limits.maxLength()), -1);
		}
		try {
			List<CommandScanner.Summon> summons = CommandScanner.scan(text);
			if (summons.isEmpty()) {
				return Result.fail(Message.of("paste.no_summon", "No summon command for a block, item or text display was found"), -1);
			}
			List<BlueprintPart> parts = new PartExtractor().extract(summons);
			if (parts.isEmpty()) {
				return Result.fail(Message.of("paste.no_displays", "The commands contain no visible displays"), -1);
			}
			if (parts.size() > limits.maxParts()) {
				return Result.fail(Message.of("paste.too_many_parts", "The model has %s parts; the limit is %s", parts.size(), limits.maxParts()), -1);
			}
			return Result.ok(new BlueprintModel(parts));
		} catch (BlueprintException e) {
			return Result.fail(e.message(), e.position());
		}
	}
}
