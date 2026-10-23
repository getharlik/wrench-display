package dev.harlik.wrenchdisplay.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.harlik.wrenchdisplay.common.Permissions;
import net.fabricmc.fabric.api.permission.v1.PermissionPredicates;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.function.Supplier;

/** {@code /wrenchdisplay give <players> wrench|blueprint} and {@code /wrenchdisplay reload}. */
final class WrenchCommand {
	private WrenchCommand() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("wrenchdisplay")
				.requires(PermissionPredicates.require(FabricPermissions.identifier(Permissions.ADMIN), PermissionLevel.GAMEMASTERS))
				.then(Commands.literal("give")
						.then(Commands.argument("targets", EntityArgument.players())
								.then(Commands.literal(ModItems.WRENCH).executes(ctx -> give(ctx, ModItems::wrench)))
								.then(Commands.literal(ModItems.BLUEPRINT).executes(ctx -> give(ctx, ModItems::blueprint)))))
				.then(Commands.literal("reload").executes(ctx -> {
					boolean ok = WrenchDisplay.reloadSettings();
					if (ok) {
						ctx.getSource().sendSuccess(() -> Component.literal("Wrench Display config reloaded"), true);
					} else {
						ctx.getSource().sendFailure(Component.literal("Could not read the config; see the server log"));
					}
					return ok ? 1 : 0;
				})));
	}

	private static int give(CommandContext<CommandSourceStack> ctx, Supplier<ItemStack> item) throws CommandSyntaxException {
		Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
		for (ServerPlayer player : targets) {
			ItemStack stack = item.get();
			if (!player.getInventory().add(stack)) {
				player.drop(stack, false, Prediction.SERVER_ONLY);
			}
		}
		ctx.getSource().sendSuccess(() -> Component.literal("Gave " + targets.size() + " player(s) a " + item.get().getHoverName().getString()), true);
		return targets.size();
	}
}
