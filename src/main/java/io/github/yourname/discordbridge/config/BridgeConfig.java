package io.github.yourname.discordbridge.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BridgeConfig {
	private static final Logger LOG = LoggerFactory.getLogger("discordbridge");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Pattern HEX = Pattern.compile("#?[0-9a-fA-F]{6}");
	public static final List<String> DEFAULT_MEDIA_HOSTS = List.of(
			"cdn.discordapp.com", "media.discordapp.net", "*.discordapp.net", "media.tenor.com");
	private static final List<String> SETTABLE_KEYS = List.of(
			"discordPrefix", "prefixColor", "nameColor", "messageColor",
			"relayJoinLeave", "relayDeaths", "relayServerStatus", "useWebhook",
			"enableInSingleplayer", "maxInboundLength", "maxInboundLines", "richMedia",
			"avatarUrlTemplate", "channelId");

	public String token = "";                 // overridden by env DISCORDBRIDGE_TOKEN when non-blank
	public String channelId = "";             // numeric snowflake as string
	public boolean enableInSingleplayer = false;
	public boolean useWebhook = true;
	public String avatarUrlTemplate = "https://mc-heads.net/avatar/{uuid}/64"; // {uuid} and {name} placeholders
	public String discordPrefix = "[D] ";
	public String prefixColor = "#5865F2";
	public String nameColor = "#7289DA";
	public String messageColor = "#FFFFFF";
	public boolean relayJoinLeave = true;
	public boolean relayDeaths = true;
	public boolean relayServerStatus = true;
	public int maxInboundLength = 512;        // chars, after sanitizing
	public int maxInboundLines = 5;
	public boolean richMedia = true;           // send discordbridge:message to clients that completed the hello handshake
	public List<String> mediaAllowedHosts = new ArrayList<>(DEFAULT_MEDIA_HOSTS);
	public int mediaMaxBytes = 8 * 1024 * 1024; // bytes; larger attachments are not sent as media
	public int mediaMaxDimension = 8192;       // px per side
	public int mediaMaxItems = 4;              // per message, hard cap 16
	public int mediaPreviewSize = 256;         // px, longest side of proxy resize

	public static BridgeConfig load(Path file) {
		if (!Files.exists(file)) {
			BridgeConfig cfg = new BridgeConfig();
			cfg.trySave(file);
			return cfg;
		}
		try {
			BridgeConfig cfg = GSON.fromJson(Files.readString(file), BridgeConfig.class);
			if (cfg == null) {
				throw new JsonParseException("empty config file");
			}
			cfg.trySave(file); // so new keys appear in old files
			return cfg;
		} catch (IOException | JsonParseException e) {
			LOG.error("Could not read {} ({}); using defaults and leaving the file untouched", file, e.getMessage());
			return new BridgeConfig();
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
		} catch (java.nio.file.AtomicMoveNotSupportedException e) {
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

	public String effectiveToken() {
		String env = System.getenv("DISCORDBRIDGE_TOKEN");
		if (env != null && !env.isBlank()) {
			return env.trim();
		}
		return token == null ? "" : token.trim();
	}

	public OptionalLong channelIdLong() {
		if (channelId == null) {
			return OptionalLong.empty();
		}
		try {
			long id = Long.parseLong(channelId.trim());
			return id > 0 ? OptionalLong.of(id) : OptionalLong.empty();
		} catch (NumberFormatException e) {
			return OptionalLong.empty();
		}
	}

	public List<String> validate() {
		List<String> problems = new ArrayList<>();
		if (effectiveToken().isEmpty()) {
			problems.add("No bot token set (config \"token\" or env DISCORDBRIDGE_TOKEN)");
		}
		if (channelIdLong().isEmpty()) {
			problems.add("channelId is missing or not numeric");
		}
		return problems;
	}

	/** Accepts "#RRGGBB" or "RRGGBB"; otherwise returns the fallback. */
	public static int parseHexColor(String s, int fallbackRgb) {
		if (s == null) {
			return fallbackRgb;
		}
		String t = s.trim();
		if (!HEX.matcher(t).matches()) {
			return fallbackRgb;
		}
		return Integer.parseInt(t.startsWith("#") ? t.substring(1) : t, 16);
	}

	public boolean setByKey(String key, String value) {
		if (key == null || value == null || !SETTABLE_KEYS.contains(key)) {
			return false;
		}
		switch (key) {
			case "discordPrefix" -> discordPrefix = value;
			case "avatarUrlTemplate" -> avatarUrlTemplate = value;
			case "prefixColor", "nameColor", "messageColor" -> {
				if (parseHexColor(value, -1) < 0) {
					return false;
				}
				String norm = String.format("#%06X", parseHexColor(value, 0));
				switch (key) {
					case "prefixColor" -> prefixColor = norm;
					case "nameColor" -> nameColor = norm;
					default -> messageColor = norm;
				}
			}
			case "relayJoinLeave", "relayDeaths", "relayServerStatus", "useWebhook", "enableInSingleplayer",
					"richMedia" -> {
				Boolean b = parseBool(value);
				if (b == null) {
					return false;
				}
				switch (key) {
					case "relayJoinLeave" -> relayJoinLeave = b;
					case "relayDeaths" -> relayDeaths = b;
					case "relayServerStatus" -> relayServerStatus = b;
					case "useWebhook" -> useWebhook = b;
					case "enableInSingleplayer" -> enableInSingleplayer = b;
					default -> richMedia = b;
				}
			}
			case "maxInboundLength", "maxInboundLines" -> {
				int n;
				try {
					n = Integer.parseInt(value.trim());
				} catch (NumberFormatException e) {
					return false;
				}
				if (n < 1 || n > 4000) {
					return false;
				}
				if (key.equals("maxInboundLength")) {
					maxInboundLength = n;
				} else {
					maxInboundLines = n;
				}
			}
			case "channelId" -> {
				BridgeConfig probe = new BridgeConfig();
				probe.channelId = value;
				if (probe.channelIdLong().isEmpty()) {
					return false;
				}
				channelId = value.trim();
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	private static Boolean parseBool(String v) {
		String t = v.trim().toLowerCase();
		if (t.equals("true")) {
			return Boolean.TRUE;
		}
		if (t.equals("false")) {
			return Boolean.FALSE;
		}
		return null;
	}

	public static List<String> settableKeys() {
		return SETTABLE_KEYS;
	}
}
