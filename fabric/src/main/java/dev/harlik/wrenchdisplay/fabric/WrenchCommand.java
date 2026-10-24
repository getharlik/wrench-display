package dev.harlik.wrenchdisplay.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.harlik.wrenchdisplay.common.Permissions;
import dev.harlik.wrenchdisplay.fabric.compat.Compat;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.function.Supplier;

/** {@code /wrenchdisplay give <players> wrench|blueprint} and {@code /wrenchdisplay reload}. */
final class WrenchCommand {
	private WrenchCommand() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("wrenchdisplay")
				.requires(FabricPermissions.require(Permissions.ADMIN))
				.then(Commands.literal("give")
						.then(Commands.argument("targets", EntityArgument.players())
								.then(Commands.literal(ModItems.WRENCH).executes(ctx -> give(ctx, ModItems::wrench)))
								.then(Commands.literal(ModItems.BLUEPRINT).executes(ctx -> give(ctx, ModItems::blueprint)))))
				.then(Commands.literal("reload").executes(ctx -> {
					boolean ok = WrenchDisplay.reloadSettings();
					if (ok) {
						ctx.getSource().sendSuccess(() -> Lang.tr("command.reloaded", "Wrench Display config reloaded"), true);
					} else {
						ctx.getSource().sendFailure(Lang.tr("command.reload_failed", "Could not read the config; see the server log"));
					}
					return ok ? 1 : 0;
				})));
	}

	private static int give(CommandContext<CommandSourceStack> ctx, Supplier<ItemStack> item) throws CommandSyntaxException {
		Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
		for (ServerPlayer player : targets) {
			ItemStack stack = item.get();
			Compat.giveOrDrop(player, stack);
		}
		ctx.getSource().sendSuccess(() -> Lang.tr("command.gave", "Gave %s to %s player(s)", item.get().getHoverName(), targets.size()), true);
		return targets.size();
	}
}
