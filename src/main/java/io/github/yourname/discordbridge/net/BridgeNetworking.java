package io.github.yourname.discordbridge.net;

import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Server-side handshake state. Static so it survives bridge restarts. */
public final class BridgeNetworking {
	private static final Logger LOG = LoggerFactory.getLogger("discordbridge");
	private static final Map<UUID, Integer> VERSIONS = new ConcurrentHashMap<>();

	private BridgeNetworking() {}

	public static void registerServer() {
		ServerPlayNetworking.registerGlobalReceiver(HelloPayload.TYPE, (payload, ctx) -> {
			ServerPlayer player = ctx.player();
			OptionalInt v = BridgeProtocol.negotiate(payload.protocolVersion());
			if (v.isEmpty()) {
				VERSIONS.remove(player.getUUID());
				LOG.debug("{} sent unsupported discordbridge protocol {}", player.getName().getString(),
						payload.protocolVersion());
				return;
			}
			VERSIONS.put(player.getUUID(), v.getAsInt());
			LOG.debug("{} negotiated discordbridge protocol v{}", player.getName().getString(), v.getAsInt());
			if (ServerPlayNetworking.canSend(player, HelloPayload.TYPE)) {
				ctx.responseSender().sendPacket(new HelloPayload(v.getAsInt()));
			}
		});
	}

	public static boolean isRich(ServerPlayer p) {
		return VERSIONS.containsKey(p.getUUID()) && ServerPlayNetworking.canSend(p, RichMessagePayload.TYPE);
	}

	public static boolean anyRich() {
		return !VERSIONS.isEmpty();
	}

	public static void forget(UUID player) {
		VERSIONS.remove(player);
	}

	public static void clear() {
		VERSIONS.clear();
	}

	/** Server thread only. */
	public static void broadcast(MinecraftServer server, Component fallback, @Nullable RichMessage rich) {
		if (rich == null) {
			server.getPlayerList().broadcastSystemMessage(fallback, false);
			return;
		}
		server.getPlayerList().broadcastSystemMessage(fallback, p -> isRich(p) ? null : fallback, false);
		RichMessagePayload payload = new RichMessagePayload(fallback, rich);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (isRich(p)) {
				ServerPlayNetworking.send(p, payload);
			}
		}
	}
}
