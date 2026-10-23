package dev.harlik.wrenchdisplay.common.edit;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Wrench edits as delta matrices applied to a display's existing transformation.
 *
 * <p>The full matrix is never decomposed and rebuilt, so sheared transforms (common in BDEngine exports)
 * keep their shape. All matrices are in the display entity's local space (entity position = origin);
 * displays placed by this mod have no entity rotation, so local axes equal world axes.
 */
public final class DisplayTransform {
	public enum Axis {
		X, Y, Z
	}

	private static final float MIN_SCALE = 0.01f;

	private DisplayTransform() {
	}

	/** Moves along a world axis. */
	public static Matrix4f move(Matrix4f m, Axis axis, float distance) {
		Vector3f d = unit(axis).mul(distance);
		return new Matrix4f().translation(d).mul(m);
	}

	/** Rotates about a world axis through {@code pivot} (e.g. the display's or the group's centre). */
	public static Matrix4f rotate(Matrix4f m, Axis axis, float degrees, Vector3f pivot) {
		Matrix4f delta = new Matrix4f()
				.translation(pivot)
				.rotate((float) Math.toRadians(degrees), unit(axis))
				.translate(-pivot.x, -pivot.y, -pivot.z);
		return delta.mul(m);
	}

	/**
	 * Changes the display's own scale along one of its local axes (or all, when {@code axis} is null) by
	 * {@code amount}, keeping its visual centre in place. Returns null if the result would collapse.
	 */
	public static Matrix4f scaleLocal(Matrix4f m, Axis axis, float amount, Vector3f localCenter) {
		float[] current = scales(m);
		float fx = 1, fy = 1, fz = 1;
		if (axis == null) {
			float uniform = (current[0] + current[1] + current[2]) / 3;
			float target = uniform + amount;
			if (uniform <= 0 || target < MIN_SCALE) return null;
			fx = fy = fz = target / uniform;
		} else {
			int i = axis.ordinal();
			float target = current[i] + amount;
			if (current[i] <= 0 || target < MIN_SCALE) return null;
			float f = target / current[i];
			if (axis == Axis.X) fx = f;
			else if (axis == Axis.Y) fy = f;
			else fz = f;
		}
		Matrix4f delta = new Matrix4f()
				.translation(localCenter)
				.scale(fx, fy, fz)
				.translate(-localCenter.x, -localCenter.y, -localCenter.z);
		return new Matrix4f(m).mul(delta);
	}

	/** Scales about a world-space pivot along world axes; used for whole groups. */
	public static Matrix4f scaleWorld(Matrix4f m, Axis axis, float factor, Vector3f pivot) {
		float fx = axis == null || axis == Axis.X ? factor : 1;
		float fy = axis == null || axis == Axis.Y ? factor : 1;
		float fz = axis == null || axis == Axis.Z ? factor : 1;
		Matrix4f delta = new Matrix4f()
				.translation(pivot)
				.scale(fx, fy, fz)
				.translate(-pivot.x, -pivot.y, -pivot.z);
		return delta.mul(m);
	}

	/** Where the display's visual centre ends up, in entity-local space. */
	public static Vector3f center(Matrix4f m, DisplayKind kind) {
		return m.transformPosition(kind.localCenter());
	}

	/** Length of each local axis after transformation (the "scale" shown in the HUD). */
	public static float[] scales(Matrix4f m) {
		return new float[] {
				new Vector3f(m.m00(), m.m01(), m.m02()).length(),
				new Vector3f(m.m10(), m.m11(), m.m12()).length(),
				new Vector3f(m.m20(), m.m21(), m.m22()).length()
		};
	}

	/** Approximate yaw/pitch/roll in degrees, for display only (the matrix may contain shear). */
	public static float[] eulerDegrees(Matrix4f m) {
		Quaternionf q = m.getNormalizedRotation(new Quaternionf());
		Vector3f euler = q.getEulerAnglesYXZ(new Vector3f());
		return new float[] {(float) Math.toDegrees(euler.y), (float) Math.toDegrees(euler.x), (float) Math.toDegrees(euler.z)};
	}

	private static Vector3f unit(Axis axis) {
		return switch (axis) {
			case X -> new Vector3f(1, 0, 0);
			case Y -> new Vector3f(0, 1, 0);
			case Z -> new Vector3f(0, 0, 1);
		};
	}
}
