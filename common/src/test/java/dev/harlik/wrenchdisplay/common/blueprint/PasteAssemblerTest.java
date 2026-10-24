package dev.harlik.wrenchdisplay.common.blueprint;

import dev.harlik.wrenchdisplay.common.blueprint.PasteAssembler.Status;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasteAssemblerTest {
	private static final int MAX = 131072;

	private static String send(PasteAssembler assembler, int session, String paste) {
		String[] pieces = PasteAssembler.split(paste);
		for (int i = 0; i < pieces.length; i++) {
			Status status = assembler.accept(session, i, pieces.length, pieces[i], MAX);
			if (status == Status.COMPLETE) return assembler.result();
			assertEquals(Status.PENDING, status);
		}
		return null;
	}

	@Test
	void bigPasteRoundTrips() {
		String paste = BlueprintParserTest.fixture("desk.txt").repeat(20); // ~94K chars, 12 pieces
		assertTrue(PasteAssembler.split(paste).length > 1);
		assertEquals(paste, send(new PasteAssembler(), 7, paste));
	}

	@Test
	void smallPasteIsOnePiece() {
		assertEquals(1, PasteAssembler.split("summon block_display ~ ~ ~").length);
		assertEquals("abc", send(new PasteAssembler(), 1, "abc"));
		assertEquals(1, PasteAssembler.split("").length);
	}

	@Test
	void newPasteDropsUnfinishedOne() {
		PasteAssembler assembler = new PasteAssembler();
		String first = "a".repeat(PasteAssembler.CHUNK_CHARS * 2);
		assertEquals(Status.PENDING, assembler.accept(1, 0, 2, first.substring(0, PasteAssembler.CHUNK_CHARS), MAX));
		assertEquals("xyz", send(assembler, 2, "xyz"));
	}

	@Test
	void rejectsBadPieces() {
		PasteAssembler assembler = new PasteAssembler();
		assertEquals(Status.REJECTED, assembler.accept(1, 1, 2, "x", MAX), "must start at 0");
		assertEquals(Status.PENDING, assembler.accept(2, 0, 3, "x", MAX));
		assertEquals(Status.REJECTED, assembler.accept(2, 2, 3, "x", MAX), "skipped a piece");
		assertEquals(Status.REJECTED, assembler.accept(3, 0, 1000, "x", MAX), "too many pieces for the limit");
		assertEquals(Status.REJECTED, assembler.accept(4, 0, 1, "x".repeat(PasteAssembler.CHUNK_CHARS + 1), MAX), "piece too big");
		assertEquals(Status.REJECTED, assembler.accept(5, 0, 1, "x".repeat(200), 100), "longer than the limit");
		assertEquals(Status.PENDING, assembler.accept(6, 0, 2, "x", MAX));
		assertEquals(Status.REJECTED, assembler.accept(6, 1, 3, "x", MAX), "piece count changed");
	}
}
