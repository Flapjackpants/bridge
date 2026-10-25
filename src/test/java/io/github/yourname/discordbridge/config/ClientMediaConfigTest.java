package io.github.yourname.discordbridge.config;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import io.github.yourname.discordbridge.media.MediaFetchPlan;
import io.github.yourname.discordbridge.media.MediaRef;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClientMediaConfigTest {
	@TempDir Path dir;

	@Test
	void clamps() {
		ClientMediaConfig c = new ClientMediaConfig();
		c.downloadThreads = 9;
		assertEquals(2, c.threads());
		c.downloadThreads = 0;
		assertEquals(1, c.threads());
		c.diskCacheMiB = 0;
		assertEquals(0, c.diskCacheBytes());
		c.diskCacheMiB = 1;
		assertEquals(1024L * 1024, c.diskCacheBytes());
		c.memoryBudgetMiB = 1;
		assertEquals(8L * 1024 * 1024, c.memoryBudgetBytes());
		c.maxDisplayWidth = 1000;
		c.maxDisplayHeight = 1;
		assertEquals(320, c.displayWidth());
		assertEquals(16, c.displayHeight());
		c.maxDownloadBytes = 1;
		c.decodeMaxSide = 9999;
		c.maxFrames = 0;
		assertEquals(64 * 1024, c.policy().maxBytes());
		assertEquals(512, c.policy().previewSize());
		assertEquals(8192, c.policy().maxDimension());
		assertEquals(16, c.policy().maxItems());
		assertEquals(512, c.limits().maxSide());
		assertEquals(1, c.limits().maxFrames());
		assertEquals(2048, c.limits().maxSourceSide());
		assertEquals(500, c.limits().maxSourceFrames());
		assertEquals(4_194_304L, c.limits().maxTotalPixels());
		assertEquals(4096, c.limits().maxSheetSide());
		c.allowedHosts = null;
		assertEquals(BridgeConfig.DEFAULT_MEDIA_HOSTS, c.policy().allowedHosts());
	}

	@Test
	void roundTrip() throws Exception {
		Path f = dir.resolve("sub/c.json");
		ClientMediaConfig c = ClientMediaConfig.load(f);
		assertTrue(Files.exists(f));
		assertEquals(MediaMode.ANIMATED, c.mode());
		assertFalse(c.clickToReveal);
		assertTrue(c.showInChatOverlay);
		c.maxFrames = 7;
		c.mode = MediaMode.STATIC;
		c.save(f);
		ClientMediaConfig reloaded = ClientMediaConfig.load(f);
		assertEquals(7, reloaded.maxFrames);
		assertEquals(MediaMode.STATIC, reloaded.mode());
	}

	@Test
	void unknownModeFallsBackToAnimated() throws Exception {
		Path f = dir.resolve("c.json");
		Files.writeString(f, "{\"mode\":\"BOGUS\"}");
		assertEquals(MediaMode.ANIMATED, ClientMediaConfig.load(f).mode());
	}

	@Test
	void missingKeysKeepDefaults() throws Exception {
		Path f = dir.resolve("c.json");
		Files.writeString(f, "{\"maxFrames\":5}");
		ClientMediaConfig c = ClientMediaConfig.load(f);
		assertEquals(5, c.maxFrames);
		assertEquals(MediaMode.ANIMATED, c.mode());
		assertTrue(c.showInChatOverlay);
	}

	@Test
	void staticLimitsOneFrame() {
		ClientMediaConfig c = new ClientMediaConfig();
		c.maxFrames = 64;
		c.mode = MediaMode.STATIC;
		assertEquals(1, c.limits().maxFrames());
		c.mode = MediaMode.ANIMATED;
		assertEquals(64, c.limits().maxFrames());
	}

	@Test
	void sourcesByMode() {
		MediaRef ref = new MediaRef(MediaRef.Kind.GIF, "https://cdn.discordapp.com/attachments/1/2/a.gif",
				"https://media.discordapp.net/attachments/1/2/a.gif?width=256&height=128", 100, 50, 10, "image/gif", "a.gif");
		ClientMediaConfig c = new ClientMediaConfig();
		c.mode = MediaMode.OFF;
		assertTrue(c.sources(ref).isEmpty());
		c.mode = MediaMode.ANIMATED;
		List<MediaFetchPlan.Source> animated = c.sources(ref);
		assertEquals(2, animated.size());
		assertTrue(animated.get(0).animated());
		assertFalse(animated.get(1).animated());
		assertEquals(MediaFetchPlan.plan(ref, c.policy()), animated);
		c.mode = MediaMode.STATIC;
		List<MediaFetchPlan.Source> st = c.sources(ref);
		assertEquals(2, st.size());
		assertFalse(st.get(0).animated());
		assertTrue(st.get(0).url().endsWith("&format=png"));
		assertTrue(st.get(1).animated());
		assertThrows(UnsupportedOperationException.class, () -> st.add(st.get(0)));
	}

	@Test
	void copyIsIndependent() {
		ClientMediaConfig c = new ClientMediaConfig();
		ClientMediaConfig d = c.copy();
		d.mode = MediaMode.OFF;
		d.maxDisplayHeight = 20;
		d.allowedHosts.add("example.com");
		assertEquals(MediaMode.ANIMATED, c.mode());
		assertEquals(90, c.maxDisplayHeight);
		assertFalse(c.allowedHosts.contains("example.com"));
	}

	@Test
	void budgetClamp() {
		ClientMediaConfig c = new ClientMediaConfig();
		c.memoryBudgetMiB = 1;
		assertEquals(8, c.memoryBudgetMiB());
		c.memoryBudgetMiB = 5000;
		assertEquals(1024, c.memoryBudgetMiB());
	}

	@Test
	void prefixDefaults() {
		assertEquals("[Discord] ", new ClientMediaConfig().prefixText());
	}

	@Test
	void prefixBlankAndNull() {
		ClientMediaConfig c = new ClientMediaConfig();
		c.discordPrefix = null;
		assertEquals("[Discord] ", c.prefixText());
		c.discordPrefix = "   ";
		assertEquals("", c.prefixText());
	}

	@Test
	void prefixSanitized() {
		ClientMediaConfig c = new ClientMediaConfig();
		c.discordPrefix = "§c[DC]  ";
		assertEquals("[DC] ", c.prefixText());
	}

	@Test
	void prefixCapped() {
		ClientMediaConfig c = new ClientMediaConfig();
		c.discordPrefix = "x".repeat(40);
		assertEquals("x".repeat(32) + " ", c.prefixText());
	}

	@Test
	void prefixColor() {
		ClientMediaConfig c = new ClientMediaConfig();
		c.discordColor = "#FF0000";
		assertEquals(0xFF0000, c.prefixRgb());
		c.discordColor = "zzz";
		assertEquals(0x5865F2, c.prefixRgb());
		c.discordColor = null;
		assertEquals(0x5865F2, c.prefixRgb());
	}

	@Test
	void oldFileGetsPrefixDefaults() throws Exception {
		Path f = dir.resolve("c.json");
		Files.writeString(f, "{\"mode\":\"STATIC\"}");
		ClientMediaConfig c = ClientMediaConfig.load(f);
		assertEquals("[Discord] ", c.prefixText());
		assertEquals(0x5865F2, c.prefixRgb());
	}

	@Test
	void modeCycles() {
		assertEquals(MediaMode.STATIC, MediaMode.OFF.next());
		assertEquals(MediaMode.ANIMATED, MediaMode.STATIC.next());
		assertEquals(MediaMode.OFF, MediaMode.ANIMATED.next());
	}

	@Test
	void loadBadJsonUsesDefaults() throws Exception {
		Path f = dir.resolve("c.json");
		Files.writeString(f, "{ not json");
		ClientMediaConfig c = ClientMediaConfig.load(f);
		assertEquals(64, c.maxFrames);
		assertEquals("{ not json", Files.readString(f));
	}
}
