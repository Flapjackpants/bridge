package io.github.yourname.discordbridge.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import io.github.yourname.discordbridge.media.MediaDecoder;
import io.github.yourname.discordbridge.media.MediaPipeline;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ClientMediaConfig {
	private static final Logger LOG = LoggerFactory.getLogger("discordbridge");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public boolean enabled = true;
	public List<String> allowedHosts = new ArrayList<>(BridgeConfig.DEFAULT_MEDIA_HOSTS);
	public int maxDownloadBytes = 8 * 1024 * 1024; // clamp 64 KiB .. 32 MiB
	public int downloadThreads = 2;                // clamp 1..2
	public int maxDisplayWidth = 160;              // gui px, clamp 16..320
	public int maxDisplayHeight = 90;              // gui px, clamp 16..240
	public int decodeMaxSide = 256;                // clamp 16..512
	public int maxFrames = 64;                     // clamp 1..256
	public int memoryBudgetMiB = 96;               // clamp 8..1024
	public int diskCacheMiB = 256;                 // clamp 0..4096; 0 disables disk cache

	public static ClientMediaConfig load(Path file) {
		if (!Files.exists(file)) {
			ClientMediaConfig cfg = new ClientMediaConfig();
			cfg.trySave(file);
			return cfg;
		}
		try {
			ClientMediaConfig cfg = GSON.fromJson(Files.readString(file), ClientMediaConfig.class);
			if (cfg == null) {
				throw new JsonParseException("empty config file");
			}
			cfg.trySave(file); // so new keys appear in old files
			return cfg;
		} catch (IOException | JsonParseException e) {
			LOG.error("Could not read {} ({}); using defaults and leaving the file untouched", file, e.getMessage());
			return new ClientMediaConfig();
		}
	}

	public void save(Path file) throws IOException {
		Path parent = file.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		Files.writeString(tmp, GSON.toJson(this));
		try {
			Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private void trySave(Path file) {
		try {
			save(file);
		} catch (IOException e) {
			LOG.warn("Could not write {}: {}", file, e.getMessage());
		}
	}

	public int threads() {
		return Math.clamp(downloadThreads, 1, 2);
	}

	public int displayWidth() {
		return Math.clamp(maxDisplayWidth, 16, 320);
	}

	public int displayHeight() {
		return Math.clamp(maxDisplayHeight, 16, 240);
	}

	public long memoryBudgetBytes() {
		return Math.clamp(memoryBudgetMiB, 8, 1024) * 1024L * 1024L;
	}

	public long diskCacheBytes() {
		return Math.clamp(diskCacheMiB, 0, 4096) * 1024L * 1024L;
	}

	public MediaPipeline.Policy policy() {
		return new MediaPipeline.Policy(MediaPipeline.Policy.normalizeHosts(allowedHosts),
				Math.clamp(maxDownloadBytes, 64 * 1024, 32 * 1024 * 1024), 8192, 16, Math.clamp(decodeMaxSide, 16, 512));
	}

	public MediaDecoder.Limits limits() {
		return new MediaDecoder.Limits(Math.clamp(decodeMaxSide, 16, 512), Math.clamp(maxFrames, 1, 256),
				2048, 500, 4_194_304L, 4096);
	}
}
