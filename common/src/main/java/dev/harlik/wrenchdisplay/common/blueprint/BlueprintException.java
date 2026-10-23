package dev.harlik.wrenchdisplay.common.blueprint;

/** A problem with a pasted blueprint, with the character position it was found at (-1 if unknown). */
public class BlueprintException extends RuntimeException {
	private final int position;

	public BlueprintException(String message, int position) {
		super(message);
		this.position = position;
	}

	public int position() {
		return position;
	}
}
