package dev.harlik.wrenchdisplay.paper;

import dev.harlik.wrenchdisplay.common.edit.Bounds;
import dev.harlik.wrenchdisplay.common.edit.DisplayKind;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Finds the display under the crosshair. Displays have no hitbox, so the ray is tested against each display's
 * model box after its transformation, the same way the client renders it.
 */
final class DisplayTargeting {
	/** Displays can render far from their entity position (blueprint parts are offset several blocks). */
	private static final double SEARCH_MARGIN = 8;

	private DisplayTargeting() {
	}

	record Hit(Display display, double distance) {
	}

	static Hit find(Player player, double reach) {
		Location eye = player.getEyeLocation();
		Vector look = eye.getDirection();
		RayTraceResult block = player.getWorld().rayTraceBlocks(eye, look, reach, FluidCollisionMode.NEVER, true);
		double limit = block == null ? reach : block.getHitPosition().distance(eye.toVector());

		Hit best = null;
		double radius = reach + SEARCH_MARGIN;
		for (Display display : player.getWorld().getNearbyEntitiesByType(Display.class, eye, radius)) {
			double distance = intersect(display, eye, look);
			if (distance >= 0 && distance <= limit && (best == null || distance < best.distance())) {
				best = new Hit(display, distance);
			}
		}
		return best;
	}

	/** Model matrix relative to {@code origin} (kept small for float precision). */
	static Matrix4f worldMatrix(Display display, Location origin) {
		Location pos = display.getLocation();
		return new Matrix4f()
				.translation((float) (pos.getX() - origin.getX()), (float) (pos.getY() - origin.getY()), (float) (pos.getZ() - origin.getZ()))
				.rotateYXZ((float) Math.toRadians(-pos.getYaw()), (float) Math.toRadians(pos.getPitch()), 0)
				.mul(PaperDisplay.matrix(display));
	}

	/** Distance along the ray to the display's box, or -1 if missed. */
	private static double intersect(Display display, Location eye, Vector look) {
		Matrix4f world = worldMatrix(display, eye);
		if (Math.abs(world.determinant()) < 1e-9f) return -1;
		Matrix4f inverse = world.invert(new Matrix4f());
		Vector4f origin = inverse.transform(new Vector4f(0, 0, 0, 1));
		Vector4f dir = inverse.transform(new Vector4f((float) look.getX(), (float) look.getY(), (float) look.getZ(), 0));
		DisplayKind kind = PaperDisplay.kindOf(display);
		Vector3f min = Bounds.min(kind);
		Vector3f max = Bounds.max(kind);

		// Slab test; t stays in world units because the world ray direction has unit length.
		double tMin = 0;
		double tMax = Double.MAX_VALUE;
		float[] o = {origin.x, origin.y, origin.z};
		float[] d = {dir.x, dir.y, dir.z};
		float[] lo = {min.x, min.y, min.z};
		float[] hi = {max.x, max.y, max.z};
		for (int i = 0; i < 3; i++) {
			if (Math.abs(d[i]) < 1e-9f) {
				if (o[i] < lo[i] || o[i] > hi[i]) return -1;
				continue;
			}
			double t1 = (lo[i] - o[i]) / d[i];
			double t2 = (hi[i] - o[i]) / d[i];
			tMin = Math.max(tMin, Math.min(t1, t2));
			tMax = Math.min(tMax, Math.max(t1, t2));
			if (tMin > tMax) return -1;
		}
		return tMin;
	}
}
