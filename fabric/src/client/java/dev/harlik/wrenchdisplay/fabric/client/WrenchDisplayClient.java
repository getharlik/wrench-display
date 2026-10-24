package dev.harlik.wrenchdisplay.fabric.client;

import dev.harlik.wrenchdisplay.fabric.net.BlueprintPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class WrenchDisplayClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Registering the receiver also tells the server this client has the mod, so it opens this screen
		// instead of the vanilla dialog.
		ClientPlayNetworking.registerGlobalReceiver(BlueprintPayloads.OpenPaste.TYPE, (payload, context) -> {
			//? if >=26.2 {
			context.client().gui.setScreen(new BlueprintScreen(payload.maxLength()));
			//?} else {
			/*context.client().setScreen(new BlueprintScreen(payload.maxLength()));
			*///?}
		});
	}
}
