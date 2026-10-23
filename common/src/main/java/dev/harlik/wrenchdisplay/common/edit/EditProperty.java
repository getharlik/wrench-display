package dev.harlik.wrenchdisplay.common.edit;

import dev.harlik.wrenchdisplay.common.PermissionView;
import dev.harlik.wrenchdisplay.common.Permissions;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Everything the wrench can change, in cycling order. */
public enum EditProperty {
	MODE("Mode", Scope.GROUP_ONLY, null, EnumSet.allOf(DisplayKind.class)),
	STEP("Step", Scope.BOTH, null, EnumSet.allOf(DisplayKind.class)),
	MOVE_X("Move X", Scope.BOTH, null, EnumSet.allOf(DisplayKind.class)),
	MOVE_Y("Move Y", Scope.BOTH, null, EnumSet.allOf(DisplayKind.class)),
	MOVE_Z("Move Z", Scope.BOTH, null, EnumSet.allOf(DisplayKind.class)),
	ROTATE_YAW("Rotate yaw", Scope.BOTH, null, EnumSet.allOf(DisplayKind.class)),
	ROTATE_PITCH("Rotate pitch", Scope.BOTH, null, EnumSet.allOf(DisplayKind.class)),
	ROTATE_ROLL("Rotate roll", Scope.BOTH, null, EnumSet.allOf(DisplayKind.class)),
	SCALE_UNIFORM("Scale", Scope.BOTH, null, EnumSet.allOf(DisplayKind.class)),
	SCALE_X("Scale X", Scope.PART_ONLY, null, EnumSet.allOf(DisplayKind.class)),
	SCALE_Y("Scale Y", Scope.PART_ONLY, null, EnumSet.allOf(DisplayKind.class)),
	SCALE_Z("Scale Z", Scope.PART_ONLY, null, EnumSet.allOf(DisplayKind.class)),
	BLOCK_STATE_KEY("Block state", Scope.PART_ONLY, null, EnumSet.of(DisplayKind.BLOCK)),
	BLOCK_STATE_VALUE("State value", Scope.PART_ONLY, null, EnumSet.of(DisplayKind.BLOCK)),
	ITEM_TRANSFORM("Item pose", Scope.PART_ONLY, null, EnumSet.of(DisplayKind.ITEM)),
	BILLBOARD("Billboard", Scope.BOTH, null, EnumSet.allOf(DisplayKind.class)),
	SHADOW_RADIUS("Shadow radius", Scope.BOTH, null, EnumSet.allOf(DisplayKind.class)),
	SHADOW_STRENGTH("Shadow strength", Scope.BOTH, null, EnumSet.allOf(DisplayKind.class)),
	BRIGHTNESS_BLOCK("Block light", Scope.BOTH, Permissions.VISUAL, EnumSet.allOf(DisplayKind.class)),
	BRIGHTNESS_SKY("Sky light", Scope.BOTH, Permissions.VISUAL, EnumSet.allOf(DisplayKind.class)),
	GLOW("Glow", Scope.BOTH, Permissions.VISUAL, EnumSet.of(DisplayKind.BLOCK, DisplayKind.ITEM)),
	GLOW_COLOR("Glow colour", Scope.BOTH, Permissions.VISUAL, EnumSet.of(DisplayKind.BLOCK, DisplayKind.ITEM)),
	VIEW_RANGE("View range", Scope.BOTH, Permissions.VISUAL, EnumSet.allOf(DisplayKind.class)),
	REMOVE("Remove", Scope.BOTH, null, EnumSet.allOf(DisplayKind.class));

	public enum Scope {
		/** Only offered while editing a group as a whole. */
		GROUP_ONLY,
		/** Only offered while editing a single display. */
		PART_ONLY,
		BOTH
	}

	private final String label;
	private final Scope scope;
	private final String permission;
	private final Set<DisplayKind> kinds;

	EditProperty(String label, Scope scope, String permission, Set<DisplayKind> kinds) {
		this.label = label;
		this.scope = scope;
		this.permission = permission;
		this.kinds = kinds;
	}

	public String label() {
		return label;
	}

	/** Null when everyone may use it. */
	public String permission() {
		return permission;
	}

	public boolean isTransform() {
		return ordinal() >= MOVE_X.ordinal() && ordinal() <= SCALE_Z.ordinal();
	}

	/**
	 * The properties offered for a target.
	 *
	 * @param inGroup     the target belongs to a group (MODE is only useful then)
	 * @param groupMode   the player edits the whole group rather than one part
	 */
	public static List<EditProperty> available(DisplayKind kind, boolean inGroup, boolean groupMode, PermissionView permissions) {
		List<EditProperty> out = new ArrayList<>();
		for (EditProperty property : values()) {
			if (property == MODE) {
				if (inGroup) out.add(property);
				continue;
			}
			if (!property.kinds.contains(kind)) continue;
			if (groupMode && property.scope == Scope.PART_ONLY) continue;
			if (!groupMode && property.scope == Scope.GROUP_ONLY) continue;
			if (property.permission != null && !permissions.has(property.permission)) continue;
			out.add(property);
		}
		return out;
	}
}
