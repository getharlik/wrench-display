package dev.harlik.wrenchdisplay.common;

/** A player's permissions as seen by the core; each platform adapts its own permission system. */
@FunctionalInterface
public interface PermissionView {
	PermissionView ALL = node -> true;
	PermissionView DEFAULTS = Permissions::grantedByDefault;

	boolean has(String node);
}
