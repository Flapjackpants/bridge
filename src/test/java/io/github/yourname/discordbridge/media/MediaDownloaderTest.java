package io.github.yourname.discordbridge.media;

import static org.junit.jupiter.api.Assertions.*;

import io.github.yourname.discordbridge.config.BridgeConfig;
import io.github.yourname.discordbridge.media.MediaPipeline.Policy;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MediaDownloaderTest {
	private static final Policy POLICY = Policy.from(new BridgeConfig());

	@Test
	void redirectAllowlist() {
		String base = "https://cdn.discordapp.com/attachments/1/2/a.gif";
		assertNull(MediaDownloader.resolveRedirect(base, "https://evil.example.com/x", POLICY));
		assertNull(MediaDownloader.resolveRedirect(base, "http://cdn.discordapp.com/x", POLICY));
		assertNull(MediaDownloader.resolveRedirect(base, null, POLICY));
		assertEquals("https://cdn.discordapp.com/other/b.gif",
				MediaDownloader.resolveRedirect(base, "/other/b.gif", POLICY));
		assertEquals("https://media.tenor.com/z.gif",
				MediaDownloader.resolveRedirect(base, "https://media.tenor.com/z.gif", POLICY));
	}

	@Test
	void readLimitedBoundary() throws IOException {
		byte[] ten = new byte[10];
		assertEquals(10, MediaDownloader.readLimited(new ByteArrayInputStream(ten), 10).length);
		IOException e = assertThrows(IOException.class,
				() -> MediaDownloader.readLimited(new ByteArrayInputStream(new byte[11]), 10));
		assertInstanceOf(MediaTooLargeException.class, e);
		assertEquals("too large", e.getMessage());
	}

	@Test
	void fetchRejectsDisallowed() {
		MediaDownloader d = new MediaDownloader(POLICY, Duration.ofSeconds(1));
		IOException e = assertThrows(IOException.class, () -> d.fetch("https://example.com/a.png"));
		assertEquals("not allowed", e.getMessage());
	}
}
