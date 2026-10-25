package io.github.flapjackpants.discordbridge.text;

import io.github.flapjackpants.discordbridge.config.BridgeConfig;
import java.net.URI;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

public final class ComponentRenderer {
	private ComponentRenderer() {}

	public static Component render(List<Segment> segments, BridgeConfig cfg) {
		MutableComponent root = Component.literal("");
		for (Segment seg : segments) {
			Style style = switch (seg.role()) {
				case PREFIX -> Style.EMPTY.withColor(TextColor.fromRgb(BridgeConfig.parseHexColor(cfg.prefixColor, 0x5865F2)));
				case NAME -> Style.EMPTY.withColor(TextColor.fromRgb(BridgeConfig.parseHexColor(cfg.nameColor, 0x7289DA)));
				case BODY -> Style.EMPTY.withColor(TextColor.fromRgb(BridgeConfig.parseHexColor(cfg.messageColor, 0xFFFFFF)));
				case REPLY -> Style.EMPTY.withColor(ChatFormatting.GRAY);
				case ATTACHMENT -> Style.EMPTY.withColor(ChatFormatting.AQUA).withUnderlined(true);
			};
			if (seg.clickUrl() != null) {
				try {
					style = style.withClickEvent(new ClickEvent.OpenUrl(URI.create(seg.clickUrl())));
				} catch (IllegalArgumentException ignored) {
					// invalid URL: omit the click
				}
			}
			if (seg.hoverText() != null) {
				style = style.withHoverEvent(new HoverEvent.ShowText(Component.literal(seg.hoverText())));
			}
			root.append(Component.literal(seg.text()).withStyle(style));
		}
		return root;
	}

	public static Component withPrefix(Component rendered, String prefix, int rgb) {
		try {
			List<Component> kids = rendered.getSiblings();
			if (kids.isEmpty()) {
				return rendered;
			}
			MutableComponent out = rendered.plainCopy().setStyle(rendered.getStyle());
			out.append(Component.literal(prefix).withStyle(kids.get(0).getStyle().withColor(rgb)));
			for (int i = 1; i < kids.size(); i++) {
				out.append(kids.get(i));
			}
			return out;
		} catch (RuntimeException e) {
			return rendered;
		}
	}
}
