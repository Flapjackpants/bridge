package io.github.flapjackpants.discordbridge.text;

public enum Emphasis {
	BOLD_ITALIC("***"), ITALIC("*");

	private final String marker;

	Emphasis(String marker) {
		this.marker = marker;
	}

	public String marker() {
		return marker;
	}
}
