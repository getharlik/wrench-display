package dev.harlik.wrenchdisplay.fabric.wrench;

import dev.harlik.wrenchdisplay.common.edit.Bounds;
import dev.harlik.wrenchdisplay.common.edit.DisplayKind;
import dev.harlik.wrenchdisplay.fabric.FabricDisplay;
import dev.harlik.wrenchdisplay.fabric.mixin.DisplayAccessor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Finds the display under the crosshair. Displays have no hitbox, so the ray is tested against each
 * display's model box after its transformation, the same way the renderer places it.
 */
public final class DisplayTargeting {
	/** Displays can render far from their entity position (blueprint parts are offset several blocks). */
	private static final double SEARCH_MARGIN = 8;

	private DisplayTargeting() {
	}

	public record Hit(Display display, double distance) {
	}

	public static Hit find(ServerPlayer player, double reach) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0f);
		BlockHitResult block = player.level().clip(new ClipContext(eye, eye.add(look.scale(reach)),
				ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		double limit = block.getType() == HitResult.Type.MISS ? reach : block.getLocation().distanceTo(eye);

		Hit best = null;
		for (Display display : player.level().getEntitiesOfClass(Display.class, player.getBoundingBox().inflate(reach + SEARCH_MARGIN))) {
			double distance = intersect(display, eye, look);
			if (distance >= 0 && distance <= limit && (best == null || distance < best.distance())) {
				best = new Hit(display, distance);
			}
		}
		return best;
	}

	/** Model matrix relative to {@code origin} (kept small for float precision). */
	public static Matrix4f worldMatrix(Display display, Vec3 origin) {
		Vec3 pos = display.position().subtract(origin);
		return new Matrix4f()
				.translation((float) pos.x, (float) pos.y, (float) pos.z)
				.rotateYXZ((float) Math.toRadians(-display.getYRot()), (float) Math.toRadians(display.getXRot()), 0)
				.mul(DisplayAccessor.wrenchDisplay$createTransformation(display.getEntityData()).getMatrix());
	}

	/** Distance along the ray to the display's box, or -1 if missed. */
	private static double intersect(Display display, Vec3 eye, Vec3 look) {
		Matrix4f world = worldMatrix(display, eye);
		if (Math.abs(world.determinant()) < 1e-9f) {
			return -1;
		}
		Matrix4f inverse = world.invert(new Matrix4f());
		Vector4f origin = inverse.transform(new Vector4f(0, 0, 0, 1));
		Vector4f dir = inverse.transform(new Vector4f((float) look.x, (float) look.y, (float) look.z, 0));
		DisplayKind kind = FabricDisplay.kindOf(display);
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
