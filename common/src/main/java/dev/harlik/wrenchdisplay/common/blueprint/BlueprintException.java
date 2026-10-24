package dev.harlik.wrenchdisplay.common.blueprint;

import dev.harlik.wrenchdisplay.common.text.Message;

/** A problem with a pasted blueprint, with the character position it was found at (-1 if unknown). */
public class BlueprintException extends RuntimeException {
	private final Message message;
	private final int position;

	public BlueprintException(Message message, int position) {
		super(message.toFallbackString());
		this.message = message;
		this.position = position;
	}

	public Message message() {
		return message;
	}

	public int position() {
		return position;
	}
}
