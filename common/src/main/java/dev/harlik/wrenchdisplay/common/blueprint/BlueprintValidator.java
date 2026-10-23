package dev.harlik.wrenchdisplay.common.blueprint;

import dev.harlik.wrenchdisplay.common.PermissionView;
import dev.harlik.wrenchdisplay.common.Permissions;
import dev.harlik.wrenchdisplay.common.Settings;
import dev.harlik.wrenchdisplay.common.edit.DisplayKind;
import dev.harlik.wrenchdisplay.common.math.Matrices;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Survival checks for a parsed blueprint. Instead of the wrench's per-display clamps, blueprints are limited
 * by overall size, because real models are built from tiny, stretched and offset parts.
 */
public final class BlueprintValidator {
	private static final int MAX_LISTED = 5;

	private BlueprintValidator() {
	}

	/** Block knowledge only the platform has. */
	public interface BlockInfo {
		BlockInfo NONE = (blockId, key) -> null;

		/** The block's default value for a state key, so unchanged extra keys (e.g. leaves' distance) pass. Null if unknown. */
		String defaultValue(String blockId, String key);

		/**
		 * The visible shape of the block inside its unit cube as {minX, minY, minZ, maxX, maxY, maxZ}, or null for a
		 * full cube. Without it a stretched candle would count as long as a stretched full block.
		 */
		default float[] shape(String blockId, Map<String, String> properties) {
			return null;
		}
	}

	/** @return player-facing problems; empty when the blueprint may be placed */
	public static List<String> validate(BlueprintModel model, Settings settings, PermissionView permissions, BlockInfo blocks) {
		List<String> problems = new ArrayList<>();
		Settings.Blueprint limits = settings.blueprint;
		boolean sizeBypass = permissions.has(Permissions.BYPASS_BLUEPRINT_SIZE);

		int maxParts = sizeBypass ? limits.maxPartsWithBypass : limits.maxParts;
		if (model.parts().size() > maxParts) {
			problems.add("Too many parts: " + model.parts().size() + " (limit " + maxParts + ")");
		}
		if (!sizeBypass) {
			float[] size = size(model, blocks);
			if (size[0] > limits.maxModelSize + 1e-3 || size[1] > limits.maxModelSize + 1e-3 || size[2] > limits.maxModelSize + 1e-3) {
				problems.add(String.format(Locale.ROOT, "Model is %.1f×%.1f×%.1f blocks (limit %.0f per side)",
						size[0], size[1], size[2], limits.maxModelSize));
			}
		}

		List<String> partProblems = new ArrayList<>();
		for (int i = 0; i < model.parts().size(); i++) {
			BlueprintPart part = model.parts().get(i);
			String problem = checkPart(part, settings, permissions, blocks, sizeBypass);
			if (problem != null) {
				partProblems.add("Part " + (i + 1) + ": " + problem);
			}
		}
		problems.addAll(partProblems.subList(0, Math.min(MAX_LISTED, partProblems.size())));
		if (partProblems.size() > MAX_LISTED) {
			problems.add("…and " + (partProblems.size() - MAX_LISTED) + " more parts");
		}
		return problems;
	}

	private static String checkPart(BlueprintPart part, Settings settings, PermissionView permissions, BlockInfo blocks,
			boolean sizeBypass) {
		if (part.kind() == DisplayKind.TEXT && !permissions.has(Permissions.TEXT)) {
			return "text displays need permission";
		}
		BlueprintPart.DisplayProps props = part.props();
		if (!permissions.has(Permissions.VISUAL)) {
			if (props.isGlowing()) return "glowing needs permission";
			if (props.hasBrightnessOverride()) return "custom brightness needs permission";
			if (props.viewRange() != null && props.viewRange() > 1.0f) return "view range above 1 needs permission";
		}
		if (part.kind() == DisplayKind.BLOCK) {
			String id = part.block().id();
			if (!permissions.has(Permissions.RESTRICTED_BLOCKS) && settings.survival.restrictedBlocks.contains(id)) {
				return id + " is restricted";
			}
			if (!permissions.has(Permissions.FREE_BLOCK_STATES)) {
				for (Map.Entry<String, String> entry : part.block().properties().entrySet()) {
					if (settings.survival.allowedBlockStateKeys.contains(entry.getKey())) continue;
					String defaultValue = blocks.defaultValue(id, entry.getKey());
					if (defaultValue == null || !defaultValue.equals(entry.getValue())) {
						return "block state " + entry.getKey() + "=" + entry.getValue() + " needs permission";
					}
				}
			}
		}
		if (!sizeBypass) {
			float[] b = bounds(part, blocks);
			float largest = Math.max(b[3] - b[0], Math.max(b[4] - b[1], b[5] - b[2]));
			if (largest > settings.blueprint.maxPartSize + 1e-3) {
				return String.format(Locale.ROOT, "%.1f blocks long (limit %.0f)", largest, settings.blueprint.maxPartSize);
			}
		}
		return null;
	}

	private static float[] bounds(BlueprintPart part, BlockInfo blocks) {
		if (part.kind() == DisplayKind.BLOCK) {
			float[] shape = blocks.shape(part.block().id(), part.block().properties());
			if (shape != null) {
				return Matrices.transformedBounds(Matrices.fromRowMajor(part.matrix()),
						new Vector3f(shape[0], shape[1], shape[2]), new Vector3f(shape[3], shape[4], shape[5]));
			}
		}
		return BlueprintModel.partBounds(part);
	}

	private static float[] size(BlueprintModel model, BlockInfo blocks) {
		float[] total = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
				Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
		for (BlueprintPart part : model.parts()) {
			float[] b = bounds(part, blocks);
			for (int a = 0; a < 3; a++) {
				total[a] = Math.min(total[a], b[a]);
				total[a + 3] = Math.max(total[a + 3], b[a + 3]);
			}
		}
		return new float[] {total[3] - total[0], total[4] - total[1], total[5] - total[2]};
	}
}
