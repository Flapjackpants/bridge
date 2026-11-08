package io.github.flapjackpants.discordbridge.text;

import static org.junit.jupiter.api.Assertions.*;

import io.github.flapjackpants.discordbridge.text.Emojis.CustomTag;
import io.github.flapjackpants.discordbridge.text.Emojis.Piece;
import java.util.List;
import org.junit.jupiter.api.Test;

class EmojisTest {
	private static final String DISCORD = "https://cdn.discordapp.com/emojis/";
	private static final String TWEMOJI = "https://cdn.jsdelivr.net/gh/jdecked/twemoji@16.0.1/assets/72x72/";

	private static String joined(List<Piece> pieces) {
		StringBuilder sb = new StringBuilder();
		for (Piece p : pieces) {
			sb.append(p.text());
		}
		return sb.toString();
	}

	private static EmojiRef onlyEmoji(String body) {
		List<Piece> pieces = Emojis.split(body, List.of());
		assertEquals(1, pieces.size(), body);
		assertEquals(body, pieces.get(0).text(), body);
		assertNotNull(pieces.get(0).emoji(), body);
		return pieces.get(0).emoji();
	}

	private static void assertText(String body) {
		List<Piece> pieces = Emojis.split(body, List.of());
		assertEquals(1, pieces.size(), body);
		assertEquals(body, pieces.get(0).text(), body);
		assertNull(pieces.get(0).emoji(), body);
	}

	@Test
	void parsesCustomTags() {
		assertEquals(List.of(new CustomTag("smile", "123", false), new CustomTag("dance", "456", true)),
				Emojis.parseCustomTags("hi <:smile:123> <a:dance:456>"));
		assertTrue(Emojis.parseCustomTags(null).isEmpty());
		assertTrue(Emojis.parseCustomTags("<:bad name:1>").isEmpty());
	}

	@Test
	void splitsCustomEmoji() {
		assertEquals(List.of(
				new Piece("hi ", null),
				new Piece(":smile:", new EmojiRef(":smile:", DISCORD + "123.png?size=48", false)),
				new Piece(" there", null)),
				Emojis.split("hi :smile: there", List.of(new CustomTag("smile", "123", false))));
	}

	@Test
	void animatedCustomEmoji() {
		EmojiRef ref = Emojis.split(":dance:", List.of(new CustomTag("dance", "456", true))).get(0).emoji();
		assertNotNull(ref);
		assertEquals(DISCORD + "456.gif?size=48", ref.url());
		assertTrue(ref.animated());
	}

	@Test
	void sameNameTwoTagsMatchInOrder() {
		List<Piece> pieces = Emojis.split("a :x: b :x:",
				List.of(new CustomTag("x", "1", false), new CustomTag("x", "2", false)));
		List<EmojiRef> emojis = pieces.stream().map(Piece::emoji).filter(e -> e != null).toList();
		assertEquals(2, emojis.size());
		assertEquals(DISCORD + "1.png?size=48", emojis.get(0).url());
		assertEquals(DISCORD + "2.png?size=48", emojis.get(1).url());
	}

	@Test
	void absentTagSkippedWithoutMovingCursor() {
		List<Piece> pieces = Emojis.split(":x:",
				List.of(new CustomTag("y", "9", false), new CustomTag("x", "1", false)));
		assertEquals(1, pieces.size());
		assertEquals(DISCORD + "1.png?size=48", pieces.get(0).emoji().url());
	}

	@Test
	void literalTagTextWithoutTags() {
		List<Piece> pieces = Emojis.split(":x:", List.of());
		assertEquals(List.of(new Piece(":x:", null)), pieces);
	}

	@Test
	void splitsUnicodeEmoji() {
		assertEquals(List.of(
				new Piece("a", null),
				new Piece("😀", new EmojiRef("😀", TWEMOJI + "1f600.png", false)),
				new Piece("b", null)),
				Emojis.split("a😀b", List.of()));
	}

	@Test
	void twemojiNames() {
		assertEquals(TWEMOJI + "2764.png", onlyEmoji("❤️").url());
		assertEquals(TWEMOJI + "31-20e3.png", onlyEmoji("1️⃣").url());
		assertEquals(TWEMOJI + "1f44d-1f3fd.png", onlyEmoji("👍🏽").url());
		assertEquals(TWEMOJI + "1f1fa-1f1f8.png", onlyEmoji("🇺🇸").url());
		assertEquals(TWEMOJI + "1f468-200d-1f469-200d-1f467.png", onlyEmoji("👨‍👩‍👧").url());
		assertEquals(TWEMOJI + "1f3f3-fe0f-200d-1f308.png", onlyEmoji("🏳️‍🌈").url());
	}

	@Test
	void notEmoji() {
		assertText("❤");
		assertText("©");
		assertText("123");
		assertText("#");
		assertText("\uD83C\uDDFA");
	}

	@Test
	void invariantJoinsAndCounts() {
		String body = "x :a: 😀 :a: ❤️ y";
		List<Piece> pieces = Emojis.split(body, List.of(new CustomTag("a", "1", false)));
		assertEquals(body, joined(pieces));
		assertEquals(3, pieces.stream().filter(p -> p.emoji() != null).count());
		List<Piece> texts = pieces.stream().filter(p -> p.emoji() == null).toList();
		assertTrue(texts.stream().anyMatch(p -> p.text().contains(":a:")), "second :a: stays text");
		for (Piece p : pieces) {
			assertFalse(p.text().isEmpty());
		}
	}

	@Test
	void capsEmojiCount() {
		String body = "😀".repeat(70);
		List<Piece> pieces = Emojis.split(body, List.of());
		assertEquals(65, pieces.size());
		assertEquals(64, pieces.stream().filter(p -> p.emoji() != null).count());
		Piece tail = pieces.get(64);
		assertNull(tail.emoji());
		assertEquals("😀".repeat(6), tail.text());
	}

	@Test
	void emptyBodies() {
		assertTrue(Emojis.split("", List.of()).isEmpty());
		assertTrue(Emojis.split(null, List.of()).isEmpty());
	}

	@Test
	void trustedUrls() {
		assertTrue(Emojis.isTrustedUrl(Emojis.customUrl("123", true)));
		assertTrue(Emojis.isTrustedUrl(Emojis.customUrl("123", false)));
		assertTrue(Emojis.isTrustedUrl(Emojis.twemojiUrl("😀")));
		assertTrue(Emojis.isTrustedUrl(Emojis.twemojiUrl("👨‍👩‍👧")));
		assertFalse(Emojis.isTrustedUrl(null));
		assertFalse(Emojis.isTrustedUrl("https://evil.com/emojis/1.png?size=48"));
		assertFalse(Emojis.isTrustedUrl("http://cdn.discordapp.com/emojis/1.png?size=48"));
		assertFalse(Emojis.isTrustedUrl("https://cdn.discordapp.com/emojis/1.png"));
		assertFalse(Emojis.isTrustedUrl(
				"https://cdn.jsdelivr.net/gh/jdecked/twemoji@15.0.0/assets/72x72/1f600.png"));
	}
}
