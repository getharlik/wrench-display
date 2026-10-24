package dev.harlik.wrenchdisplay.common.blueprint;

/**
 * Joins a paste that the client mod sent in several pieces. A single packet from the client is limited to about
 * 32 KB, so long pastes are split into {@link #CHUNK_CHARS}-character pieces and numbered.
 *
 * <p>One instance per player. A piece from a new paste (different session id) drops any unfinished one. Pieces
 * must arrive in order (the connection keeps them in order), and the total is capped by {@code maxLength}, so a
 * misbehaving client can't make the server hold more than one paste's worth of text.
 */
public final class PasteAssembler {
	/** Characters per piece; UTF-8 needs at most 3 bytes per char, which keeps a piece under the packet limit. */
	public static final int CHUNK_CHARS = 8000;

	private final StringBuilder text = new StringBuilder();
	private int session;
	private int expectedIndex = -1;
	private int total;

	public enum Status {
		/** Piece stored; more are expected. */
		PENDING,
		/** That was the last piece; {@link #result()} holds the whole paste. */
		COMPLETE,
		/** Piece rejected (out of order, too long, bad numbering); the paste was dropped. */
		REJECTED
	}

	/** How many pieces a paste of {@code maxLength} characters may have. */
	public static int maxChunks(int maxLength) {
		return Math.max(1, (maxLength + CHUNK_CHARS - 1) / CHUNK_CHARS);
	}

	public Status accept(int sessionId, int index, int chunkCount, String piece, int maxLength) {
		if (sessionId != session || expectedIndex < 0) {
			reset();
			session = sessionId;
			if (index != 0) return Status.REJECTED;
		}
		if (index != expectedIndex + 1 || chunkCount < 1 || chunkCount > maxChunks(maxLength)
				|| (expectedIndex >= 0 && chunkCount != total) || piece.length() > CHUNK_CHARS
				|| text.length() + piece.length() > maxLength) {
			reset();
			return Status.REJECTED;
		}
		total = chunkCount;
		expectedIndex = index;
		text.append(piece);
		return index == chunkCount - 1 ? Status.COMPLETE : Status.PENDING;
	}

	/** The joined paste after {@link Status#COMPLETE}; clears the assembler. */
	public String result() {
		String out = text.toString();
		reset();
		return out;
	}

	private void reset() {
		text.setLength(0);
		expectedIndex = -1;
		total = 0;
	}

	/** Splits a paste into the pieces the client sends. */
	public static String[] split(String paste) {
		int count = Math.max(1, (paste.length() + CHUNK_CHARS - 1) / CHUNK_CHARS);
		String[] out = new String[count];
		for (int i = 0; i < count; i++) {
			out[i] = paste.substring(i * CHUNK_CHARS, Math.min(paste.length(), (i + 1) * CHUNK_CHARS));
		}
		return out;
	}
}
