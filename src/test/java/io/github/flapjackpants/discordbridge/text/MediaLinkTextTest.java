package io.github.flapjackpants.discordbridge.text;

import static org.junit.jupiter.api.Assertions.*;

import io.github.flapjackpants.discordbridge.text.MediaLinkText.LinkState;
import java.net.URI;
import java.util.Map;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.Test;

class MediaLinkTextTest {
	private static final Component F = Component.literal("(F)");
	private static final Component L = Component.literal("(L)");

	private static MutableComponent root(Component... kids) {
		MutableComponent root = Component.literal("");
		for (Component k : kids) {
			root.append(k);
		}
		return root;
	}

	private static Component attach(String text, String url) {
		return Component.literal(text).withStyle(Style.EMPTY.withClickEvent(new ClickEvent.OpenUrl(URI.create(url))));
	}

	private static Component rewrite(Component c, Map<String, LinkState> states) {
		return MediaLinkText.rewrite(c, states, F, L);
	}

	@Test
	void hidesWholeBodyLink() {
		Component c = root(Component.literal("[D] "), Component.literal("<A>"),
				Component.literal(" https://tenor.com/view/x"));
		Component out = rewrite(c, Map.of("https://tenor.com/view/x", LinkState.HIDE));
		assertEquals("[D] <A>", out.getString());
		assertEquals(2, out.getSiblings().size());
	}

	@Test
	void hidesLinkMidTextKeepsStyle() {
		Style blue = Style.EMPTY.withColor(TextColor.fromRgb(0x0000FF));
		Component c = root(Component.literal("<A>"), Component.literal(" look https://t.co/a cool").withStyle(blue));
		Component out = rewrite(c, Map.of("https://t.co/a", LinkState.HIDE));
		assertEquals("<A> look cool", out.getString());
		assertEquals(blue, out.getSiblings().get(1).getStyle());
	}

	@Test
	void failedAppendsSuffix() {
		Component c = root(Component.literal("<A>"), Component.literal(" https://tenor.com/view/x"));
		Component out = rewrite(c, Map.of("https://tenor.com/view/x", LinkState.FAILED));
		assertEquals("<A> https://tenor.com/view/x (F)", out.getString());
	}

	@Test
	void tooLargeSuffixBeforeRemainder() {
		Component c = root(Component.literal("<A>"), Component.literal(" a https://x.com/p.gif b"));
		Component out = rewrite(c, Map.of("https://x.com/p.gif", LinkState.TOO_LARGE));
		assertEquals("<A> a https://x.com/p.gif (L) b", out.getString());
	}

	@Test
	void hidesAttachmentSegment() {
		Component c = root(Component.literal("<A>"), attach(" [image]", "https://cdn/a.png"),
				attach(" [image]", "https://cdn/b.png"));
		Component out = rewrite(c, Map.of("https://cdn/a.png", LinkState.HIDE));
		assertEquals("<A> [image]", out.getString());
		assertEquals(2, out.getSiblings().size());
		ClickEvent.OpenUrl open = (ClickEvent.OpenUrl) out.getSiblings().get(1).getStyle().getClickEvent();
		assertEquals("https://cdn/b.png", open.uri().toString());
	}

	@Test
	void attachmentFailedKeepsClick() {
		Component c = root(Component.literal("<A>"), attach(" [image]", "https://cdn/a.png"));
		Component out = rewrite(c, Map.of("https://cdn/a.png", LinkState.FAILED));
		assertEquals("<A> [image] (F)", out.getString());
		assertInstanceOf(ClickEvent.OpenUrl.class, out.getSiblings().get(1).getStyle().getClickEvent());
	}

	@Test
	void partialUrlNotMatched() {
		Component c = root(Component.literal("<A>"), Component.literal(" https://x.com/p.gifv"));
		assertSame(c, rewrite(c, Map.of("https://x.com/p.gif", LinkState.HIDE)));
	}

	@Test
	void noopReturnsSameInstance() {
		Component c = root(Component.literal("<A>"), Component.literal(" hi"));
		assertSame(c, rewrite(c, Map.of()));
		Component plain = Component.literal("https://x.com/p.gif");
		assertSame(plain, rewrite(plain, Map.of("https://x.com/p.gif", LinkState.HIDE)));
		assertSame(c, rewrite(c, Map.of("https://other", LinkState.HIDE)));
	}

	@Test
	void combinePriority() {
		for (LinkState s : LinkState.values()) {
			assertEquals(LinkState.HIDE, MediaLinkText.combine(LinkState.HIDE, s));
			assertEquals(LinkState.HIDE, MediaLinkText.combine(s, LinkState.HIDE));
		}
		assertEquals(LinkState.TOO_LARGE, MediaLinkText.combine(LinkState.TOO_LARGE, LinkState.FAILED));
		assertEquals(LinkState.TOO_LARGE, MediaLinkText.combine(LinkState.FAILED, LinkState.TOO_LARGE));
	}
}
