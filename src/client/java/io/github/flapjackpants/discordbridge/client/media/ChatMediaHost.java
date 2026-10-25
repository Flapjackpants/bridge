package io.github.flapjackpants.discordbridge.client.media;

public interface ChatMediaHost {
	/** Rebuilds chat rows (refreshTrimmedMessages) keeping chatScrollbarPos (clamped). Render thread. */
	void discordbridge$refreshKeepScroll();
}
