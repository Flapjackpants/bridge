package io.github.yourname.discordbridge.client;

import io.github.yourname.discordbridge.client.media.ChatMedia;
import io.github.yourname.discordbridge.client.media.ClientMediaManager;
import io.github.yourname.discordbridge.net.BridgeProtocol;
import io.github.yourname.discordbridge.net.HelloPayload;
import io.github.yourname.discordbridge.net.RichMessagePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DiscordBridgeClient implements ClientModInitializer {
	private static final Logger LOG = LoggerFactory.getLogger("discordbridge");

	@Override
	public void onInitializeClient() {
		ClientSettings.init();
		ClientMediaManager.init();
		ClientSettings.addListener(c -> Minecraft.getInstance().execute(
				() -> Minecraft.getInstance().gui.hud.getChat().rescaleChat()));

		ClientPlayNetworking.registerGlobalReceiver(HelloPayload.TYPE, (payload, ctx) ->
				LOG.debug("discordbridge server protocol v{}", payload.protocolVersion()));

		ClientPlayNetworking.registerGlobalReceiver(RichMessagePayload.TYPE, (payload, ctx) -> {
			ChatMedia.register(payload.fallback(), payload.message().media());
			ctx.client().gui.chatListener().handleSystemMessage(payload.fallback(), false);
		});

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (ClientPlayNetworking.canSend(HelloPayload.TYPE)) {
				sender.sendPacket(new HelloPayload(BridgeProtocol.VERSION));
			}
		});
	}
}
