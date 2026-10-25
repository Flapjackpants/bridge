package io.github.yourname.discordbridge.media;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.jspecify.annotations.Nullable;

public final class MediaDownloader {
	private static final int MAX_REDIRECTS = 3;

	private final MediaPipeline.Policy policy;
	private final Duration timeout;
	private final HttpClient client;

	public MediaDownloader(MediaPipeline.Policy policy, Duration timeout) {
		this.policy = policy;
		this.timeout = timeout;
		this.client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(timeout).build();
	}

	public byte[] fetch(String url) throws IOException {
		if (!policy.allows(url)) {
			throw new IOException("not allowed");
		}
		String current = url;
		for (int hop = 0; hop <= MAX_REDIRECTS + 1; hop++) {
			HttpResponse<InputStream> res;
			try {
				HttpRequest req = HttpRequest.newBuilder(URI.create(current)).GET().timeout(timeout)
						.header("User-Agent", "discordbridge").build();
				res = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new InterruptedIOException("interrupted");
			} catch (IllegalArgumentException e) {
				throw new IOException("not allowed", e);
			}
			try (InputStream body = res.body()) {
				int code = res.statusCode();
				if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
					if (hop >= MAX_REDIRECTS) {
						throw new IOException("too many redirects");
					}
					String next = resolveRedirect(current, res.headers().firstValue("location").orElse(null), policy);
					if (next == null) {
						throw new IOException("redirect not allowed");
					}
					current = next;
					continue;
				}
				if (code != 200) {
					throw new IOException("HTTP " + code);
				}
				long len = res.headers().firstValueAsLong("content-length").orElse(-1);
				if (len > policy.maxBytes()) {
					throw new IOException("too large");
				}
				return readLimited(body, policy.maxBytes());
			}
		}
		throw new IOException("too many redirects");
	}

	static @Nullable String resolveRedirect(String base, @Nullable String location, MediaPipeline.Policy policy) {
		if (location == null) {
			return null;
		}
		try {
			String next = URI.create(base).resolve(location).toString();
			return policy.allows(next) ? next : null;
		} catch (RuntimeException e) {
			return null;
		}
	}

	static byte[] readLimited(InputStream in, long max) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buf = new byte[8192];
		long total = 0;
		int n;
		while ((n = in.read(buf)) != -1) {
			total += n;
			if (total > max) {
				throw new IOException("too large");
			}
			out.write(buf, 0, n);
		}
		return out.toByteArray();
	}
}
