package io.github.yourname.discordbridge.media;

import java.io.IOException;

public final class MediaTooLargeException extends IOException {
	@java.io.Serial
	private static final long serialVersionUID = 1L;

	public MediaTooLargeException(String message) {
		super(message);
	}
}
