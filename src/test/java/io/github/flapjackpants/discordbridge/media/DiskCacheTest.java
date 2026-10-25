package io.github.flapjackpants.discordbridge.media;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiskCacheTest {
	@TempDir Path dir;

	@Test
	void roundTrip() {
		DiskCache c = new DiskCache(dir.resolve("c"), 1000);
		assertNull(c.get("k1"));
		c.put("k1", new byte[] {1, 2, 3});
		assertArrayEquals(new byte[] {1, 2, 3}, c.get("k1"));
	}

	@Test
	void trimEvictsOldest() throws Exception {
		DiskCache c = new DiskCache(dir, 100);
		c.put("a", new byte[60]);
		Files.setLastModifiedTime(dir.resolve("a.bin"), FileTime.fromMillis(1000));
		c.put("b", new byte[60]);
		Files.setLastModifiedTime(dir.resolve("b.bin"), FileTime.fromMillis(2000));
		c.trim();
		assertFalse(Files.exists(dir.resolve("a.bin")));
		assertTrue(Files.exists(dir.resolve("b.bin")));
	}

	@Test
	void rejectsBadKey() {
		DiskCache c = new DiskCache(dir, 100);
		assertThrows(IllegalArgumentException.class, () -> c.get("../x"));
		assertThrows(IllegalArgumentException.class, () -> c.put("../x", new byte[1]));
	}

	@Test
	void disabled() throws Exception {
		DiskCache c = new DiskCache(dir.resolve("off"), 0);
		c.put("k", new byte[5]);
		assertNull(c.get("k"));
		assertFalse(Files.exists(dir.resolve("off")));
	}
}
