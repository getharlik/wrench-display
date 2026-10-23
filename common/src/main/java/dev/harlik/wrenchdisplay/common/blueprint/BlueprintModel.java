package dev.harlik.wrenchdisplay.common.blueprint;

import dev.harlik.wrenchdisplay.common.math.Matrices;
import dev.harlik.wrenchdisplay.common.edit.Bounds;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A parsed blueprint: its parts, overall bounds and what it costs to place in survival. */
public final class BlueprintModel {
	private final List<BlueprintPart> parts;
	private final float[] bounds;
	private final Map<MaterialKey, Integer> materials;

	public BlueprintModel(List<BlueprintPart> parts) {
		this.parts = List.copyOf(parts);
		this.bounds = computeBounds(this.parts);
		this.materials = Collections.unmodifiableMap(computeMaterials(this.parts));
	}

	public List<BlueprintPart> parts() {
		return parts;
	}

	/** {minX, minY, minZ, maxX, maxY, maxZ} relative to the paste origin. */
	public float[] bounds() {
		return bounds.clone();
	}

	public float[] size() {
		return new float[] {bounds[3] - bounds[0], bounds[4] - bounds[1], bounds[5] - bounds[2]};
	}

	/** Items needed in survival, keyed by what has to match. Text parts are free. */
	public Map<MaterialKey, Integer> materials() {
		return materials;
	}

	/** World-space axis-aligned bounds of one part, same layout as {@link #bounds()}. */
	public static float[] partBounds(BlueprintPart part) {
		return Bounds.of(part.kind(), Matrices.fromRowMajor(part.matrix()));
	}

	private static float[] computeBounds(List<BlueprintPart> parts) {
		if (parts.isEmpty()) {
			return new float[6];
		}
		float[] out = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
				Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
		for (BlueprintPart part : parts) {
			float[] b = partBounds(part);
			for (int i = 0; i < 3; i++) {
				out[i] = Math.min(out[i], b[i]);
				out[i + 3] = Math.max(out[i + 3], b[i + 3]);
			}
		}
		return out;
	}

	private static Map<MaterialKey, Integer> computeMaterials(List<BlueprintPart> parts) {
		Map<MaterialKey, Integer> out = new LinkedHashMap<>();
		for (BlueprintPart part : parts) {
			switch (part.kind()) {
				case BLOCK -> out.merge(MaterialKey.block(part.block().id()), 1, Integer::sum);
				case ITEM -> out.merge(MaterialKey.item(part.item().id(), part.item().matchKey()), part.item().count(), Integer::sum);
				default -> {
				}
			}
		}
		return out;
	}
}
