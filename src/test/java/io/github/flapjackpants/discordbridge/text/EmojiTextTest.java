package io.github.flapjackpants.discordbridge.text;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.util.List;
import java.util.function.Function;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

class EmojiTextTest {
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

	private static EmojiRef ref(String text) {
		return new EmojiRef(text, "u", false);
	}

	@Test
	void replacesEmojiSiblingInOrder() {
		Component c = root(Component.literal("[D] "), attach("<A>", "https://example.com"),
				Component.literal(" hi "), Component.literal(":x:"), Component.literal(" yo"));
		Component out = EmojiText.rewrite(c, List.of(ref(":x:")), e -> Component.literal("E"));
		assertEquals("[D] <A> hi E yo", out.getString());
		assertNotSame(c, out);
		assertEquals(5, out.getSiblings().size());
	}

	@Test
	void emptyEmojisReturnSameInstance() {
		Component c = root(Component.literal(" hi "), Component.literal(":x:"));
		assertSame(c, EmojiText.rewrite(c, List.of(), e -> Component.literal("E")));
	}

	@Test
	void nullReplacementKeepsTextAndAdvances() {
		Component c = root(Component.literal(" a "), Component.literal(":x:"), Component.literal(" "),
				Component.literal("😀"));
		Function<EmojiRef, Component> make = e -> e.text().equals(":x:") ? null : Component.literal("Y");
		Component out = EmojiText.rewrite(c, List.of(ref(":x:"), ref("😀")), make);
		assertEquals(" a :x: Y", out.getString());
	}

	@Test
	void clickableSiblingIsNotReplaced() {
		Component c = root(attach(":x:", "https://example.com"));
		assertSame(c, EmojiText.rewrite(c, List.of(ref(":x:")), e -> Component.literal("E")));
	}

	@Test
	void onlyFirstMatchingSiblingIsReplaced() {
		Component c = root(Component.literal(":x:"), Component.literal(":x:"));
		Component out = EmojiText.rewrite(c, List.of(ref(":x:")), e -> Component.literal("E"));
		assertEquals("E:x:", out.getString());
	}

	@Test
	void noMatchReturnsSameInstance() {
		Component c = root(Component.literal("a"), Component.literal("b"));
		assertSame(c, EmojiText.rewrite(c, List.of(ref(":x:")), e -> Component.literal("E")));
	}
}
