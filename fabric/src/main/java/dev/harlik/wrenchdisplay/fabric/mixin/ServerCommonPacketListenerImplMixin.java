package dev.harlik.wrenchdisplay.fabric.mixin;

import dev.harlik.wrenchdisplay.fabric.WrenchDisplay;
import dev.harlik.wrenchdisplay.fabric.blueprint.BlueprintHandler;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dialog buttons with a custom action (the blueprint paste window) arrive here; there is no Fabric event for them. */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerImplMixin {
	@Inject(method = "handleCustomClickAction", cancellable = true, at = @At(value = "INVOKE", shift = At.Shift.AFTER,
			target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V"))
	private void wrenchDisplay$onCustomClick(ServerboundCustomClickActionPacket packet, CallbackInfo ci) {
		if ((Object) this instanceof ServerGamePacketListenerImpl game && packet.id().getNamespace().equals(WrenchDisplay.MOD_ID)) {
			BlueprintHandler.onCustomClick(game.player, packet.id(), packet.payload());
			ci.cancel();
		}
	}
}
