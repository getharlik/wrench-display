package dev.harlik.wrenchdisplay.common.edit;

import dev.harlik.wrenchdisplay.common.PermissionView;
import dev.harlik.wrenchdisplay.common.Permissions;
import dev.harlik.wrenchdisplay.common.Settings;
import dev.harlik.wrenchdisplay.common.edit.DisplayTransform.Axis;
import dev.harlik.wrenchdisplay.common.text.Message;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Applies a wrench click (+1 / -1) on the selected property to a display or a whole group. */
public final class PropertyEditor {
	public static final List<String> ITEM_TRANSFORMS = List.of(
			"none", "thirdperson_lefthand", "thirdperson_righthand", "firstperson_lefthand", "firstperson_righthand",
			"head", "gui", "ground", "fixed");
	public static final List<String> BILLBOARDS = List.of("fixed", "vertical", "horizontal", "center");
	/** -1 = default glow colour, then the 16 dye colours. */
	public static final int[] GLOW_COLORS = {
			-1, 0xF9FFFE, 0xF9801D, 0xC74EBD, 0x3AB3DA, 0xFED83D, 0x80C71F, 0xF38BAA, 0x474F52,
			0x9D9D97, 0x169C9C, 0x8932B8, 0x3C44AA, 0x835432, 0x5E7C16, 0xB02E26, 0x1D1D21};

	private PropertyEditor() {
	}

	public enum Outcome {
		CHANGED,
		/** Refused by a survival limit; nothing was changed. */
		LIMIT,
		/** Nothing to do (e.g. no other value exists, or REMOVE which the platform handles). */
		UNCHANGED
	}

	/**
	 * @param focus     the display the player is looking at
	 * @param members   the displays to change: the whole group in group mode, otherwise just {@code focus}
	 */
	public static Outcome adjust(EditableDisplay focus, List<EditableDisplay> members, EditProperty property, int direction,
			WrenchSession session, Settings settings, PermissionView permissions) {
		int dir = Integer.signum(direction);
		boolean group = members.size() > 1 || members.get(0) != focus;
		int step = session.stepLevel();
		switch (property) {
			case MODE -> {
				session.toggleGroupMode();
				return Outcome.CHANGED;
			}
			case STEP -> {
				int before = session.stepLevel();
				session.changeStep(dir);
				return before == session.stepLevel() ? Outcome.UNCHANGED : Outcome.CHANGED;
			}
			case MOVE_X, MOVE_Y, MOVE_Z -> {
				Axis axis = Axis.values()[property.ordinal() - EditProperty.MOVE_X.ordinal()];
				float distance = dir * StepTable.move(step);
				return transform(focus, members, group, settings, permissions, m -> DisplayTransform.move(m, axis, distance));
			}
			case ROTATE_YAW, ROTATE_PITCH, ROTATE_ROLL -> {
				Axis axis = property == EditProperty.ROTATE_YAW ? Axis.Y : property == EditProperty.ROTATE_PITCH ? Axis.X : Axis.Z;
				// Yaw is clockwise when seen from above, like player yaw.
				float degrees = (axis == Axis.Y ? -dir : dir) * StepTable.rotateDegrees(step);
				Vector3f pivot = pivot(focus, members, group);
				return transform(focus, members, group, settings, permissions, m -> DisplayTransform.rotate(m, axis, degrees, pivot));
			}
			case SCALE_UNIFORM, SCALE_X, SCALE_Y, SCALE_Z -> {
				Axis axis = property == EditProperty.SCALE_UNIFORM ? null : Axis.values()[property.ordinal() - EditProperty.SCALE_X.ordinal()];
				float amount = dir * StepTable.scale(step);
				if (group) {
					float factor = dir > 0 ? 1 + StepTable.scale(step) : 1 / (1 + StepTable.scale(step));
					Vector3f pivot = pivot(focus, members, true);
					return transform(focus, members, true, settings, permissions, m -> DisplayTransform.scaleWorld(m, axis, factor, pivot));
				}
				return transform(focus, members, false, settings, permissions,
						m -> DisplayTransform.scaleLocal(m, axis, amount, focus.kind().localCenter()));
			}
			case BLOCK_STATE_KEY -> {
				List<String> keys = editableStateKeys(focus, settings, permissions);
				if (keys.size() < 2) return Outcome.UNCHANGED;
				session.setStateKeyIndex(Math.floorMod(session.stateKeyIndex() + dir, keys.size()));
				return Outcome.CHANGED;
			}
			case BLOCK_STATE_VALUE -> {
				String key = selectedStateKey(focus, session, settings, permissions);
				if (key == null) return Outcome.UNCHANGED;
				List<String> values = focus.stateValues(key);
				if (values.size() < 2) return Outcome.UNCHANGED;
				int index = values.indexOf(focus.stateValue(key));
				// Some values may be rejected by the block; try the next ones in the same direction.
				for (int i = 1; i < values.size(); i++) {
					if (focus.setStateValue(key, values.get(Math.floorMod(index + dir * i, values.size())))) {
						return Outcome.CHANGED;
					}
				}
				return Outcome.UNCHANGED;
			}
			case ITEM_TRANSFORM -> {
				focus.setItemTransform(cycle(ITEM_TRANSFORMS, focus.itemTransform(), dir));
				return Outcome.CHANGED;
			}
			case BILLBOARD -> {
				String next = cycle(BILLBOARDS, focus.billboard(), dir);
				members.forEach(d -> d.setBillboard(next));
				return Outcome.CHANGED;
			}
			case SHADOW_RADIUS -> {
				float value = clamp(focus.shadowRadius() + dir * StepTable.fine(step), 0, 64);
				members.forEach(d -> d.setShadowRadius(value));
				return Outcome.CHANGED;
			}
			case SHADOW_STRENGTH -> {
				float value = clamp(focus.shadowStrength() + dir * StepTable.fine(step), 0, 1);
				members.forEach(d -> d.setShadowStrength(value));
				return Outcome.CHANGED;
			}
			case BRIGHTNESS_BLOCK, BRIGHTNESS_SKY -> {
				int[] current = focus.brightness();
				int[] next = current == null ? new int[] {-1, -1} : current.clone();
				int i = property == EditProperty.BRIGHTNESS_BLOCK ? 0 : 1;
				next[i] = Math.max(-1, Math.min(15, next[i] + dir));
				// Going below 0 clears the override; otherwise both channels are set and an unset one starts at 0.
				int[] value = next[i] < 0 ? null : new int[] {Math.max(0, next[0]), Math.max(0, next[1])};
				members.forEach(d -> d.setBrightness(value == null ? null : value.clone()));
				return Outcome.CHANGED;
			}
			case GLOW -> {
				boolean value = !focus.glowing();
				members.forEach(d -> d.setGlowing(value));
				return Outcome.CHANGED;
			}
			case GLOW_COLOR -> {
				int index = 0;
				for (int i = 0; i < GLOW_COLORS.length; i++) {
					if (GLOW_COLORS[i] == focus.glowColor()) index = i;
				}
				int value = GLOW_COLORS[Math.floorMod(index + dir, GLOW_COLORS.length)];
				members.forEach(d -> d.setGlowColor(value));
				return Outcome.CHANGED;
			}
			case VIEW_RANGE -> {
				float value = clamp(focus.viewRange() + dir * StepTable.fine(step), 0, 16);
				members.forEach(d -> d.setViewRange(value));
				return Outcome.CHANGED;
			}
			default -> {
				return Outcome.UNCHANGED;
			}
		}
	}

