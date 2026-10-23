package dev.harlik.wrenchdisplay.common.edit;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

/**
 * A display entity as the edit logic sees it. Each platform wraps its entity in this interface;
 * the transformation is in entity-local space (entity position = origin).
 */
public interface EditableDisplay {
	DisplayKind kind();

	/**
	 * Where this display's centre was when it was placed, relative to the entity origin; null when no
	 * placement limit applies (foreign displays). Members of one group share the same entity origin.
	 */
	Vector3f home();

	Matrix4f transformation();

	void setTransformation(Matrix4f matrix);

	/** Block state keys of a block display, in a stable order; empty for other kinds. */
	List<String> stateKeys();

	/** All values the given key can take, in the block's own order. */
	List<String> stateValues(String key);

	String stateValue(String key);

	/** Returns false if the platform refused the value (e.g. invalid for the block). */
	boolean setStateValue(String key, String value);

	/** Item display context ("none", "head", "gui", …); null for other kinds. */
	String itemTransform();

	void setItemTransform(String mode);

	/** "fixed", "vertical", "horizontal" or "center". */
	String billboard();

	void setBillboard(String mode);

	float shadowRadius();

	void setShadowRadius(float radius);

	float shadowStrength();

	void setShadowStrength(float strength);

	/** Brightness override as {block, sky}, or null when the display uses world light. */
	int[] brightness();

	void setBrightness(int[] blockAndSky);

	boolean glowing();

	void setGlowing(boolean glowing);

	/** RGB colour, or -1 for the team/default colour. */
	int glowColor();

	void setGlowColor(int rgb);

	float viewRange();

	void setViewRange(float range);
}
