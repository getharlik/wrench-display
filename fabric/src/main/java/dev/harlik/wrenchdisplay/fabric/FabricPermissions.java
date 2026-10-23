package dev.harlik.wrenchdisplay.fabric;

import dev.harlik.wrenchdisplay.common.PermissionView;
import dev.harlik.wrenchdisplay.common.Permissions;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;

/**
 * Maps the shared permission nodes onto Fabric's permission API, which permission mods (LuckPerms etc.) hook
 * into. Node {@code wrenchdisplay.bypass.offset} becomes {@code wrenchdisplay:bypass.offset}. Without a
 * permission mod, the use nodes are granted to everyone and everything else to operators (level 2).
 */
public final class FabricPermissions {
	private static final String PREFIX = "wrenchdisplay.";

	private FabricPermissions() {
	}

	public static PermissionView of(ServerPlayer player) {
		return node -> player.checkPermission(identifier(node),
				Permissions.grantedByDefault(node) ? PermissionLevel.ALL : PermissionLevel.GAMEMASTERS);
	}

	public static Identifier identifier(String node) {
		String path = node.startsWith(PREFIX) ? node.substring(PREFIX.length()) : node;
		return Identifier.fromNamespaceAndPath("wrenchdisplay", path);
	}
}