	/** Vanilla dye names for {@link #GLOW_COLORS} (index 0, the default colour, has none). */
	private static final String[] GLOW_COLOR_NAMES = {
			null, "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
			"light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};

	/** Action bar text for the selected property, e.g. "Scale X: 1.250  [step 1/8 · 5°]". */
	public static Message describe(EditableDisplay focus, EditProperty property, WrenchSession session, Settings settings,
			PermissionView permissions) {
		Matrix4f m = focus.transformation();
		Object value = switch (property) {
			case MODE -> session.groupMode() ? Message.of("mode.group", "whole group") : Message.of("mode.part", "single part");
			case STEP -> StepTable.label(session.stepLevel());
			case MOVE_X, MOVE_Y, MOVE_Z -> {
				Vector3f center = DisplayTransform.center(m, focus.kind());
				float v = property == EditProperty.MOVE_X ? center.x : property == EditProperty.MOVE_Y ? center.y : center.z;
				yield format(v);
			}
			case ROTATE_YAW, ROTATE_PITCH, ROTATE_ROLL -> {
				float[] euler = DisplayTransform.eulerDegrees(m);
				int i = property == EditProperty.ROTATE_YAW ? 0 : property == EditProperty.ROTATE_PITCH ? 1 : 2;
				yield Math.round(euler[i]) + "°";
			}
			case SCALE_UNIFORM -> {
				float[] s = DisplayTransform.scales(m);
				yield format((s[0] + s[1] + s[2]) / 3);
			}
			case SCALE_X, SCALE_Y, SCALE_Z -> format(DisplayTransform.scales(m)[property.ordinal() - EditProperty.SCALE_X.ordinal()]);
			case BLOCK_STATE_KEY -> {
				String key = selectedStateKey(focus, session, settings, permissions);
				yield key == null ? none() : Message.stateKey(key);
			}
			case BLOCK_STATE_VALUE -> {
				String key = selectedStateKey(focus, session, settings, permissions);
				yield key == null ? none() : Message.of("hud.state_value", "%s: %s", Message.stateKey(key), Message.stateValue(focus.stateValue(key)));
			}
			case ITEM_TRANSFORM -> Message.of("item_transform." + focus.itemTransform(), Message.humanize(focus.itemTransform()));
			case BILLBOARD -> Message.of("billboard." + focus.billboard(), focus.billboard());
			case SHADOW_RADIUS -> format(focus.shadowRadius());
			case SHADOW_STRENGTH -> format(focus.shadowStrength());
			case BRIGHTNESS_BLOCK, BRIGHTNESS_SKY -> {
				int[] b = focus.brightness();
				yield b == null ? Message.of("hud.world_light", "world light") : String.valueOf(b[property == EditProperty.BRIGHTNESS_BLOCK ? 0 : 1]);
			}
			case GLOW -> focus.glowing() ? Message.of("hud.on", "on") : Message.of("hud.off", "off");
			case GLOW_COLOR -> glowColorName(focus.glowColor());
			case VIEW_RANGE -> format(focus.viewRange());
			case REMOVE -> Message.of("hud.remove_hint", "right-click to remove");
		};
		if (property.isTransform() || property == EditProperty.SHADOW_RADIUS || property == EditProperty.SHADOW_STRENGTH
				|| property == EditProperty.VIEW_RANGE) {
			return Message.of("hud.value_step", "%s: %s  [step %s]", property.message(), value, StepTable.label(session.stepLevel()));
		}
		return Message.of("hud.value", "%s: %s", property.message(), value);
	}

	private static Message none() {
		return Message.of("hud.none", "(none)");
	}

	private static Object glowColorName(int rgb) {
		if (rgb < 0) return Message.of("hud.default", "default");
		for (int i = 1; i < GLOW_COLORS.length; i++) {
			if (GLOW_COLORS[i] == rgb) {
				return Message.vanilla("color.minecraft." + GLOW_COLOR_NAMES[i], Message.humanize(GLOW_COLOR_NAMES[i]));
			}
		}
		return String.format(Locale.ROOT, "#%06X", rgb);
	}

	/** Block-state keys the player may change on this display. */
	public static List<String> editableStateKeys(EditableDisplay display, Settings settings, PermissionView permissions) {
		List<String> keys = new ArrayList<>(display.stateKeys());
		if (!permissions.has(Permissions.FREE_BLOCK_STATES)) {
			keys.retainAll(settings.survival.allowedBlockStateKeys);
		}
		return keys;
	}

	private static String selectedStateKey(EditableDisplay display, WrenchSession session, Settings settings, PermissionView permissions) {
		List<String> keys = editableStateKeys(display, settings, permissions);
		return keys.isEmpty() ? null : keys.get(Math.floorMod(session.stateKeyIndex(), keys.size()));
	}

	private interface MatrixEdit {
		Matrix4f apply(Matrix4f m);
	}

	private static Outcome transform(EditableDisplay focus, List<EditableDisplay> members, boolean group, Settings settings,
			PermissionView permissions, MatrixEdit edit) {
		List<Matrix4f> before = new ArrayList<>();
		List<Matrix4f> after = new ArrayList<>();
		for (EditableDisplay member : members) {
			Matrix4f m = member.transformation();
			Matrix4f changed = edit.apply(new Matrix4f(m));
			if (changed == null) return Outcome.LIMIT;
			before.add(m);
			after.add(changed);
		}
		boolean allowed = group
				? Limits.allowGroup(members, before, after, settings, permissions)
				: Limits.allowPart(focus, before.get(0), after.get(0), settings, permissions);
		if (!allowed) return Outcome.LIMIT;
		for (int i = 0; i < members.size(); i++) {
			members.get(i).setTransformation(after.get(i));
		}
		return Outcome.CHANGED;
	}

	private static Vector3f pivot(EditableDisplay focus, List<EditableDisplay> members, boolean group) {
		if (!group) return DisplayTransform.center(focus.transformation(), focus.kind());
		List<Matrix4f> matrices = new ArrayList<>();
		members.forEach(d -> matrices.add(d.transformation()));
		return Limits.centroid(members, matrices);
	}

	private static String cycle(List<String> values, String current, int dir) {
		int index = values.indexOf(current);
		return values.get(Math.floorMod((index < 0 ? 0 : index) + dir, values.size()));
	}

	private static float clamp(float v, float min, float max) {
		return Math.max(min, Math.min(max, v));
	}

	private static String format(float v) {
		return String.format(Locale.ROOT, "%.3f", v);
	}
}
