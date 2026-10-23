package dev.harlik.wrenchdisplay.common.edit;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory {@link EditableDisplay} for tests. */
class FakeDisplay implements EditableDisplay {
	final DisplayKind kind;
	Vector3f home;
	Matrix4f matrix;
	final Map<String, List<String>> stateOptions = new LinkedHashMap<>();
	final Map<String, String> state = new LinkedHashMap<>();
	String itemTransform = "none";
	String billboard = "fixed";
	float shadowRadius;
	float shadowStrength = 1;
	int[] brightness;
	boolean glowing;
	int glowColor = -1;
	float viewRange = 1;

	FakeDisplay(DisplayKind kind, Matrix4f matrix) {
		this.kind = kind;
		this.matrix = new Matrix4f(matrix);
		this.home = DisplayTransform.center(matrix, kind);
	}

	@Override public DisplayKind kind() { return kind; }
	@Override public Vector3f home() { return home == null ? null : new Vector3f(home); }
	@Override public Matrix4f transformation() { return new Matrix4f(matrix); }
	@Override public void setTransformation(Matrix4f m) { matrix = new Matrix4f(m); }
	@Override public List<String> stateKeys() { return List.copyOf(stateOptions.keySet()); }
	@Override public List<String> stateValues(String key) { return stateOptions.getOrDefault(key, List.of()); }
	@Override public String stateValue(String key) { return state.get(key); }
	@Override public boolean setStateValue(String key, String value) { state.put(key, value); return true; }
	@Override public String itemTransform() { return itemTransform; }
	@Override public void setItemTransform(String mode) { itemTransform = mode; }
	@Override public String billboard() { return billboard; }
	@Override public void setBillboard(String mode) { billboard = mode; }
	@Override public float shadowRadius() { return shadowRadius; }
	@Override public void setShadowRadius(float radius) { shadowRadius = radius; }
	@Override public float shadowStrength() { return shadowStrength; }
	@Override public void setShadowStrength(float strength) { shadowStrength = strength; }
	@Override public int[] brightness() { return brightness; }
	@Override public void setBrightness(int[] blockAndSky) { brightness = blockAndSky; }
	@Override public boolean glowing() { return glowing; }
	@Override public void setGlowing(boolean glowing) { this.glowing = glowing; }
	@Override public int glowColor() { return glowColor; }
	@Override public void setGlowColor(int rgb) { glowColor = rgb; }
	@Override public float viewRange() { return viewRange; }
	@Override public void setViewRange(float range) { viewRange = range; }
}
