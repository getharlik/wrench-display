package dev.harlik.wrenchdisplay.paper;

import dev.harlik.wrenchdisplay.common.PermissionView;
import org.bukkit.entity.Player;

/** The shared nodes are used as-is; their defaults are declared in paper-plugin.yml. */
final class PaperPermissions {
	private PaperPermissions() {
	}

	static PermissionView of(Player player) {
		return player::hasPermission;
	}
}
