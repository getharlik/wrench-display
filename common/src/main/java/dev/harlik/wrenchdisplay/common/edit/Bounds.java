package dev.harlik.wrenchdisplay.common.edit;

import dev.harlik.wrenchdisplay.common.math.Matrices;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Approximate model boxes per display kind, used for limits, targeting and outlines. */
public final class Bounds {
	private Bounds() {
	}

	public static Vector3f min(DisplayKind kind) {
		return switch (kind) {
			case BLOCK -> new Vector3f(0, 0, 0);
			case ITEM -> new Vector3f(-0.5f, -0.5f, -0.5f);
			// Text size depends on its content; this is a rough single-line box around the anchor.
			case TEXT -> new Vector3f(-0.5f, 0, -0.01f);
		};
	}

	public static Vector3f max(DisplayKind kind) {
		return switch (kind) {
			case BLOCK -> new Vector3f(1, 1, 1);
			case ITEM -> new Vector3f(0.5f, 0.5f, 0.5f);
			case TEXT -> new Vector3f(0.5f, 0.3f, 0.01f);
		};
	}

	/** Axis-aligned bounds {minX, minY, minZ, maxX, maxY, maxZ} of the transformed model box. */
	public static float[] of(DisplayKind kind, Matrix4f matrix) {
		return Matrices.transformedBounds(matrix, min(kind), max(kind));
	}
}
