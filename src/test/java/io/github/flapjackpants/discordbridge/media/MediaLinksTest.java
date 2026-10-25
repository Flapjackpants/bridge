package io.github.flapjackpants.discordbridge.media;

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

	private static MediaRef video(String url, String proxy, String link) {
		return new MediaRef(MediaRef.Kind.VIDEO, url, proxy, 10, 10, -1, "video/mp4", "", link);
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

	@Test
	void openUriVideoPrefersLink() {
		assertEquals(URI.create("https://www.youtube.com/watch?v=x"),
				MediaLinks.openUri(video(null, "https://media.discordapp.net/t.png", "https://www.youtube.com/watch?v=x"),
						POLICY));
	}

	@Test
	void openUriVideoAllowsHttpLink() {
		assertEquals(URI.create("http://example.com/v.mp4"),
				MediaLinks.openUri(video(null, null, "http://example.com/v.mp4"), POLICY));
	}

	@Test
	void openUriVideoNoLinkFallsBack() {
		assertEquals(URI.create("https://cdn.discordapp.com/v.mp4"),
				MediaLinks.openUri(video("https://cdn.discordapp.com/v.mp4", null, null), POLICY));
	}

	@Test
	void openUriVideoRejectsUnsafeLink() {
		for (String bad : new String[] {"javascript:alert(1)", "file:///etc/passwd", "https://u:p@x.com/"}) {
			assertEquals(URI.create("https://cdn.discordapp.com/v.mp4"),
					MediaLinks.openUri(video("https://cdn.discordapp.com/v.mp4", null, bad), POLICY), bad);
			assertNull(MediaLinks.openUri(video(null, null, bad), POLICY), bad);
		}
	}

	@Test
	void openUriNonVideoIgnoresLink() {
		MediaRef gif = new MediaRef(MediaRef.Kind.GIF, "https://cdn.discordapp.com/a.gif", null, 10, 10, -1,
				"image/gif", "", "https://tenor.com/view/x");
		assertEquals(URI.create("https://cdn.discordapp.com/a.gif"), MediaLinks.openUri(gif, POLICY));
	}
}
