package io.github.yourname.discordbridge.media;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class MediaLinksTest {
	private static final MediaPipeline.Policy POLICY = new MediaPipeline.Policy(
			List.of("cdn.discordapp.com", "*.klipy.com"), 8_000_000L, 8192, 4, 256);

	private static MediaRef ref(String url, String proxy, String name) {
		return new MediaRef(MediaRef.Kind.GIF, url, proxy, 10, 10, -1, "image/gif", name);
	}

	@Test
	void labelPrefersFileName() {
		assertEquals("cat.gif", MediaLinks.label(ref("https://static.klipy.com/a.gif", null, "cat.gif")));
	}

	@Test
	void labelFallsBackToHost() {
		assertEquals("static.klipy.com", MediaLinks.label(ref("https://static.klipy.com/a.gif", null, "")));
	}

	@Test
	void labelFallsBackToProxyHost() {
		assertEquals("cdn.discordapp.com", MediaLinks.label(ref(null, "https://cdn.discordapp.com/x", "")));
	}

	@Test
	void labelEmptyWhenUnknown() {
		assertEquals("", MediaLinks.label(ref(null, null, "")));
		assertEquals("", MediaLinks.label(ref(null, null, "   ")));
		assertEquals("", MediaLinks.label(ref("::bad", null, "")));
	}

	@Test
	void labelTruncates() {
		assertEquals(MediaLinks.MAX_LABEL, MediaLinks.label(ref(null, null, "a".repeat(100))).length());
	}

	@Test
	void openUriPrefersUrl() {
		assertEquals(URI.create("https://cdn.discordapp.com/a.gif"),
				MediaLinks.openUri(ref("https://cdn.discordapp.com/a.gif", "https://cdn.discordapp.com/p.gif", ""), POLICY));
	}

	@Test
	void openUriFallsBackToProxy() {
		assertEquals(URI.create("https://cdn.discordapp.com/p.gif"),
				MediaLinks.openUri(ref("https://evil.example/a.gif", "https://cdn.discordapp.com/p.gif", ""), POLICY));
	}

	@Test
	void openUriRejectsInsecure() {
		assertNull(MediaLinks.openUri(ref("http://cdn.discordapp.com/a.gif", null, ""), POLICY));
	}
}
