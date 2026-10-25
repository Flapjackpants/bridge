package io.github.yourname.discordbridge.media;

import static org.junit.jupiter.api.Assertions.*;

import io.github.yourname.discordbridge.config.BridgeConfig;
import io.github.yourname.discordbridge.media.MediaFetchPlan.Source;
import io.github.yourname.discordbridge.media.MediaPipeline.Policy;
import java.util.List;
import org.junit.jupiter.api.Test;

class MediaFetchPlanTest {
	private static final Policy POLICY = Policy.from(new BridgeConfig());
	private static final String CDN = "https://cdn.discordapp.com/attachments/1/2/a.png";
	private static final String PROXY = "https://media.discordapp.net/attachments/1/2/a.png?width=256&height=128";
	private static final String PROXY_PNG = PROXY + "&format=png";

	private static MediaRef ref(MediaRef.Kind k, String url, String proxy, long size, String ct) {
		return new MediaRef(k, url, proxy, 100, 50, size, ct, "a");
	}

	private static List<String> urls(List<Source> s) {
		return s.stream().map(Source::url).toList();
	}

	@Test
	void staticOrder() {
		List<Source> s = MediaFetchPlan.plan(ref(MediaRef.Kind.STATIC, CDN, PROXY, 10, "image/png"), POLICY);
		assertEquals(List.of(PROXY_PNG, CDN), urls(s));
		assertFalse(s.get(0).animated());
		assertFalse(s.get(1).animated());
		assertEquals(MediaFetchPlan.cacheKey(CDN), s.get(1).cacheKey());
	}

	@Test
	void staticWebpNoProxyEmpty() {
		assertTrue(MediaFetchPlan.plan(ref(MediaRef.Kind.STATIC, CDN, null, 10, "image/webp"), POLICY).isEmpty());
	}

	@Test
	void gifOversizeSkipsOriginal() {
		Policy small = new Policy(POLICY.allowedHosts(), 100, 8192, 16, 256);
		List<Source> s = MediaFetchPlan.plan(ref(MediaRef.Kind.GIF, CDN, PROXY, 1000, "image/gif"), small);
		assertEquals(List.of(PROXY_PNG), urls(s));
		List<Source> ok = MediaFetchPlan.plan(ref(MediaRef.Kind.GIF, CDN, PROXY, 50, "image/gif"), small);
		assertEquals(List.of(CDN, PROXY_PNG), urls(ok));
		assertTrue(ok.get(0).animated());
		assertFalse(ok.get(1).animated());
	}

	@Test
	void gifvAnimatedFirst() {
		String gif = "https://static1.klipy.com/a.gif";
		List<Source> s = MediaFetchPlan.plan(ref(MediaRef.Kind.GIFV, gif, PROXY, -1, "image/gif"), POLICY);
		assertEquals(List.of(gif, PROXY_PNG), urls(s));
		assertTrue(s.get(0).animated());
		String webp = "https://static1.klipy.com/a.webp";
		List<Source> w = MediaFetchPlan.plan(ref(MediaRef.Kind.GIFV, webp, PROXY, -1, "image/webp"), POLICY);
		assertEquals(List.of(webp, PROXY_PNG), urls(w));
		assertTrue(w.get(0).animated());
		List<Source> t = MediaFetchPlan.plan(ref(MediaRef.Kind.GIFV, CDN, PROXY, -1, "image/png"), POLICY);
		assertEquals(List.of(PROXY_PNG, CDN), urls(t));
		assertFalse(t.get(1).animated());
	}

	@Test
	void videoThumbnailOnly() {
		String mp4 = "https://cdn.discordapp.com/attachments/1/2/a.mp4";
		List<Source> s = MediaFetchPlan.plan(ref(MediaRef.Kind.VIDEO, mp4, PROXY, 10, "video/mp4"), POLICY);
		assertEquals(List.of(PROXY_PNG), urls(s));
	}

	@Test
	void disallowedDropped() {
		MediaRef r = ref(MediaRef.Kind.STATIC, "https://example.com/a.png", "https://example.org/p.png", 10, "image/png");
		assertTrue(MediaFetchPlan.plan(r, POLICY).isEmpty());
		MediaRef r2 = ref(MediaRef.Kind.STATIC, null, null, 10, "image/png");
		assertTrue(MediaFetchPlan.plan(r2, POLICY).isEmpty());
	}

	@Test
	void cacheKeyAttachment() {
		String base = "https://cdn.discordapp.com/attachments/1/2/a.gif";
		assertEquals("a2-o", MediaFetchPlan.cacheKey(base + "?ex=1&hm=x"));
		assertEquals("a2-o", MediaFetchPlan.cacheKey(base + "?ex=9"));
		assertEquals("a2-p256x128png", MediaFetchPlan.cacheKey(
				"https://media.discordapp.net/attachments/1/2/a.gif?ex=1&width=256&height=128&format=png"));
	}

	@Test
	void cacheKeyHashed() {
		String k = MediaFetchPlan.cacheKey("https://media.tenor.com/abc/cat.gif");
		assertTrue(k.matches("u[0-9a-f]{40}"), k);
		assertTrue(k.matches("[a-z0-9-]{1,80}"));
		assertNotEquals(k, MediaFetchPlan.cacheKey("https://media.tenor.com/abc/dog.gif"));
		assertTrue(MediaFetchPlan.cacheKey("::bad uri").matches("[a-z0-9-]{1,80}"));
	}

	@Test
	void pngFormat() {
		assertEquals("https://media.discordapp.net/a.png?width=1&format=png",
				MediaFetchPlan.withPngFormat("https://media.discordapp.net/a.png?format=webp&width=1"));
		assertEquals("https://images-ext-1.discordapp.net/a.png?format=png",
				MediaFetchPlan.withPngFormat("https://images-ext-1.discordapp.net/a.png"));
		String tenor = "https://media.tenor.com/abc/cat.png?format=webp";
		assertEquals(tenor, MediaFetchPlan.withPngFormat(tenor));
	}
}
