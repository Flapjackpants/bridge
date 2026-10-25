package io.github.flapjackpants.discordbridge.config;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BridgeConfigTest {
	@TempDir Path dir;

	@Test
	void createsDefault() throws Exception {
		Path f = dir.resolve("sub/cfg.json");
		BridgeConfig c = BridgeConfig.load(f);
		assertTrue(Files.exists(f));
		assertEquals("[Discord] ", c.discordPrefix);
	}

	@Test
	void malformedNotOverwritten() throws Exception {
		Path f = dir.resolve("cfg.json");
		Files.writeString(f, "{ not json");
		BridgeConfig c = BridgeConfig.load(f);
		assertEquals("#5865F2", c.prefixColor);
		assertEquals("{ not json", Files.readString(f));
	}

	@Test
	void fillsNewKeys() throws Exception {
		Path f = dir.resolve("cfg.json");
		Files.writeString(f, "{\"channelId\":\"123\"}");
		BridgeConfig c = BridgeConfig.load(f);
		assertEquals("123", c.channelId);
		assertEquals(512, c.maxInboundLength);
		String text = Files.readString(f);
		assertTrue(text.contains("maxInboundLength"));
		assertTrue(text.contains("discordPrefix"));
	}

	@Test
	void saveRoundTrips() throws Exception {
		Path f = dir.resolve("cfg.json");
		BridgeConfig c = new BridgeConfig();
		c.discordPrefix = "<D> ";
		c.save(f);
		assertEquals("<D> ", BridgeConfig.load(f).discordPrefix);
		assertFalse(Files.exists(dir.resolve("cfg.json.tmp")));
	}

	@Test
	void hexParsing() {
		assertEquals(1, BridgeConfig.parseHexColor("#zzz", 1));
		assertEquals(0x5865F2, BridgeConfig.parseHexColor("5865f2", 1));
		assertEquals(0x5865F2, BridgeConfig.parseHexColor("#5865F2", 1));
		assertEquals(7, BridgeConfig.parseHexColor(null, 7));
	}

	@Test
	void setByKeyAccepts() {
		BridgeConfig c = new BridgeConfig();
		assertTrue(c.setByKey("prefixColor", "#FF0000"));
		assertEquals("#FF0000", c.prefixColor);
		assertTrue(c.setByKey("maxInboundLines", "3"));
		assertEquals(3, c.maxInboundLines);
		assertTrue(c.setByKey("relayDeaths", "false"));
		assertFalse(c.relayDeaths);
		assertTrue(c.setByKey("channelId", "123"));
	}

	@Test
	void setByKeyRejects() {
		BridgeConfig c = new BridgeConfig();
		assertFalse(c.setByKey("token", "abc"));
		assertFalse(c.setByKey("nope", "x"));
		assertFalse(c.setByKey("prefixColor", "#zzz"));
		assertFalse(c.setByKey("maxInboundLength", "0"));
		assertFalse(c.setByKey("maxInboundLength", "4001"));
		assertFalse(c.setByKey("maxInboundLength", "abc"));
		assertFalse(c.setByKey("useWebhook", "maybe"));
		assertFalse(BridgeConfig.settableKeys().contains("token"));
	}

	@Test
	void validate() {
		BridgeConfig c = new BridgeConfig();
		c.token = "";
		c.channelId = "abc";
		var problems = c.validate();
		assertEquals(2, problems.size());
		assertTrue(problems.stream().noneMatch(p -> p.contains("abc") && p.toLowerCase().contains("token abc")));
		c.channelId = "123";
		c.token = "secret-token";
		assertTrue(c.validate().isEmpty());
		c.token = "";
		assertTrue(c.validate().stream().noneMatch(p -> p.contains("secret-token")));
	}

	@Test
	void channelIdParsing() {
		BridgeConfig c = new BridgeConfig();
		c.channelId = "-5";
		assertTrue(c.channelIdLong().isEmpty());
		c.channelId = " 42 ";
		assertEquals(42L, c.channelIdLong().getAsLong());
	}

	@Test
	void fillsMediaKeys() throws Exception {
		Path f = dir.resolve("cfg.json");
		Files.writeString(f, "{\"channelId\":\"123\"}");
		BridgeConfig c = BridgeConfig.load(f);
		assertTrue(c.richMedia);
		assertEquals(BridgeConfig.DEFAULT_MEDIA_HOSTS, c.mediaAllowedHosts);
		String text = Files.readString(f);
		for (String k : new String[] {"richMedia", "mediaAllowedHosts",
				"mediaMaxItems", "mediaPreviewSize"}) {
			assertTrue(text.contains(k), k);
		}
		assertFalse(text.contains("mediaMaxBytes"));
	}

	@Test
	void richMediaSettable() {
		BridgeConfig c = new BridgeConfig();
		assertTrue(c.setByKey("richMedia", "false"));
		assertFalse(c.richMedia);
		assertTrue(c.setByKey("enableInSingleplayer", "true"));
		assertTrue(c.enableInSingleplayer);
		assertFalse(c.setByKey("mediaMaxItems", "5"));
		assertFalse(c.setByKey("richMedia", "maybe"));
		assertTrue(BridgeConfig.settableKeys().contains("richMedia"));
	}
}
