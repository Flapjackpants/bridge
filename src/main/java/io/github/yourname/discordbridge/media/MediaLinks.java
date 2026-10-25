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

	/** First of url, proxyUrl that policy allows, as a URI; null if none. */
	public static @Nullable URI openUri(MediaRef ref, MediaPipeline.Policy policy) {
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
