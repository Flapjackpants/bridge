package io.github.yourname.discordbridge.config;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
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
		assertTrue(c.enabled);
		c.maxFrames = 7;
		c.save(f);
		assertEquals(7, ClientMediaConfig.load(f).maxFrames);
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
