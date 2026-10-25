package io.github.yourname.discordbridge.media;

import java.net.URI;
import java.net.URISyntaxException;
import org.jspecify.annotations.Nullable;

public final class MediaLinks {
	public static final int MAX_LABEL = 64;

	private MediaLinks() {}

	/** fileName if non-blank, else host of url, else host of proxyUrl, else "". Truncated to MAX_LABEL chars. */
	public static String label(MediaRef ref) {
		String s = ref.fileName();
		if (s.isBlank()) {
			s = host(ref.url());
		}
		if (s.isEmpty()) {
			s = host(ref.proxyUrl());
		}
		return s.length() > MAX_LABEL ? s.substring(0, MAX_LABEL) : s;
	}

	/**
	 * VIDEO: linkUrl first when it is an absolute http/https URI with a host and no user-info (host allowlist NOT applied:
	 * it is the link the sender posted, already clickable in chat). Then, for every kind: first of url, proxyUrl that
	 * policy allows. Null if none. Never throws.
	 */
	public static @Nullable URI openUri(MediaRef ref, MediaPipeline.Policy policy) {
		if (ref.kind() == MediaRef.Kind.VIDEO) {
			URI link = webUri(ref.linkUrl());
			if (link != null) {
				return link;
			}
		}
		for (String s : new String[] {ref.url(), ref.proxyUrl()}) {
			if (s != null && policy.allows(s)) {
				try {
					return new URI(s);
				} catch (URISyntaxException e) {
					// try next
				}
			}
		}
		return null;
	}

	/** Absolute http/https URI with non-null host and null user-info; else null. Never throws. */
	private static @Nullable URI webUri(@Nullable String s) {
		if (s == null) {
			return null;
		}
		try {
			URI u = new URI(s);
			String scheme = u.getScheme();
			boolean web = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
			return web && u.getHost() != null && u.getUserInfo() == null ? u : null;
		} catch (URISyntaxException e) {
			return null;
		}
	}

	private static String host(@Nullable String s) {
		if (s == null) {
			return "";
		}
		try {
			String h = new URI(s).getHost();
			return h == null ? "" : h;
		} catch (URISyntaxException e) {
			return "";
		}
	}
}
