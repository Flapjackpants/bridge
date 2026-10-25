package io.github.flapjackpants.discordbridge.client;

import io.github.flapjackpants.discordbridge.client.media.ChatMedia;
import io.github.flapjackpants.discordbridge.client.media.ClientMediaManager;
import io.github.flapjackpants.discordbridge.config.ClientMediaConfig;
import io.github.flapjackpants.discordbridge.net.BridgeProtocol;
import io.github.flapjackpants.discordbridge.net.HelloPayload;
import io.github.flapjackpants.discordbridge.net.RichMessagePayload;
import io.github.flapjackpants.discordbridge.text.ComponentRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
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
			ClientMediaConfig cfg = ClientSettings.get();
			Component shown = ComponentRenderer.withPrefix(payload.fallback(), cfg.prefixText(), cfg.prefixRgb());
			ChatMedia.register(shown, payload.message().media());
			ctx.client().gui.chatListener().handleSystemMessage(shown, false);
		});

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (ClientPlayNetworking.canSend(HelloPayload.TYPE)) {
				sender.sendPacket(new HelloPayload(BridgeProtocol.VERSION));
			}
		});
	}
}
