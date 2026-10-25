package io.github.flapjackpants.discordbridge.text;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.Test;

class ComponentRendererTest {
	@Test
	void replacesFirstChild() {
		Style blue = Style.EMPTY.withColor(TextColor.fromRgb(0x0000FF));
		MutableComponent root = Component.literal("");
		root.append(Component.literal("[D] ").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x123456))));
		root.append(Component.literal("<A>").withStyle(blue));
		root.append(Component.literal(" hi"));
		Component out = ComponentRenderer.withPrefix(root, "[X] ", 0xFF0000);
		assertEquals("[X] <A> hi", out.getString());
		assertEquals(0xFF0000, out.getSiblings().get(0).getStyle().getColor().getValue());
		assertEquals(blue, out.getSiblings().get(1).getStyle());
	}

	@Test
	void noChildrenUnchanged() {
		Component c = Component.literal("plain");
		assertSame(c, ComponentRenderer.withPrefix(c, "[X] ", 0xFF0000));
	}
}
