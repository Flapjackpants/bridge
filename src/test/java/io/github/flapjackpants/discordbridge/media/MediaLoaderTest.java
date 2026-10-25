package io.github.flapjackpants.discordbridge.media;

import static org.junit.jupiter.api.Assertions.*;

import io.github.flapjackpants.discordbridge.media.MediaDecoder.Limits;
import io.github.flapjackpants.discordbridge.media.MediaLoader.Result;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MediaLoaderTest {
	private static final Limits LIMITS = new Limits(256, 64, 2048, 500, 4_194_304L, 4096);

	@TempDir Path dir;

	private final Map<String, byte[]> remote = new HashMap<>();
	private final List<String> calls = new ArrayList<>();
	private final MediaLoader.Fetcher fetcher = url -> {
		calls.add(url);
		byte[] b = remote.get(url);
		if (b == null) {
			throw new IOException("404");
		}
		return b;
	};

	private static byte[] png(int w, int h) throws IOException {
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setColor(new Color(0xFFFF0000, true));
		g.fillRect(0, 0, w, h);
		g.dispose();
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ImageIO.write(img, "png", bos);
		return bos.toByteArray();
	}

	private static MediaFetchPlan.Source src(String url, String key) {
		return new MediaFetchPlan.Source(url, key, false);
	}

	@Test
	void fallsBackToNextSource() throws Exception {
		remote.put("u2", png(8, 4));
		MediaLoader l = new MediaLoader(fetcher, null, LIMITS);
		Result r = l.load(List.of(src("u1", "k1"), src("u2", "k2")));
		assertNotNull(r);
		assertEquals(List.of("u1", "u2"), calls);
		assertEquals(r.sheet().frames(), r.delaysMs().length);
		assertEquals(r.sheet().width() * r.sheet().height(), r.argb().length);
		assertEquals(8, r.sheet().frameWidth());
		assertEquals(4, r.sheet().frameHeight());
	}

	private static final MediaLoader.Fetcher TOO_LARGE = url -> {
		throw new MediaTooLargeException("too large");
	};

	@Test
	void tooLargeReported() throws Exception {
		MediaLoader.Outcome o = new MediaLoader(TOO_LARGE, null, LIMITS).loadOutcome(List.of(src("u1", "k1")));
		assertNull(o.result());
		assertTrue(o.tooLarge());
	}

	@Test
	void tooLargeThenSuccess() throws Exception {
		remote.put("u2", png(8, 4));
		MediaLoader.Fetcher f = url -> {
			if (url.equals("u1")) {
				throw new MediaTooLargeException("too large");
			}
			return fetcher.fetch(url);
		};
		MediaLoader.Outcome o = new MediaLoader(f, null, LIMITS).loadOutcome(List.of(src("u1", "k1"), src("u2", "k2")));
		assertNotNull(o.result());
		assertFalse(o.tooLarge());
	}

	@Test
	void plainFailureNotTooLarge() throws Exception {
		MediaLoader.Outcome o = new MediaLoader(fetcher, null, LIMITS).loadOutcome(List.of(src("u1", "k1")));
		assertNull(o.result());
		assertFalse(o.tooLarge());
	}

	@Test
	void decodeTooLargeReported() throws Exception {
		remote.put("u1", png(3000, 10));
		MediaLoader.Outcome o = new MediaLoader(fetcher, null, LIMITS).loadOutcome(List.of(src("u1", "k1")));
		assertNull(o.result());
		assertTrue(o.tooLarge());
	}

	@Test
	void usesDiskCache() throws Exception {
		DiskCache disk = new DiskCache(dir, 1_000_000);
		disk.put("k1", png(4, 4));
		MediaLoader l = new MediaLoader(fetcher, disk, LIMITS);
		assertNotNull(l.load(List.of(src("u1", "k1"))));
		assertTrue(calls.isEmpty());
	}

	@Test
	void refetchesCorruptCacheEntry() throws Exception {
		DiskCache disk = new DiskCache(dir, 1_000_000);
		disk.put("k1", new byte[] {9, 9, 9, 9});
		byte[] good = png(4, 4);
		remote.put("u1", good);
		MediaLoader l = new MediaLoader(fetcher, disk, LIMITS);
		assertNotNull(l.load(List.of(src("u1", "k1"))));
		assertEquals(List.of("u1"), calls);
		assertArrayEquals(good, disk.get("k1"));
	}

	@Test
	void returnsNullWhenAllFail() throws Exception {
		remote.put("u2", new byte[] {1, 2, 3});
		MediaLoader l = new MediaLoader(fetcher, null, LIMITS);
		assertNull(l.load(List.of(src("u1", "k1"), src("u2", "k2"))));
	}

	@Test
	void worksWithoutDisk() throws Exception {
		remote.put("u1", png(4, 4));
		MediaLoader l = new MediaLoader(fetcher, null, LIMITS);
		assertNotNull(l.load(List.of(src("u1", "k1"))));
		assertNotNull(l.load(List.of(src("u1", "k1"))));
		assertEquals(List.of("u1", "u1"), calls);
	}

	@Test
	void propagatesInterrupt() {
		List<String> seen = new ArrayList<>();
		MediaLoader l = new MediaLoader(url -> {
			seen.add(url);
			throw new InterruptedIOException("stop");
		}, null, LIMITS);
		assertThrows(InterruptedIOException.class, () -> l.load(List.of(src("u1", "k1"), src("u2", "k2"))));
		assertEquals(List.of("u1"), seen);
	}
}
