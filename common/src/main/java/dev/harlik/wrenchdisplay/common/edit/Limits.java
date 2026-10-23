package dev.harlik.wrenchdisplay.common.edit;

import dev.harlik.wrenchdisplay.common.PermissionView;
import dev.harlik.wrenchdisplay.common.Permissions;
import dev.harlik.wrenchdisplay.common.Settings;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

/**
 * Survival limits for wrench edits.
 *
 * <p>A change is refused only when it moves a value <em>further</em> out of range; values that are already
 * outside (for example a 0.03-scale part from a blueprint placed with a bypass) are never forced back, and
 * edits that bring them closer are always allowed.
 */
public final class Limits {
	private static final float TOLERANCE = 1e-4f;

	private Limits() {
	}

	/** Single display: offset from its home and per-axis scale. */
	public static boolean allowPart(EditableDisplay display, Matrix4f before, Matrix4f after, Settings settings, PermissionView permissions) {
		Settings.Survival survival = settings.survival;
		if (!permissions.has(Permissions.BYPASS_OFFSET) && display.home() != null) {
			Vector3f home = display.home();
			float was = offsetExcess(DisplayTransform.center(before, display.kind()).sub(home), survival.maxOffset);
			float now = offsetExcess(DisplayTransform.center(after, display.kind()).sub(home), survival.maxOffset);
			if (now > was + TOLERANCE) return false;
		}
		if (!permissions.has(Permissions.BYPASS_SCALE)) {
			float[] was = DisplayTransform.scales(before);
			float[] now = DisplayTransform.scales(after);
			for (int i = 0; i < 3; i++) {
				if (scaleExcess(now[i], survival) > scaleExcess(was[i], survival) + TOLERANCE) return false;
			}
		}
		return true;
	}

	/**
	 * Whole group: its centroid may not drift further than {@code maxOffset} from where it was placed, and the
	 * group must keep fitting the blueprint size limits.
	 */
	public static boolean allowGroup(List<EditableDisplay> members, List<Matrix4f> before, List<Matrix4f> after,
			Settings settings, PermissionView permissions) {
		if (!permissions.has(Permissions.BYPASS_OFFSET)) {
			Vector3f home = homeCentroid(members);
			if (home != null) {
				float was = offsetExcess(centroid(members, before).sub(home), settings.survival.maxOffset);
				float now = offsetExcess(centroid(members, after).sub(home), settings.survival.maxOffset);
				if (now > was + TOLERANCE) return false;
			}
		}
		if (!permissions.has(Permissions.BYPASS_BLUEPRINT_SIZE)) {
			Settings.Blueprint blueprint = settings.blueprint;
			if (sizeExcess(members, after, blueprint) > sizeExcess(members, before, blueprint) + TOLERANCE) return false;
		}
		return true;
	}

	public static Vector3f centroid(List<EditableDisplay> members, List<Matrix4f> matrices) {
		Vector3f sum = new Vector3f();
		for (int i = 0; i < members.size(); i++) {
			sum.add(DisplayTransform.center(matrices.get(i), members.get(i).kind()));
		}
		return sum.div(Math.max(1, members.size()));
	}

	private static Vector3f homeCentroid(List<EditableDisplay> members) {
		Vector3f sum = new Vector3f();
		for (EditableDisplay member : members) {
			if (member.home() == null) return null;
			sum.add(member.home());
		}
		return sum.div(Math.max(1, members.size()));
	}

	private static float offsetExcess(Vector3f offset, double max) {
		float worst = Math.max(Math.abs(offset.x), Math.max(Math.abs(offset.y), Math.abs(offset.z)));
		return (float) Math.max(0, worst - max);
	}

	private static float scaleExcess(float scale, Settings.Survival survival) {
		if (scale < survival.minScale) return (float) (survival.minScale - scale);
		if (scale > survival.maxScale) return (float) (scale - survival.maxScale);
		return 0;
	}

	private static float sizeExcess(List<EditableDisplay> members, List<Matrix4f> matrices, Settings.Blueprint limits) {
		float[] total = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
				Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
		float excess = 0;
		for (int i = 0; i < members.size(); i++) {
			float[] b = Bounds.of(members.get(i).kind(), matrices.get(i));
			for (int a = 0; a < 3; a++) {
				total[a] = Math.min(total[a], b[a]);
				total[a + 3] = Math.max(total[a + 3], b[a + 3]);
				excess = Math.max(excess, (float) (b[a + 3] - b[a] - limits.maxPartSize));
			}
		}
		for (int a = 0; a < 3; a++) {
			excess = Math.max(excess, (float) (total[a + 3] - total[a] - limits.maxModelSize));
		}
		return Math.max(0, excess);
	}
}
