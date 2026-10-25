package io.github.yourname.discordbridge.media;

import io.github.yourname.discordbridge.config.BridgeConfig;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

public final class MediaPipeline {
	public static final int MAX_URL_LENGTH = 2048;
	public static final int MAX_ITEMS_HARD = 16;
	private static final long DEFAULT_MAX_BYTES = 8L * 1024 * 1024;
	private static final int DEFAULT_MAX_DIMENSION = 8192;
	private static final int MAX_FILE_NAME = 255;
	private static final Pattern LINK = Pattern.compile("https?://\\S+");

	private MediaPipeline() {}

	public enum Source { ATTACHMENT, EMBED_IMAGE, EMBED_GIFV, EMBED_VIDEO }

	public record Candidate(Source source, @Nullable String url, @Nullable String proxyUrl,
			int width, int height, long sizeBytes, @Nullable String contentType, @Nullable String fileName,
			@Nullable String thumbUrl, @Nullable String thumbProxyUrl, int thumbWidth, int thumbHeight,
			@Nullable String thumbContentType) {}

	public record Policy(List<String> allowedHosts, long maxBytes, int maxDimension, int maxItems, int previewSize) {
		/** null → copy of the default hosts; else trimmed, lowercased, blanks/nulls dropped. */
		public static List<String> normalizeHosts(@Nullable List<String> raw) {
			if (raw == null) {
				return List.copyOf(BridgeConfig.DEFAULT_MEDIA_HOSTS);
			}
			List<String> hosts = new ArrayList<>();
			for (String h : raw) {
				if (h != null && !h.isBlank()) {
					hosts.add(h.trim().toLowerCase(Locale.ROOT));
				}
			}
			return List.copyOf(hosts);
		}

		public static Policy from(BridgeConfig cfg) {
			return new Policy(normalizeHosts(cfg.mediaAllowedHosts),
					cfg.mediaMaxBytes <= 0 ? DEFAULT_MAX_BYTES : cfg.mediaMaxBytes,
					cfg.mediaMaxDimension <= 0 ? DEFAULT_MAX_DIMENSION : cfg.mediaMaxDimension,
					Math.clamp(cfg.mediaMaxItems, 0, MAX_ITEMS_HARD),
					Math.clamp(cfg.mediaPreviewSize, 16, 1024));
		}

