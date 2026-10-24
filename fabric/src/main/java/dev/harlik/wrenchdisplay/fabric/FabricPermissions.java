package dev.harlik.wrenchdisplay.fabric;

import dev.harlik.wrenchdisplay.common.PermissionView;
import dev.harlik.wrenchdisplay.common.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
//? if >=26.1 {
import net.fabricmc.fabric.api.permission.v1.PermissionPredicates;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.PermissionLevel;
//?}

import java.util.function.Predicate;

/**
 * The shared permission nodes on top of the permission API permission mods (LuckPerms etc.) hook into: Fabric's own
 * API on 26.1+ (node {@code wrenchdisplay.bypass.offset} becomes {@code wrenchdisplay:bypass.offset}), the bundled
 * fabric-permissions-api on 1.21.x (nodes as they are). Without a permission mod, the use nodes are granted to
 * everyone and everything else to operators (level 2).
 */
public final class FabricPermissions {
	private static final int OP_LEVEL = 2;

	private FabricPermissions() {
	}

	public static PermissionView of(ServerPlayer player) {
		//? if >=26.1 {
		return node -> player.checkPermission(identifier(node),
				Permissions.grantedByDefault(node) ? PermissionLevel.ALL : PermissionLevel.GAMEMASTERS);
		//?} else {
		/*return node -> Permissions.grantedByDefault(node)
				? me.lucko.fabric.api.permissions.v0.Permissions.check(player, node, true)
				: me.lucko.fabric.api.permissions.v0.Permissions.check(player, node, OP_LEVEL);
		*///?}
	}

	/** For commands: the node, or operator level 2 without a permission mod. */
	public static Predicate<CommandSourceStack> require(String node) {
		//? if >=26.1 {
		return PermissionPredicates.require(identifier(node), PermissionLevel.GAMEMASTERS);
		//?} else {
		/*return me.lucko.fabric.api.permissions.v0.Permissions.require(node, OP_LEVEL);
		*///?}
	}

	//? if >=26.1 {
	private static final String PREFIX = "wrenchdisplay.";

	public static Identifier identifier(String node) {
		String path = node.startsWith(PREFIX) ? node.substring(PREFIX.length()) : node;
		return Identifier.fromNamespaceAndPath("wrenchdisplay", path);
	}
	//?}
}
