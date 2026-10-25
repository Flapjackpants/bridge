package io.github.yourname.discordbridge.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import io.github.yourname.discordbridge.media.MediaDecoder;
import io.github.yourname.discordbridge.media.MediaFetchPlan;
import io.github.yourname.discordbridge.media.MediaRef;
import io.github.yourname.discordbridge.text.TextSanitizer;
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

	public MediaMode mode = MediaMode.ANIMATED;    // OFF: no fetching, text fallback only
	public boolean clickToReveal = false;          // media hidden until the player clicks it
	public boolean showInChatOverlay = true;       // render media in the HUD chat, not only in the open chat screen
	public static final String DEFAULT_DISCORD_PREFIX = "[Discord]";
	public static final String DEFAULT_DISCORD_COLOR = "#5865F2";
	public String discordPrefix = DEFAULT_DISCORD_PREFIX; // shown before Discord messages; blank hides it
	public String discordColor = DEFAULT_DISCORD_COLOR;   // #RRGGBB; invalid falls back to default
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
		return memoryBudgetMiB() * 1024L * 1024L;
	}

	public long diskCacheBytes() {
		return Math.clamp(diskCacheMiB, 0, 4096) * 1024L * 1024L;
	}

	public MediaPipeline.Policy policy() {
		return new MediaPipeline.Policy(MediaPipeline.Policy.normalizeHosts(allowedHosts),
				Math.clamp(maxDownloadBytes, 64 * 1024, 32 * 1024 * 1024), 8192, 16, Math.clamp(decodeMaxSide, 16, 512));
	}

	public MediaDecoder.Limits limits() {
		return new MediaDecoder.Limits(Math.clamp(decodeMaxSide, 16, 512),
				mode() == MediaMode.STATIC ? 1 : Math.clamp(maxFrames, 1, 256), 2048, 500, 4_194_304L, 4096);
	}

	/** The prefix to show, with a trailing space; empty when the player blanked it. */
	public String prefixText() {
		String s = discordPrefix == null ? DEFAULT_DISCORD_PREFIX
				: TextSanitizer.stripLegacyFormatting(discordPrefix).strip();
		if (s.length() > 32) {
			int end = Character.isHighSurrogate(s.charAt(31)) ? 31 : 32;
			s = s.substring(0, end);
		}
		return s.isEmpty() ? "" : s + " ";
	}

	public int prefixRgb() {
		return BridgeConfig.parseHexColor(discordColor, 0x5865F2);
	}

	public MediaMode mode() {
		return mode == null ? MediaMode.ANIMATED : mode;
	}

	public int memoryBudgetMiB() {
		return Math.clamp(memoryBudgetMiB, 8, 1024);
	}

	public ClientMediaConfig copy() {
		return GSON.fromJson(GSON.toJson(this), ClientMediaConfig.class);
	}

	/** OFF: none; STATIC: non-animated sources first; ANIMATED: plan as is. Never throws; unmodifiable. */
	public List<MediaFetchPlan.Source> sources(MediaRef ref) {
		try {
			MediaMode m = mode();
			if (m == MediaMode.OFF) {
				return List.of();
			}
			List<MediaFetchPlan.Source> plan = MediaFetchPlan.plan(ref, policy());
			if (m == MediaMode.ANIMATED) {
				return plan;
			}
			List<MediaFetchPlan.Source> still = new ArrayList<>();
			List<MediaFetchPlan.Source> moving = new ArrayList<>();
			for (MediaFetchPlan.Source s : plan) {
				(s.animated() ? moving : still).add(s);
			}
			still.addAll(moving);
			return List.copyOf(still);
		} catch (RuntimeException e) {
			return List.of();
		}
	}
}
