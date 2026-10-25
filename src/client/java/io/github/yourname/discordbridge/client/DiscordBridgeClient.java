package io.github.yourname.discordbridge.client;

import io.github.yourname.discordbridge.net.BridgeProtocol;
import io.github.yourname.discordbridge.net.HelloPayload;
import io.github.yourname.discordbridge.net.RichMessagePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DiscordBridgeClient implements ClientModInitializer {
	private static final Logger LOG = LoggerFactory.getLogger("discordbridge");

	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(HelloPayload.TYPE, (payload, ctx) ->
				LOG.debug("discordbridge server protocol v{}", payload.protocolVersion()));

		// Spec 3 replaces this with rich rendering.
		ClientPlayNetworking.registerGlobalReceiver(RichMessagePayload.TYPE, (payload, ctx) ->
				ctx.client().gui.chatListener().handleSystemMessage(payload.fallback(), false));

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (ClientPlayNetworking.canSend(HelloPayload.TYPE)) {
				sender.sendPacket(new HelloPayload(BridgeProtocol.VERSION));
			}
		});
	}
}
