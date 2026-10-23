package dev.harlik.wrenchdisplay.common.math;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Conversions between the row-major float arrays used by display NBT and JOML's column-major matrices.
 */
public final class Matrices {
	private Matrices() {
	}

	public static Matrix4f fromRowMajor(float[] rowMajor) {
		return new Matrix4f().set(rowMajor).transpose();
	}

	public static float[] toRowMajor(Matrix4f matrix) {
		return new Matrix4f(matrix).transpose().get(new float[16]);
	}

	/** Axis-aligned bounds of {@code matrix} applied to the box [min, max]; returns {minX, minY, minZ, maxX, maxY, maxZ}. */
	public static float[] transformedBounds(Matrix4f matrix, Vector3f min, Vector3f max) {
		float[] out = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
				Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
		Vector3f corner = new Vector3f();
		for (int i = 0; i < 8; i++) {
			corner.set((i & 1) == 0 ? min.x : max.x, (i & 2) == 0 ? min.y : max.y, (i & 4) == 0 ? min.z : max.z);
			matrix.transformPosition(corner);
			out[0] = Math.min(out[0], corner.x);
			out[1] = Math.min(out[1], corner.y);
			out[2] = Math.min(out[2], corner.z);
			out[3] = Math.max(out[3], corner.x);
			out[4] = Math.max(out[4], corner.y);
			out[5] = Math.max(out[5], corner.z);
		}
		return out;
	}
}
