package dev.harlik.wrenchdisplay.common.edit;

import org.joml.Vector3f;

public enum DisplayKind {
	BLOCK(new Vector3f(0.5f, 0.5f, 0.5f)),
	ITEM(new Vector3f()),
	TEXT(new Vector3f(0, 0.15f, 0));

	private final Vector3f localCenter;

	DisplayKind(Vector3f localCenter) {
		this.localCenter = localCenter;
	}

	/** Visual centre of the untransformed model; block displays have their origin at the corner. */
	public Vector3f localCenter() {
		return new Vector3f(localCenter);
	}
}
