package dev.harlik.wrenchdisplay.common.snbt;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SnbtReaderTest {
	@Test
	void typedNumbers() {
		Map<?, ?> map = (Map<?, ?>) SnbtReader.parse("{a:1b,b:2s,c:3,d:4L,e:0.5f,f:1.5,g:2d,h:2.05e-8f,i:-0.0000999933f,j:5000000000}");
		assertEquals((byte) 1, map.get("a"));
		assertEquals((short) 2, map.get("b"));
		assertEquals(3, map.get("c"));
		assertEquals(4L, map.get("d"));
		assertEquals(0.5f, map.get("e"));
		assertEquals(1.5, map.get("f"));
		assertEquals(2.0, map.get("g"));
		assertEquals(2.05e-8f, map.get("h"));
		assertEquals(-0.0000999933f, map.get("i"));
		assertEquals(5000000000L, map.get("j"));
	}

	@Test
	void typedArraysIncludingEmpty() {
		Map<?, ?> map = (Map<?, ?>) SnbtReader.parse("{a:[I;1,-2,3,4],b:[I;],c:[B;1b,0b],d:[L;5L]}");
		assertArrayEquals(new int[] {1, -2, 3, 4}, (int[]) map.get("a"));
		assertArrayEquals(new int[0], (int[]) map.get("b"));
		assertArrayEquals(new byte[] {1, 0}, (byte[]) map.get("c"));
		assertArrayEquals(new long[] {5}, (long[]) map.get("d"));
	}

	@Test
	void quotedKeysStringsAndJsonStyleLists() {
		Map<?, ?> map = (Map<?, ?>) SnbtReader.parse(
				"{\"minecraft:profile\":{name:'it\\'s'},text:[{\"text\":\"●\",\"bold\":true}],id:\"minecraft:stone\"}");
		assertEquals(Map.of("name", "it's"), map.get("minecraft:profile"));
		assertEquals(List.of(Map.of("text", "●", "bold", true)), map.get("text"));
		assertEquals("minecraft:stone", map.get("id"));
	}

	@Test
	void unquotedWordsStayStrings() {
		Map<?, ?> map = (Map<?, ?>) SnbtReader.parse("{billboard:center,item_display:none,x:1.2.3}");
		assertEquals("center", map.get("billboard"));
		assertEquals("none", map.get("item_display"));
		assertEquals("1.2.3", map.get("x"));
	}

	@Test
	void parseAtReportsEnd() {
		String src = "summon x ~ ~ ~ {a:{b:[1,2]}} trailing";
		SnbtReader.Result result = SnbtReader.parseAt(src, src.indexOf('{'));
		assertEquals(" trailing", src.substring(result.end()));
	}

	@Test
	void errorsCarryPosition() {
		SnbtException e = assertThrows(SnbtException.class, () -> SnbtReader.parse("{a:1,b:}"));
		assertEquals(7, e.position());
		assertThrows(SnbtException.class, () -> SnbtReader.parse("{a:\"unterminated}"));
	}

	@Test
	void writerRoundTrips() {
		String src = "{a:1b,b:[I;1,2,3,4],\"minecraft:profile\":{properties:[{name:\"textures\",value:\"abc\"}]},f:0.5f,s:\"q\\\"uote\"}";
		Object parsed = SnbtReader.parse(src);
		Object again = SnbtReader.parse(SnbtWriter.write(parsed));
		assertEquals(SnbtWriter.writeCanonical(parsed), SnbtWriter.writeCanonical(again));
	}
}
