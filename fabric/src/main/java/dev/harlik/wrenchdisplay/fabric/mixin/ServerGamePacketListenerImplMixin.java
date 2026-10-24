package dev.harlik.wrenchdisplay.fabric.mixin;

import dev.harlik.wrenchdisplay.fabric.blueprint.BlueprintHandler;
import dev.harlik.wrenchdisplay.fabric.wrench.WrenchHandler;
//? if >=26.3 {
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
//?} else {
/*import net.minecraft.network.protocol.game.ServerboundSwingPacket;
*///?}
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Inputs vanilla clients send that Fabric API has no event for. Injected after the hop onto the server
 * thread, since the first call of each handler runs on the network thread.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
	@Shadow
	public ServerPlayer player;

	/** Sneak + scroll cycles the wrench property / rotates the blueprint instead of changing the hotbar slot. */
	@Inject(method = "handleSetCarriedItem", cancellable = true, at = @At(value = "INVOKE", shift = At.Shift.AFTER,
			target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V"))
	private void wrenchDisplay$onSetCarriedItem(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
		if (WrenchHandler.onHotbarScroll(player, packet.getSlot()) || BlueprintHandler.onHotbarScroll(player, packet.getSlot())) {
			ci.cancel();
		}
	}

	//? if >=26.3 {
	/** Left-click that hit nothing (no block, no entity): the wrench's "−" or "previous property" in the air. */
	@Inject(method = "handlePunch", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
			target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V"))
	private void wrenchDisplay$onPunch(ServerboundPunchPacket packet, CallbackInfo ci) {
		WrenchHandler.onLeftClickAir(player);
	}
	//?} else {
	/*// Before 26.3 a left-click into the air only shows up as an arm swing.
	@Inject(method = "handleAnimate", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
			target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V"))
	private void wrenchDisplay$onSwing(ServerboundSwingPacket packet, CallbackInfo ci) {
		WrenchHandler.onLeftClickAir(player);
	}
	*///?}
}
