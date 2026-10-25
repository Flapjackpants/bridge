package io.github.yourname.discordbridge.media;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

public final class MediaFetchPlan {
	private static final Pattern ATTACHMENT = Pattern.compile("^/attachments/(\\d+)/(\\d+)/[^/]+$");
	private static final Set<String> DECODABLE = Set.of("image/png", "image/jpeg", "image/gif");

	private MediaFetchPlan() {}

	public record Source(String url, String cacheKey, boolean animated) {}

	/** Ordered fallbacks; every url passes policy.allows. Empty if none. Never throws. */
	public static List<Source> plan(MediaRef ref, MediaPipeline.Policy policy) {
		try {
			String proxyPng = ref.proxyUrl() == null ? null : withPngFormat(ref.proxyUrl());
			String url = ref.url();
			List<Source> cand = new ArrayList<>();
			switch (ref.kind()) {
				case STATIC -> {
					add(cand, proxyPng, false);
					if (DECODABLE.contains(ref.contentType())) {
						add(cand, url, false);
					}
				}
				case GIF -> {
					if (ref.sizeBytes() <= 0 || ref.sizeBytes() <= policy.maxBytes()) {
						add(cand, url, true);
					}
					add(cand, proxyPng, false);
				}
				case GIFV -> {
					if (ref.contentType().equals("image/gif")) {
						add(cand, url, true);
						add(cand, proxyPng, false);
					} else {
						add(cand, proxyPng, false);
						add(cand, url, false);
					}
				}
				case VIDEO -> add(cand, proxyPng, false);
			}
			List<Source> out = new ArrayList<>();
			Set<String> seen = new LinkedHashSet<>();
			for (Source s : cand) {
				if (policy.allows(s.url()) && seen.add(s.url())) {
					out.add(s);
				}
			}
			return List.copyOf(out);
		} catch (RuntimeException e) {
			return List.of();
		}
	}

	private static void add(List<Source> out, @Nullable String url, boolean animated) {
		if (url != null) {
			out.add(new Source(url, cacheKey(url), animated));
		}
	}

	/** Matches [a-z0-9-]{1,80}. Never throws. */
	public static String cacheKey(String url) {
		String key;
		try {
			key = attachmentKey(url);
		} catch (Exception e) {
			key = null;
		}
		if (key == null) {
			key = hashKey(url);
		}
		key = key.replaceAll("[^a-z0-9-]", "");
		if (key.length() > 80) {
			key = key.substring(0, 80);
		}
		return key.isEmpty() ? hashKey(url) : key;
	}

	private static @Nullable String attachmentKey(String url) throws Exception {
		URI uri = new URI(url);
		String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
		boolean cdn = host.equals("cdn.discordapp.com");
		if (!cdn && !host.equals("media.discordapp.net")) {
			return null;
		}
		Matcher m = ATTACHMENT.matcher(uri.getPath() == null ? "" : uri.getPath());
		if (!m.matches()) {
			return null;
		}
		String id = m.group(2);
		if (cdn) {
			return "a" + id + "-o";
		}
		return "a" + id + "-p" + queryValue(uri, "width") + "x" + queryValue(uri, "height") + queryValue(uri, "format");
	}

	private static String queryValue(URI uri, String name) {
		String q = uri.getRawQuery();
		if (q != null) {
			for (String param : q.split("&")) {
				if (param.startsWith(name + "=")) {
					return param.substring(name.length() + 1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
				}
			}
		}
		return "";
	}

	private static String hashKey(String url) {
		try {
			byte[] d = MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8));
			return "u" + HexFormat.of().formatHex(d).substring(0, 40);
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Sets/replaces query param format=png on *.discordapp.net / media.discordapp.net URLs; others unchanged. */
	static String withPngFormat(String url) {
		try {
			URI uri = new URI(url);
			String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
			if (!host.equals("media.discordapp.net") && !host.endsWith(".discordapp.net")) {
				return url;
			}
			int hash = url.indexOf('#');
			String fragment = hash < 0 ? "" : url.substring(hash);
			String noFrag = hash < 0 ? url : url.substring(0, hash);
			int q = noFrag.indexOf('?');
			String base = q < 0 ? noFrag : noFrag.substring(0, q);
			List<String> kept = new ArrayList<>();
			if (q >= 0) {
				for (String param : noFrag.substring(q + 1).split("&")) {
					if (!param.isEmpty() && !param.startsWith("format=")) {
						kept.add(param);
					}
				}
			}
			kept.add("format=png");
			return base + "?" + String.join("&", kept) + fragment;
		} catch (Exception e) {
			return url;
		}
	}
}
