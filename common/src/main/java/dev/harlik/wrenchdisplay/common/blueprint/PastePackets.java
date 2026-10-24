package dev.harlik.wrenchdisplay.common.blueprint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Byte layout of the client-mod packets, for servers without Minecraft's codecs (the Paper plugin). Matches the
 * Fabric payload codecs: VarInts, and strings as a VarInt byte length followed by UTF-8.
 */
public final class PastePackets {
	public static final String OPEN_PASTE = "wrench-display:open_paste";
	public static final String PASTE_CHUNK = "wrench-display:paste_chunk";

	private PastePackets() {
	}

	public record Chunk(int session, int index, int count, String text) {
	}

	public static byte[] encodeOpenPaste(int maxLength) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		writeVarInt(out, maxLength);
		return out.toByteArray();
	}

	public static byte[] encodeChunk(Chunk chunk) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		writeVarInt(out, chunk.session());
		writeVarInt(out, chunk.index());
		writeVarInt(out, chunk.count());
		byte[] text = chunk.text().getBytes(StandardCharsets.UTF_8);
		writeVarInt(out, text.length);
		out.writeBytes(text);
		return out.toByteArray();
	}

	/** Returns null for malformed or oversized data. */
	public static Chunk decodeChunk(byte[] data) {
		try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
			int session = readVarInt(in);
			int index = readVarInt(in);
			int count = readVarInt(in);
			int length = readVarInt(in);
			if (length < 0 || length > PasteAssembler.CHUNK_CHARS * 3) return null;
			byte[] text = in.readNBytes(length);
			if (text.length != length) return null;
			return new Chunk(session, index, count, new String(text, StandardCharsets.UTF_8));
		} catch (IOException e) {
			return null;
		}
	}

	private static void writeVarInt(ByteArrayOutputStream out, int value) {
		while ((value & ~0x7F) != 0) {
			out.write((value & 0x7F) | 0x80);
			value >>>= 7;
		}
		out.write(value);
	}

	private static int readVarInt(DataInputStream in) throws IOException {
		int value = 0;
		for (int shift = 0; shift < 35; shift += 7) {
			int b = in.readUnsignedByte();
			value |= (b & 0x7F) << shift;
			if ((b & 0x80) == 0) return value;
		}
		throw new IOException("VarInt too long");
	}
}
