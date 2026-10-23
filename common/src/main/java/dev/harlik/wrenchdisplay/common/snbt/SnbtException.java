package dev.harlik.wrenchdisplay.common.snbt;

public class SnbtException extends RuntimeException {
	private final int position;

	public SnbtException(String message, int position) {
		super(message + " at position " + position);
		this.position = position;
	}

	public int position() {
		return position;
	}
}
