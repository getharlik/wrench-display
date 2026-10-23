package dev.harlik.wrenchdisplay.common.edit;

/** Step sizes per level, selected with the {@link EditProperty#STEP} property. */
public final class StepTable {
	public static final int LEVELS = 5;
	public static final int DEFAULT_LEVEL = 1;

	private static final float[] MOVE = {1 / 16f, 1 / 8f, 1 / 4f, 1 / 2f, 1};
	private static final String[] MOVE_LABELS = {"1/16", "1/8", "1/4", "1/2", "1"};
	private static final float[] ROTATE_DEGREES = {1, 5, 15, 45, 90};
	private static final float[] SCALE = {1 / 16f, 1 / 8f, 1 / 4f, 1 / 2f, 1};
	private static final float[] FINE = {0.01f, 0.05f, 0.1f, 0.25f, 0.5f};

	private StepTable() {
	}

	public static int clampLevel(int level) {
		return Math.max(0, Math.min(LEVELS - 1, level));
	}

	public static float move(int level) {
		return MOVE[clampLevel(level)];
	}

	public static float rotateDegrees(int level) {
		return ROTATE_DEGREES[clampLevel(level)];
	}

	public static float scale(int level) {
		return SCALE[clampLevel(level)];
	}

	/** Shadow radius/strength and view range. */
	public static float fine(int level) {
		return FINE[clampLevel(level)];
	}

	public static String label(int level) {
		int l = clampLevel(level);
		return MOVE_LABELS[l] + " · " + (int) ROTATE_DEGREES[l] + "°";
	}
}
