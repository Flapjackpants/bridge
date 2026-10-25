package io.github.yourname.discordbridge.net;

import java.util.OptionalInt;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class BridgeProtocol {
	public static final int VERSION = 1;
	public static final int MIN_VERSION = 1;

	private BridgeProtocol() {}

	/** Empty when the client is older than {@link #MIN_VERSION}; otherwise the highest shared version. */
	public static OptionalInt negotiate(int clientVersion) {
		return clientVersion < MIN_VERSION ? OptionalInt.empty() : OptionalInt.of(Math.min(clientVersion, VERSION));
	}

	/** Call exactly once, from {@code DiscordBridgeMod.onInitialize}. */
	public static void registerPayloadTypes() {
		PayloadTypeRegistry.serverboundPlay().register(HelloPayload.TYPE, HelloPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(HelloPayload.TYPE, HelloPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(RichMessagePayload.TYPE, RichMessagePayload.CODEC);
	}
}
