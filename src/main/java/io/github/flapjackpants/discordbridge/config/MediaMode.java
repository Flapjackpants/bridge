package io.github.flapjackpants.discordbridge.config;

public enum MediaMode {
	OFF, STATIC, ANIMATED;

	/** OFF→STATIC→ANIMATED→OFF */
	public MediaMode next() {
		return values()[(ordinal() + 1) % values().length];
	}
}
