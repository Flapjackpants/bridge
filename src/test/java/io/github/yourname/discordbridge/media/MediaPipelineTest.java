package io.github.yourname.discordbridge.media;

import static org.junit.jupiter.api.Assertions.*;

import io.github.yourname.discordbridge.config.BridgeConfig;
import io.github.yourname.discordbridge.media.MediaPipeline.Candidate;
import io.github.yourname.discordbridge.media.MediaPipeline.Policy;
import io.github.yourname.discordbridge.media.MediaPipeline.Source;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class MediaPipelineTest {
	private static final String CDN = "https://cdn.discordapp.com/attachments/1/2/";
	private static final String PROXY = "https://media.discordapp.net/attachments/1/2/";
	private static final Policy POLICY = Policy.from(new BridgeConfig());

	private static Candidate att(String url, String proxy, int w, int h, long size, String ct, String name) {
		return new Candidate(Source.ATTACHMENT, url, proxy, w, h, size, ct, name, null, null, 0, 0, null);
	}

	private static Candidate png() {
		return att(CDN + "a.png", PROXY + "a.png", 1024, 512, 1000, "image/png", "a.png");
	}

	private static List<MediaRef> run(Candidate c) {
		return MediaPipeline.process(List.of(c), POLICY);
	}

	@Test
	void staticAttachmentResized() {
		List<MediaRef> out = run(png());
		assertEquals(1, out.size());
		MediaRef r = out.get(0);
		assertEquals(MediaRef.Kind.STATIC, r.kind());
		assertEquals(CDN + "a.png", r.url());
		assertEquals(PROXY + "a.png?width=256&height=128", r.proxyUrl());
		assertEquals(1024, r.width());
		assertEquals("image/png", r.contentType());
		assertEquals("a.png", r.fileName());
	}

	@Test
	void resizeAppendsToQuery() {
		assertEquals(PROXY + "a.png?ex=1&hm=2&width=256&height=128",
				MediaPipeline.withResize(PROXY + "a.png?ex=1&hm=2", 1024, 512, 256));
	}

	@Test
	void resizeReplacesExisting() {
		assertEquals(PROXY + "a.png?ex=1&hm=2&width=256&height=128",
				MediaPipeline.withResize(PROXY + "a.png?width=10&ex=1&height=10&hm=2", 1024, 512, 256));
		assertEquals(PROXY + "a.png?width=256&height=128",
				MediaPipeline.withResize(PROXY + "a.png?width=10&height=10", 1024, 512, 256));
	}

	@Test
	void resizeNoUpscale() {
		assertEquals(PROXY + "a.png?width=100&height=50", MediaPipeline.withResize(PROXY + "a.png", 100, 50, 256));
	}

	@Test
	void resizeUnknownDims() {
		assertEquals(PROXY + "a.png", MediaPipeline.withResize(PROXY + "a.png", 0, 0, 256));
		assertNull(MediaPipeline.withResize(null, 10, 10, 256));
	}

	@Test
	void gifByContentType() {
		MediaRef r = run(att(CDN + "a.gif", null, 10, 10, 10, "image/gif; charset=x", "a.gif")).get(0);
		assertEquals(MediaRef.Kind.GIF, r.kind());
		assertEquals("image/gif", r.contentType());
	}

	@Test
	void gifByExtension() {
		MediaRef r = run(att(CDN + "a.bin", null, 10, 10, 10, null, "x.GIF")).get(0);
		assertEquals(MediaRef.Kind.GIF, r.kind());
	}

	@Test
	void videoAttachment() {
		MediaRef r = run(att(CDN + "v.mp4", PROXY + "v.mp4", 640, 360, 5000, "video/mp4", "v.mp4")).get(0);
		assertEquals(MediaRef.Kind.VIDEO, r.kind());
		assertNull(r.proxyUrl());
		assertEquals(CDN + "v.mp4", r.url());
	}

	@Test
	void nonMediaSkipped() {
		assertTrue(run(att(CDN + "a.txt", null, -1, -1, 10, "text/plain", "a.txt")).isEmpty());
		assertTrue(run(att(CDN + "a.svg", null, 10, 10, 10, "image/svg+xml", "a.svg")).isEmpty());
	}

	@Test
	void oversizeSkipped() {
		assertTrue(run(att(CDN + "a.png", null, 10, 10, 9L * 1024 * 1024, "image/png", "a.png")).isEmpty());
	}

	@Test
	void oversizeDimensionSkipped() {
		assertTrue(run(att(CDN + "a.png", null, 9000, 10, 10, "image/png", "a.png")).isEmpty());
	}

	@Test
	void unknownSizeAllowed() {
		Candidate c = new Candidate(Source.EMBED_IMAGE, CDN + "e.png", null, 10, 10, -1, null, null,
				null, null, 0, 0, null);
		List<MediaRef> out = run(c);
		assertEquals(1, out.size());
		assertEquals(-1, out.get(0).sizeBytes());
		assertEquals(MediaRef.Kind.STATIC, out.get(0).kind());
	}

	@Test
	void disallowedUrlKeepsProxy() {
		MediaRef r = run(att("https://evil.example.com/a.png", PROXY + "a.png", 100, 50, 10, "image/png", "a.png")).get(0);
		assertNull(r.url());
		assertEquals(PROXY + "a.png?width=100&height=50", r.proxyUrl());
	}

	@Test
	void rejectsUnsafeUrls() {
		assertFalse(POLICY.allows("http://cdn.discordapp.com/a.png"));
		assertFalse(POLICY.allows("https://cdn.discordapp.com:8443/a.png"));
		assertFalse(POLICY.allows("https://user@cdn.discordapp.com/a.png"));
		assertFalse(POLICY.allows(null));
		assertFalse(POLICY.allows("https://cdn.discordapp.com/" + "a".repeat(2100)));
		assertTrue(POLICY.allows("HTTPS://cdn.discordapp.com:443/a.png"));
		assertTrue(run(att("http://cdn.discordapp.com/a.png", "https://evil.example.com/a.png", 10, 10, 10,
				"image/png", "a.png")).isEmpty());
		assertTrue(run(att("https://cdn.discordapp.com:8443/a.png", null, 10, 10, 10, "image/png", "a.png")).isEmpty());
		assertTrue(run(att("https://u:p@cdn.discordapp.com/a.png", null, 10, 10, 10, "image/png", "a.png")).isEmpty());
	}

	@Test
	void wildcardMatching() {
		Policy p = new Policy(List.of("*.discordapp.net"), 1000, 1000, 4, 256);
		assertFalse(p.allows("https://discordapp.net/a"));
		assertFalse(p.allows("https://evildiscordapp.net/a"));
		assertTrue(p.allows("https://images-ext-2.discordapp.net/a"));
	}

	private static Candidate tenor() {
		return new Candidate(Source.EMBED_GIFV, "https://media.tenor.com/abcdefAAAPo/cat.mp4", null, 640, 360, -1, null,
				null, "https://media.tenor.com/abcdefAAAAe/cat.png",
				"https://images-ext-1.discordapp.net/external/x/https/media.tenor.com/abcdefAAAAe/cat.png",
				320, 180, "image/png");
	}

	@Test
	void tenorGifvToGif() {
		MediaRef r = run(tenor()).get(0);
		assertEquals(MediaRef.Kind.GIFV, r.kind());
		assertEquals("https://media.tenor.com/abcdefAAAAC/cat.gif", r.url());
		assertEquals("image/gif", r.contentType());
		assertEquals("https://images-ext-1.discordapp.net/external/x/https/media.tenor.com/abcdefAAAAe/cat.png"
				+ "?width=256&height=144", r.proxyUrl());
		assertEquals(640, r.width());
	}

	@Test
	void gifvFallsBackToThumbnail() {
		Candidate c = new Candidate(Source.EMBED_GIFV, "https://media.giphy.com/media/x/giphy.mp4", null, 0, 0, -1, null,
				null, "https://media.giphy.com/media/x/giphy_s.gif",
				"https://images-ext-1.discordapp.net/external/y/giphy_s.gif", 200, 100, "image/png");
		MediaRef r = run(c).get(0);
		assertEquals(MediaRef.Kind.GIFV, r.kind());
		assertNull(r.url());
		assertEquals("https://images-ext-1.discordapp.net/external/y/giphy_s.gif?width=200&height=100", r.proxyUrl());
		assertEquals("image/png", r.contentType());
		assertEquals(200, r.width());
	}

	@Test
	void gifvTenorDisallowed() {
		Policy p = new Policy(List.of("*.discordapp.net"), 1000000, 8192, 4, 256);
		MediaRef r = MediaPipeline.process(List.of(tenor()), p).get(0);
		assertNull(r.url());
		assertNotNull(r.proxyUrl());
	}

	@Test
	void tenorRenditionRejects() {
		assertNull(MediaPipeline.tenorGifRendition("https://example.com/abcdefAAAPo/cat.mp4"));
		assertNull(MediaPipeline.tenorGifRendition("https://media.tenor.com/cat.mp4"));
		assertNull(MediaPipeline.tenorGifRendition("https://media.tenor.com/abc/cat.mp4"));
		assertNull(MediaPipeline.tenorGifRendition("https://media.tenor.com/abcdefAAAPo/cat"));
		assertNull(MediaPipeline.tenorGifRendition(null));
		assertNull(MediaPipeline.tenorGifRendition("::bad uri"));
		assertEquals("https://media.tenor.com/abcdefAAAAC/cat.gif",
				MediaPipeline.tenorGifRendition("https://media.tenor.com/abcdefAAAPo/cat.mp4?x=1"));
	}

	@Test
	void maxItemsCap() {
		List<Candidate> six = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			six.add(att(CDN + i + ".png", null, 10, 10, 10, "image/png", i + ".png"));
		}
		Policy four = new Policy(List.of("cdn.discordapp.com"), 1000, 1000, 4, 256);
		List<MediaRef> out = MediaPipeline.process(six, four);
		assertEquals(4, out.size());
		assertEquals("0.png", out.get(0).fileName());
		assertEquals("3.png", out.get(3).fileName());
		assertTrue(MediaPipeline.process(six, new Policy(four.allowedHosts(), 1000, 1000, 0, 256)).isEmpty());
		assertThrows(UnsupportedOperationException.class, () -> out.add(out.get(0)));
	}

	@Test
	void policyFromConfig() {
		BridgeConfig c = new BridgeConfig();
		c.mediaAllowedHosts = null;
		assertEquals(BridgeConfig.DEFAULT_MEDIA_HOSTS, Policy.from(c).allowedHosts());
		c.mediaAllowedHosts = Arrays.asList(" CDN.Discordapp.com ", "", "  ", null);
		assertEquals(List.of("cdn.discordapp.com"), Policy.from(c).allowedHosts());
		c.mediaMaxItems = 99;
		c.mediaMaxBytes = 0;
		c.mediaMaxDimension = -1;
		c.mediaPreviewSize = 1;
		Policy p = Policy.from(c);
		assertEquals(16, p.maxItems());
		assertEquals(8L * 1024 * 1024, p.maxBytes());
		assertEquals(8192, p.maxDimension());
		assertEquals(16, p.previewSize());
		c.mediaMaxItems = -3;
		c.mediaPreviewSize = 5000;
		assertEquals(0, Policy.from(c).maxItems());
		assertEquals(1024, Policy.from(c).previewSize());
	}

	@Test
	void fileNameClipped() {
		MediaRef r = run(att(CDN + "a.png", null, 10, 10, 10, "image/png", "n".repeat(300))).get(0);
		assertEquals(255, r.fileName().length());
	}

	@Test
	void mayGainEmbeds() {
		assertTrue(MediaPipeline.mayGainEmbeds("see https://tenor.com/x", 0, 0));
		assertFalse(MediaPipeline.mayGainEmbeds("see https://tenor.com/x", 1, 0));
		assertFalse(MediaPipeline.mayGainEmbeds("see https://tenor.com/x", 0, 1));
		assertFalse(MediaPipeline.mayGainEmbeds("no link", 0, 0));
		assertFalse(MediaPipeline.mayGainEmbeds(null, 0, 0));
	}
}
