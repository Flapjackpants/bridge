package io.github.yourname.discordbridge.media;

import org.jspecify.annotations.Nullable;

public record MediaRef(Kind kind, @Nullable String url, @Nullable String proxyUrl,
		int width, int height, long sizeBytes, String contentType, String fileName) {
	/** Wire format is the ordinal: append-only; reordering requires a protocol VERSION bump. */
	public enum Kind { STATIC, GIF, GIFV, VIDEO }

	public MediaRef {
		width = Math.max(width, 0);
		height = Math.max(height, 0);
		sizeBytes = sizeBytes <= 0 ? -1 : sizeBytes;
		contentType = contentType == null ? "" : contentType;
		fileName = fileName == null ? "" : fileName;
	}
}
