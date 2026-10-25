package io.github.yourname.discordbridge;

import io.github.yourname.discordbridge.command.BridgeCommand;
import io.github.yourname.discordbridge.config.BridgeConfig;
import io.github.yourname.discordbridge.discord.DiscordBridge;
import io.github.yourname.discordbridge.net.BridgeNetworking;
import io.github.yourname.discordbridge.net.BridgeProtocol;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DiscordBridgeMod implements ModInitializer {
	public static final String MOD_ID = "discordbridge";
	private static final Logger LOG = LoggerFactory.getLogger(MOD_ID);
	private static final Object LOCK = new Object();

	private static volatile BridgeConfig config = new BridgeConfig();
	private static volatile @Nullable DiscordBridge bridge;
	private static volatile @Nullable MinecraftServer server;
	private static volatile @Nullable ExecutorService lifecycle;

	public static Path configPath() {
		return FabricLoader.getInstance().getConfigDir().resolve("discordbridge-server.json");
	}

	public static BridgeConfig config() {
		return config;
	}

	public static @Nullable DiscordBridge bridge() {
		return bridge;
	}

	/** Stops the old bridge (5s), reloads the config from disk, and starts a new bridge. */
	public static CompletableFuture<Void> restart() {
		ExecutorService ex = lifecycle;
		if (ex == null) {
			return CompletableFuture.failedFuture(new IllegalStateException("server not running"));
		}
		try {
			return CompletableFuture.runAsync(() -> {
				synchronized (LOCK) {
					DiscordBridge old = bridge;
					bridge = null;
					if (old != null) {
						old.stop(Duration.ofSeconds(5));
					}
					config = BridgeConfig.load(configPath());
					startBridge();
				}
			}, ex);
		} catch (RuntimeException e) {
			return CompletableFuture.failedFuture(e);
		}
	}

	private static void startBridge() {
		MinecraftServer s = server;
		if (s == null || !(s.isDedicatedServer() || config.enableInSingleplayer)) {
			return;
		}
		DiscordBridge b = new DiscordBridge(s, config);
		bridge = b;
		b.start();
	}

	@Override
	public void onInitialize() {
		config = BridgeConfig.load(configPath());
		BridgeProtocol.registerPayloadTypes();
		BridgeNetworking.registerServer();

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				BridgeCommand.register(dispatcher));

		ServerLifecycleEvents.SERVER_STARTED.register(s -> {
			synchronized (LOCK) {
				server = s;
				lifecycle = Executors.newSingleThreadExecutor(r -> {
					Thread t = new Thread(r, "discordbridge-lifecycle");
					t.setDaemon(true);
					return t;
				});
				startBridge();
			}
			DiscordBridge b = bridge;
			if (b != null && config.relayServerStatus) {
				b.sendEvent("✅ Server started");
			}
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
			synchronized (LOCK) {
				BridgeNetworking.clear();
				DiscordBridge b = bridge;
				bridge = null;
				if (b != null) {
					if (config.relayServerStatus) {
						b.sendEvent("🛑 Server stopping");
					}
					b.stop(Duration.ofSeconds(5));
				}
				ExecutorService ex = lifecycle;
				lifecycle = null;
				if (ex != null) {
					ex.shutdown();
				}
				server = null;
			}
		});

		ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
			DiscordBridge b = bridge;
			if (b != null) {
				b.sendChat(sender, message.signedContent());
			}
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> {
			DiscordBridge b = bridge;
			if (b != null && config.relayJoinLeave) {
				b.sendEvent("➕ " + handler.getPlayer().getName().getString() + " joined");
			}
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> {
			BridgeNetworking.forget(handler.getPlayer().getUUID());
			DiscordBridge b = bridge;
			if (b != null && config.relayJoinLeave) {
				b.sendEvent("➖ " + handler.getPlayer().getName().getString() + " left");
			}
		});

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			DiscordBridge b = bridge;
			if (b != null && config.relayDeaths && entity instanceof ServerPlayer p) {
				b.sendEvent("💀 " + source.getLocalizedDeathMessage(p).getString());
			}
		});

		LOG.info("Discord Bridge initialized");
	}
}
