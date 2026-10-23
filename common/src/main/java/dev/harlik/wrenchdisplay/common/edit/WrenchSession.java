package dev.harlik.wrenchdisplay.common.edit;

import java.util.List;

/** Per-player wrench state: selected property, step level, group/part mode and selected block-state key. */
public final class WrenchSession {
	private EditProperty property = EditProperty.MOVE_X;
	private int stepLevel = StepTable.DEFAULT_LEVEL;
	private boolean groupMode = true;
	private int stateKeyIndex;

	public EditProperty property() {
		return property;
	}

	/** The selected property if the target offers it, otherwise the first one it does. */
	public EditProperty property(List<EditProperty> available) {
		if (!available.contains(property) && !available.isEmpty()) {
			property = available.get(0);
		}
		return property;
	}

	/** Steps through {@code available} in {@code direction} (+1 / -1), wrapping around. */
	public EditProperty cycle(List<EditProperty> available, int direction) {
		if (available.isEmpty()) {
			return property;
		}
		int index = available.indexOf(property);
		if (index < 0) {
			index = direction > 0 ? -1 : 0;
		}
		property = available.get(Math.floorMod(index + Integer.signum(direction), available.size()));
		return property;
	}

	public int stepLevel() {
		return stepLevel;
	}

	public void changeStep(int direction) {
		stepLevel = StepTable.clampLevel(stepLevel + Integer.signum(direction));
	}

	public boolean groupMode() {
		return groupMode;
	}

	public void toggleGroupMode() {
		groupMode = !groupMode;
	}

	public int stateKeyIndex() {
		return stateKeyIndex;
	}

	public void setStateKeyIndex(int index) {
		stateKeyIndex = index;
	}
}