		public boolean allows(@Nullable String url) {
			if (url == null || url.length() > MAX_URL_LENGTH) {
				return false;
			}
			URI uri;
			try {
				uri = new URI(url);
			} catch (URISyntaxException e) {
				return false;
			}
			if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null
					|| (uri.getPort() != -1 && uri.getPort() != 443) || uri.getHost() == null) {
				return false;
			}
			String host = uri.getHost().toLowerCase(Locale.ROOT);
			for (String entry : allowedHosts) {
				if (host.equals(entry) || (entry.startsWith("*.") && host.endsWith(entry.substring(1)))) {
					return true;
				}
			}
			return false;
		}
	}

	/** Never throws; the returned list is unmodifiable. */
	public static List<MediaRef> process(List<Candidate> candidates, Policy policy) {
		List<MediaRef> out = new ArrayList<>();
		for (Candidate c : candidates) {
			if (out.size() >= policy.maxItems()) {
				break;
			}
			try {
				MediaRef ref = convert(c, policy);
				if (ref != null) {
					out.add(ref);
				}
			} catch (RuntimeException e) {
				// malformed candidate: skip it
			}
		}
		return List.copyOf(out);
	}

	private static @Nullable MediaRef convert(Candidate c, Policy policy) {
		String ct = normalizeType(c.contentType(), c.fileName(), c.url());
		MediaRef.Kind kind;
		switch (c.source()) {
			case ATTACHMENT -> {
				if (ct.equals("image/gif")) {
					kind = MediaRef.Kind.GIF;
				} else if (ct.equals("image/png") || ct.equals("image/jpeg") || ct.equals("image/webp")) {
					kind = MediaRef.Kind.STATIC;
				} else if (ct.startsWith("video/")) {
					kind = MediaRef.Kind.VIDEO;
				} else {
					return null;
				}
			}
			case EMBED_IMAGE -> kind = ct.equals("image/gif") ? MediaRef.Kind.GIF : MediaRef.Kind.STATIC;
			case EMBED_GIFV -> kind = MediaRef.Kind.GIFV;
			default -> kind = MediaRef.Kind.VIDEO;
		}
		if (c.sizeBytes() > 0 && c.sizeBytes() > policy.maxBytes()) {
			return null;
		}
		if (c.width() > policy.maxDimension() || c.height() > policy.maxDimension()) {
			return null;
		}

		String url;
		String proxy;
		String type = ct;
		int w = c.width();
		int h = c.height();
		boolean proxyFromThumb = false;
		switch (kind) {
			case GIFV -> {
				url = null;
				boolean rendition = false;
				String[] options = {gifRendition(c.url()), gifRendition(c.thumbUrl()), c.thumbUrl()};
				for (int i = 0; i < options.length; i++) {
					if (policy.allows(options[i])) {
						url = options[i];
						rendition = i < 2;
						break;
					}
				}
				type = rendition ? "image/gif" : normalizeType(c.thumbContentType(), null, c.thumbUrl());
				proxy = c.thumbProxyUrl();
				proxyFromThumb = true;
				if (w <= 0 || h <= 0) {
					w = c.thumbWidth();
					h = c.thumbHeight();
				}
			}
			case VIDEO -> {
				url = c.url();
				proxy = c.thumbProxyUrl();
				proxyFromThumb = true;
			}
			default -> {
				url = c.url();
				proxy = c.proxyUrl();
			}
		}
		int rw = w;
		int rh = h;
		if (proxyFromThumb && c.thumbWidth() > 0 && c.thumbHeight() > 0) {
			rw = c.thumbWidth();
			rh = c.thumbHeight();
		}
		proxy = withResize(proxy, rw, rh, policy.previewSize());

		if (!policy.allows(url)) {
			url = null;
		}
		if (!policy.allows(proxy)) {
			proxy = null;
		}
		if (url == null && proxy == null) {
			return null;
		}
		String name = c.fileName() == null ? "" : c.fileName();
		if (name.length() > MAX_FILE_NAME) {
			name = name.substring(0, MAX_FILE_NAME);
		}
		return new MediaRef(kind, url, proxy, w, h, c.sizeBytes(), type, name);
	}

	private static String normalizeType(@Nullable String contentType, @Nullable String fileName, @Nullable String url) {
		String ct = "";
		if (contentType != null) {
			ct = contentType;
			int semi = ct.indexOf(';');
			if (semi >= 0) {
				ct = ct.substring(0, semi);
			}
			ct = ct.trim().toLowerCase(Locale.ROOT);
		}
		if (!ct.isBlank()) {
			return ct;
		}
		String source = fileName;
		if (source == null || source.isBlank()) {
			source = url == null ? "" : pathOf(url);
		}
		int dot = source.lastIndexOf('.');
		String ext = dot < 0 ? "" : source.substring(dot + 1).toLowerCase(Locale.ROOT);
		return switch (ext) {
			case "png" -> "image/png";
			case "jpg", "jpeg" -> "image/jpeg";
			case "webp" -> "image/webp";
			case "gif" -> "image/gif";
			case "mp4" -> "video/mp4";
			case "webm" -> "video/webm";
			case "mov" -> "video/quicktime";
			default -> "";
		};
	}

	private static String pathOf(String url) {
		int cut = url.length();
		for (char ch : new char[] {'?', '#'}) {
			int i = url.indexOf(ch);
			if (i >= 0 && i < cut) {
				cut = i;
			}
		}
		return url.substring(0, cut);
	}

	private static final Pattern GIPHY_MEDIA_HOST = Pattern.compile("media\\d?\\.giphy\\.com");

	/**
	 * .gif rendition for known GIF hosts, else null. Never throws. Klipy is absent on purpose: its renditions
	 * have unrelated random names, and a guessed .gif answers 200 with a "content no longer available" image.
	 */
	public static @Nullable String gifRendition(@Nullable String url) {
		if (url == null) {
			return null;
		}
		try {
			URI uri = new URI(url);
			if (uri.getHost() == null || uri.getPath() == null) {
				return null;
			}
			String host = uri.getHost().toLowerCase(Locale.ROOT);
			if (host.equals("media.tenor.com")) {
				return tenorGifRendition(url);
			}
			List<String> segments = new ArrayList<>();
			for (String s : uri.getPath().split("/")) {
				if (!s.isEmpty()) {
					segments.add(s);
				}
			}
			if (GIPHY_MEDIA_HOST.matcher(host).matches()) {
				if (segments.size() >= 3 && segments.get(0).equals("media")) {
					return "https://" + host + "/" + String.join("/", segments.subList(0, segments.size() - 1))
							+ "/giphy.gif";
				}
				return null;
			}
			if (host.equals("i.giphy.com")) {
				return singleSegmentGif(host, segments, List.of("mp4", "webp", "gif"));
			}
			if (host.equals("i.imgur.com")) {
				return singleSegmentGif(host, segments, List.of("mp4", "gifv", "webm", "gif"));
			}
			return null;
		} catch (URISyntaxException | RuntimeException e) {
			return null;
		}
	}

	private static @Nullable String singleSegmentGif(String host, List<String> segments, List<String> exts) {
		if (segments.size() != 1) {
			return null;
		}
		String seg = segments.get(0);
		int dot = seg.lastIndexOf('.');
		if (dot <= 0 || !exts.contains(seg.substring(dot + 1).toLowerCase(Locale.ROOT))) {
			return null;
		}
		return "https://" + host + "/" + seg.substring(0, dot) + ".gif";
	}

	public static @Nullable String tenorGifRendition(@Nullable String url) {
		if (url == null) {
			return null;
		}
		URI uri;
		try {
			uri = new URI(url);
		} catch (URISyntaxException e) {
			return null;
		}
		if (uri.getHost() == null || !uri.getHost().equalsIgnoreCase("media.tenor.com") || uri.getPath() == null) {
			return null;
		}
		List<String> segments = new ArrayList<>();
		for (String s : uri.getPath().split("/")) {
			if (!s.isEmpty()) {
				segments.add(s);
			}
		}
		if (segments.size() != 2) {
			return null;
		}
		String id = segments.get(0);
		String file = segments.get(1);
		if (id.length() <= 5 || !file.contains(".")) {
			return null;
		}
		return "https://media.tenor.com/" + id.substring(0, id.length() - 5) + "AAAAC" + "/"
				+ file.substring(0, file.lastIndexOf('.')) + ".gif";
	}

	public static @Nullable String withResize(@Nullable String proxyUrl, int width, int height, int previewSize) {
		if (proxyUrl == null || proxyUrl.contains("#") || width <= 0 || height <= 0) {
			return proxyUrl;
		}
		double scale = Math.min(1.0, previewSize / (double) Math.max(width, height));
		long tw = Math.max(1, Math.round(width * scale));
		long th = Math.max(1, Math.round(height * scale));
		int q = proxyUrl.indexOf('?');
		String base = q < 0 ? proxyUrl : proxyUrl.substring(0, q);
		List<String> kept = new ArrayList<>();
		if (q >= 0) {
			for (String param : proxyUrl.substring(q + 1).split("&")) {
				if (!param.isEmpty() && !param.startsWith("width=") && !param.startsWith("height=")) {
					kept.add(param);
				}
			}
		}
		kept.add("width=" + tw);
		kept.add("height=" + th);
		return base + "?" + String.join("&", kept);
	}

	public static boolean mayGainEmbeds(@Nullable String content, int embedCount, int attachmentCount) {
		return embedCount == 0 && attachmentCount == 0 && content != null && LINK.matcher(content).find();
	}

	/** True if any link in content is on an allowed media host, so its embed is worth a longer wait. */
	public static boolean linksAllowedMedia(@Nullable String content, Policy policy) {
		if (content == null) {
			return false;
		}
		Matcher m = LINK.matcher(content);
		while (m.find()) {
			if (policy.allows(m.group())) {
				return true;
			}
		}
		return false;
	}
}
