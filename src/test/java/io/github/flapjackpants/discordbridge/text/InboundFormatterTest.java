package io.github.flapjackpants.discordbridge.text;

import static org.junit.jupiter.api.Assertions.*;

import io.github.flapjackpants.discordbridge.config.BridgeConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class InboundFormatterTest {
	private final BridgeConfig cfg = new BridgeConfig();

	private static InboundMessage msg(String content, String reply, List<AttachmentRef> att, List<String> stickers) {
		return new InboundMessage("Alice", "alice_u", content, reply, att, stickers, "https://discord.com/channels/1/2/3");
	}

	@Test
	void skipsEmpty() {
		assertTrue(InboundFormatter.format(msg("  ", null, List.of(), List.of()), cfg).isEmpty());
	}

	@Test
	void attachmentOnly() {
		List<Segment> s = InboundFormatter.format(
				msg("", null, List.of(new AttachmentRef("a.png", "https://cdn/a.png", true)), List.of()), cfg);
		assertEquals(List.of(Role.PREFIX, Role.NAME, Role.ATTACHMENT), s.stream().map(Segment::role).toList());
		Segment a = s.get(2);
		assertEquals(" [image]", a.text());
		assertEquals("https://cdn/a.png", a.clickUrl());
	}

	@Test
	void replyPrefix() {
		List<Segment> s = InboundFormatter.format(msg("hi", "Bob", List.of(), List.of()), cfg);
		assertEquals(List.of(Role.PREFIX, Role.REPLY, Role.NAME, Role.BODY), s.stream().map(Segment::role).toList());
		assertEquals("↪ Bob ", s.get(1).text());
		assertEquals(" hi", s.get(3).text());
	}

	@Test
	void defaultPrefix() {
		Segment first = InboundFormatter.format(msg("hi", null, List.of(), List.of()), cfg).get(0);
		assertEquals(Role.PREFIX, first.role());
		assertEquals("[Discord] ", first.text());
	}

	@Test
	void fileLabel() {
		List<Segment> s = InboundFormatter.format(
				msg("x", null, List.of(new AttachmentRef("notes.txt", "https://cdn/n", false)), List.of()), cfg);
		assertEquals(" [file: notes.txt]", s.get(s.size() - 1).text());
	}

	@Test
	void nameAttribution() {
		Segment name = InboundFormatter.format(msg("hi", null, List.of(), List.of()), cfg).get(1);
		assertEquals(Role.NAME, name.role());
		assertEquals("<Alice>", name.text());
		assertEquals("Sent from Discord by @alice_u", name.hoverText());
		assertEquals("https://discord.com/channels/1/2/3", name.clickUrl());
	}

	@Test
	void stickerLabel() {
		List<Segment> s = InboundFormatter.format(msg("", null, List.of(), List.of("wave")), cfg);
		Segment st = s.get(s.size() - 1);
		assertEquals(" [sticker: wave]", st.text());
		assertNull(st.clickUrl());
		assertNull(st.hoverText());
	}

	@Test
	void stripsLegacyFromNames() {
		InboundMessage m = new InboundMessage("§cAl", "u", "hi", "§aBo", List.of(), List.of(), "j");
		List<Segment> s = InboundFormatter.format(m, cfg);
		assertEquals("<Al>", s.get(2).text());
		assertEquals("↪ Bo ", s.get(1).text());
	}
}
