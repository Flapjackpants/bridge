package io.github.flapjackpants.discordbridge.text;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TextSanitizerTest {
	@Test
	void stripsLegacyCodes() {
		assertEquals("red", TextSanitizer.forDiscord("§cred"));
		assertEquals("ab", TextSanitizer.stripLegacyFormatting("a§lb"));
	}

	@Test
	void neutralizesMassMentions() {
		assertEquals("@​everyone hi", TextSanitizer.forDiscord("@everyone hi"));
		assertEquals("@​here", TextSanitizer.forDiscord("@here"));
	}

	@Test
	void escapesMarkdown() {
		assertEquals("\\*bold\\*", TextSanitizer.forDiscord("*bold*"));
	}

	@Test
	void truncatesForDiscord() {
		String out = TextSanitizer.forDiscord("a".repeat(2500));
		assertTrue(out.length() <= 2000);
		assertTrue(out.endsWith("…"));
	}

	@Test
	void eventBoldItalic() {
		assertEquals("***Server started***", TextSanitizer.forDiscordEvent("Server started", Emphasis.BOLD_ITALIC));
	}

	@Test
	void eventEscapesInner() {
		assertEquals("*\\*a\\* joined*", TextSanitizer.forDiscordEvent("*a* joined", Emphasis.ITALIC));
	}

	@Test
	void eventBlank() {
		assertEquals("", TextSanitizer.forDiscordEvent("  ", Emphasis.ITALIC));
		assertEquals("", TextSanitizer.forDiscordEvent(null, Emphasis.ITALIC));
	}

	@Test
	void eventTrims() {
		assertEquals("*X died*", TextSanitizer.forDiscordEvent("  X died ", Emphasis.ITALIC));
	}

	@Test
	void eventStripsLegacy() {
		assertEquals("*X died*", TextSanitizer.forDiscordEvent("§cX died", Emphasis.ITALIC));
	}

	@Test
	void eventNeutralizesMentions() {
		assertTrue(TextSanitizer.forDiscordEvent("@everyone died", Emphasis.ITALIC).contains("@\u200Beveryone"));
	}

	@Test
	void eventTruncatesInsideMarkers() {
		String out = TextSanitizer.forDiscordEvent("a".repeat(2500), Emphasis.ITALIC);
		assertTrue(out.length() <= 2000);
		assertTrue(out.startsWith("*"));
		assertTrue(out.endsWith("…*"));
	}

	@Test
	void forMinecraftStripsLegacy() {
		assertEquals("hello", TextSanitizer.forMinecraft("§4hello", 100, 5));
	}

	@Test
	void limitsLines() {
		String in = String.join("\n", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
		String out = TextSanitizer.forMinecraft(in, 1000, 5);
		assertEquals("1\n2\n3\n4\n5…", out);
	}

	@Test
	void removesControlChars() {
		assertEquals("ab\ncd", TextSanitizer.forMinecraft("a\u0007b\ncd", 100, 5));
	}

	@Test
	void truncatesForMinecraftLength() {
		String out = TextSanitizer.forMinecraft("x".repeat(100), 10, 5);
		assertEquals(10, out.length());
		assertTrue(out.endsWith("…"));
	}
}
