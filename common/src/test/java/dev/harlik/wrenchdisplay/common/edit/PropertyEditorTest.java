package dev.harlik.wrenchdisplay.common.edit;

import dev.harlik.wrenchdisplay.common.PermissionView;
import dev.harlik.wrenchdisplay.common.Permissions;
import dev.harlik.wrenchdisplay.common.Settings;
import dev.harlik.wrenchdisplay.common.edit.PropertyEditor.Outcome;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PropertyEditorTest {
	private static final float EPS = 1e-4f;
	private final Settings settings = new Settings();
	private final PermissionView survival = PermissionView.DEFAULTS;

	/** {@link WrenchSession#changeStep} moves one level per click, like the wrench does. */
	private static WrenchSession atLevel(int level) {
		WrenchSession s = new WrenchSession();
		while (s.stepLevel() < level) s.changeStep(+1);
		while (s.stepLevel() > level) s.changeStep(-1);
		return s;
	}

	private static Outcome click(FakeDisplay d, EditProperty p, int dir, WrenchSession s, Settings settings, PermissionView perms) {
		return PropertyEditor.adjust(d, List.of(d), p, dir, s, settings, perms);
	}

	@Test
	void moveUsesStepAndStopsAtSurvivalOffset() {
		FakeDisplay d = new FakeDisplay(DisplayKind.BLOCK, new Matrix4f());
		WrenchSession s = atLevel(2); // 1/4
		for (int i = 0; i < 4; i++) {
			assertEquals(Outcome.CHANGED, click(d, EditProperty.MOVE_X, +1, s, settings, survival));
		}
		assertEquals(1.0f, d.transformation().m30(), EPS);
		assertEquals(Outcome.LIMIT, click(d, EditProperty.MOVE_X, +1, s, settings, survival));
		assertEquals(1.0f, d.transformation().m30(), EPS, "a refused edit changes nothing");
		assertEquals(Outcome.CHANGED, click(d, EditProperty.MOVE_X, -1, s, settings, survival), "moving back is fine");
		assertEquals(Outcome.CHANGED, click(d, EditProperty.MOVE_X, +1, s, settings, node -> node.equals(Permissions.BYPASS_OFFSET)));
	}

	@Test
	void valuesAlreadyOutOfRangeAreNotForcedBack() {
		// a 0.03-scale part from a blueprint
		FakeDisplay d = new FakeDisplay(DisplayKind.BLOCK, new Matrix4f().scale(0.03f));
		WrenchSession s = atLevel(0); // 1/16
		assertEquals(Outcome.CHANGED, click(d, EditProperty.SCALE_UNIFORM, +1, s, settings, survival), "growing towards the range is allowed");
		assertEquals(Outcome.CHANGED, click(d, EditProperty.MOVE_Y, +1, s, settings, survival), "unrelated edits still work");
		FakeDisplay big = new FakeDisplay(DisplayKind.BLOCK, new Matrix4f().scale(1.9f));
		s = atLevel(1); // 1/8
		assertEquals(Outcome.LIMIT, click(big, EditProperty.SCALE_X, +1, s, settings, survival));
	}

	@Test
	void rotationKeepsShearAndCentre() {
		// sheared matrix from a sample model (no clean rotation/scale decomposition exists)
		Matrix4f sheared = new Matrix4f(
				1f, 0f, 0f, 0f,
				0f, 0.9531448656f, 0.3025142397f, 0f,
				0f, -0.0008874326f, 0.0027609014f, 0f,
				-2.25f, 2.1119f, 2f, 1f);
		FakeDisplay d = new FakeDisplay(DisplayKind.ITEM, sheared);
		d.home = null;
		WrenchSession s = atLevel(3); // 45°
		Vector3f center = DisplayTransform.center(d.transformation(), d.kind());
		for (int i = 0; i < 8; i++) {
			click(d, EditProperty.ROTATE_YAW, +1, s, settings, PermissionView.ALL);
			assertTrue(center.distance(DisplayTransform.center(d.transformation(), d.kind())) < EPS, "rotation pivots on the centre");
		}
		assertTrue(d.transformation().equals(sheared, EPS), "a full turn restores the sheared matrix");
	}

	@Test
	void scaleLocalKeepsBlockCentre() {
		FakeDisplay d = new FakeDisplay(DisplayKind.BLOCK, new Matrix4f());
		WrenchSession s = new WrenchSession();
		click(d, EditProperty.SCALE_UNIFORM, +1, s, settings, survival);
		assertEquals(1.125f, DisplayTransform.scales(d.transformation())[0], EPS);
		assertTrue(new Vector3f(0.5f).distance(DisplayTransform.center(d.transformation(), d.kind())) < EPS);
	}

	@Test
	void groupRotationIsAllowedInSurvivalButDriftIsNot() {
		List<EditableDisplay> group = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			group.add(new FakeDisplay(DisplayKind.BLOCK, new Matrix4f().translation(i * 1.2f - 2, 0, 0).scale(0.3f)));
		}
		FakeDisplay focus = (FakeDisplay) group.get(0);
		WrenchSession s = atLevel(4); // 90° / 1 block
		assertEquals(Outcome.CHANGED, PropertyEditor.adjust(focus, group, EditProperty.ROTATE_YAW, +1, s, settings, survival));
		assertEquals(Outcome.CHANGED, PropertyEditor.adjust(focus, group, EditProperty.MOVE_Z, +1, s, settings, survival));
		assertEquals(Outcome.LIMIT, PropertyEditor.adjust(focus, group, EditProperty.MOVE_Z, +1, s, settings, survival));
	}

	@Test
	void groupScaleRespectsBlueprintSize() {
		List<EditableDisplay> group = List.of(
				new FakeDisplay(DisplayKind.BLOCK, new Matrix4f().translation(-2, 0, 0)),
				new FakeDisplay(DisplayKind.BLOCK, new Matrix4f().translation(1, 0, 0)));
		WrenchSession s = atLevel(4); // ×2 / ÷2
		assertEquals(Outcome.LIMIT, PropertyEditor.adjust((FakeDisplay) group.get(0), group, EditProperty.SCALE_UNIFORM, +1, s, settings, survival));
		assertEquals(Outcome.CHANGED, PropertyEditor.adjust((FakeDisplay) group.get(0), group, EditProperty.SCALE_UNIFORM, -1, s, settings, survival));
	}

	@Test
	void availablePropertiesFollowKindModeAndPermissions() {
		List<EditProperty> block = EditProperty.available(DisplayKind.BLOCK, false, false, survival);
		assertTrue(block.contains(EditProperty.BLOCK_STATE_VALUE));
		assertFalse(block.contains(EditProperty.GLOW), "visual properties need permission");
		assertFalse(block.contains(EditProperty.MODE), "mode only for groups");
		assertFalse(block.contains(EditProperty.ITEM_TRANSFORM));

		List<EditProperty> group = EditProperty.available(DisplayKind.ITEM, true, true, PermissionView.ALL);
		assertTrue(group.contains(EditProperty.MODE));
		assertTrue(group.contains(EditProperty.GLOW));
		assertFalse(group.contains(EditProperty.SCALE_X), "per-axis local scale is part-only");
		assertFalse(group.contains(EditProperty.ITEM_TRANSFORM));
	}

	@Test
	void sessionCyclingWraps() {
		List<EditProperty> available = List.of(EditProperty.STEP, EditProperty.MOVE_X, EditProperty.REMOVE);
		WrenchSession s = new WrenchSession();
		assertEquals(EditProperty.REMOVE, s.cycle(available, +1));
		assertEquals(EditProperty.STEP, s.cycle(available, +1));
		assertEquals(EditProperty.REMOVE, s.cycle(available, -1));
	}

	@Test
	void blockStatesOnlyOfferAllowedKeysInSurvival() {
		FakeDisplay d = new FakeDisplay(DisplayKind.BLOCK, new Matrix4f());
		d.stateOptions.put("powered", List.of("false", "true"));
		d.stateOptions.put("facing", List.of("north", "east", "south", "west"));
		d.state.put("powered", "false");
		d.state.put("facing", "north");
		WrenchSession s = new WrenchSession();
		assertEquals(List.of("facing"), PropertyEditor.editableStateKeys(d, settings, survival));
		click(d, EditProperty.BLOCK_STATE_VALUE, +1, s, settings, survival);
		assertEquals("east", d.state.get("facing"));
		click(d, EditProperty.BLOCK_STATE_VALUE, -1, s, settings, survival);
		click(d, EditProperty.BLOCK_STATE_VALUE, -1, s, settings, survival);
		assertEquals("west", d.state.get("facing"));
		assertEquals("false", d.state.get("powered"));
	}

	@Test
	void brightnessGoesFromWorldLightToOverrideAndBack() {
		FakeDisplay d = new FakeDisplay(DisplayKind.BLOCK, new Matrix4f());
		WrenchSession s = new WrenchSession();
		click(d, EditProperty.BRIGHTNESS_BLOCK, +1, s, settings, PermissionView.ALL);
		assertArrayEquals(new int[] {0, 0}, d.brightness);
		click(d, EditProperty.BRIGHTNESS_SKY, +1, s, settings, PermissionView.ALL);
		assertArrayEquals(new int[] {0, 1}, d.brightness);
		click(d, EditProperty.BRIGHTNESS_BLOCK, -1, s, settings, PermissionView.ALL);
		assertNull(d.brightness);
	}

	@Test
	void describeShowsValueAndStep() {
		FakeDisplay d = new FakeDisplay(DisplayKind.BLOCK, new Matrix4f().scale(2, 1, 1));
		WrenchSession s = new WrenchSession();
		assertEquals("Scale X: 2.000  [step 1/8 · 5°]", PropertyEditor.describe(d, EditProperty.SCALE_X, s, settings, survival).toFallbackString());
		assertEquals("Glow: off", PropertyEditor.describe(d, EditProperty.GLOW, s, settings, survival).toFallbackString());
	}
}
