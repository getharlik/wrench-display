package dev.harlik.wrenchdisplay.common.blueprint;

/**
 * What a blueprint part costs in survival.
 *
 * <p>Block parts cost the block's item; the platform resolves block id → item (e.g. wall_torch → torch).
 * Item parts cost the item itself. {@code matchKey} narrows which stacks qualify:
 * <ul>
 *   <li>{@code null} – any stack of that id</li>
 *   <li>{@code skin:<url>} – a player head wearing that skin (timestamps/profile ids are ignored)</li>
 *   <li>{@code components:<canonical snbt>} – a stack whose components equal these</li>
 * </ul>
 */
public record MaterialKey(Type type, String id, String matchKey) {
	public enum Type {
		BLOCK, ITEM
	}

	public static MaterialKey block(String id) {
		return new MaterialKey(Type.BLOCK, id, null);
	}

	public static MaterialKey item(String id, String matchKey) {
		return new MaterialKey(Type.ITEM, id, matchKey);
	}
}
