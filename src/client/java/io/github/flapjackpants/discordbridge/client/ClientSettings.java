package io.github.flapjackpants.discordbridge.client;

import io.github.flapjackpants.discordbridge.config.ClientMediaConfig;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ClientSettings {
	private static final Logger LOG = LoggerFactory.getLogger("discordbridge");
	private static final List<Consumer<ClientMediaConfig>> LISTENERS = new CopyOnWriteArrayList<>();
	private static volatile ClientMediaConfig current = new ClientMediaConfig();

	private ClientSettings() {}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("discordbridge-client.json");
	}

	public static void init() {
		current = ClientMediaConfig.load(path());
	}

	/** Treat as read-only. */
	public static ClientMediaConfig get() {
		return current;
	}

	/** Publishes {@code next} (caller must not mutate it afterwards), saves it, then notifies listeners. */
	public static void apply(ClientMediaConfig next) {
		current = next;
		Path path = path();
		try {
			next.save(path);
		} catch (IOException e) {
			LOG.warn("Could not write {}: {}", path, e.getMessage());
		}
		for (Consumer<ClientMediaConfig> listener : LISTENERS) {
			try {
				listener.accept(next);
			} catch (RuntimeException e) {
				LOG.error("discordbridge settings listener failed", e);
			}
		}
	}

	public static void addListener(Consumer<ClientMediaConfig> listener) {
		LISTENERS.add(listener);
	}
}
