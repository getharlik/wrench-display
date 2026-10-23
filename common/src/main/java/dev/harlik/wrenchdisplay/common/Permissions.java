package dev.harlik.wrenchdisplay.common;

/** Permission nodes, shared by the Fabric mod (fabric-permissions-api / op level) and the Paper plugin. */
public final class Permissions {
	public static final String USE_WRENCH = "wrenchdisplay.use.wrench";
	public static final String USE_BLUEPRINT = "wrenchdisplay.use.blueprint";

	public static final String BYPASS_OFFSET = "wrenchdisplay.bypass.offset";
	public static final String BYPASS_SCALE = "wrenchdisplay.bypass.scale";
	public static final String BYPASS_CHUNK_LIMIT = "wrenchdisplay.bypass.chunklimit";
	public static final String BYPASS_COST = "wrenchdisplay.bypass.cost";
	public static final String BYPASS_BLUEPRINT_SIZE = "wrenchdisplay.bypass.blueprintsize";

	/** Glow, glow colour, brightness override and view range. */
	public static final String VISUAL = "wrenchdisplay.visual";
	public static final String RESTRICTED_BLOCKS = "wrenchdisplay.blocks.restricted";
	public static final String FREE_BLOCK_STATES = "wrenchdisplay.blockstate.free";
	public static final String TEXT = "wrenchdisplay.text";

	public static final String EDIT_OTHERS = "wrenchdisplay.edit.others";
	/** Displays not placed by this mod (vanilla /summon, other plugins). */
	public static final String EDIT_FOREIGN = "wrenchdisplay.edit.foreign";

	public static final String ADMIN = "wrenchdisplay.admin";

	private Permissions() {
	}

	/** True for nodes every player has unless the server takes them away. */
	public static boolean grantedByDefault(String node) {
		return node.equals(USE_WRENCH) || node.equals(USE_BLUEPRINT);
	}
}
