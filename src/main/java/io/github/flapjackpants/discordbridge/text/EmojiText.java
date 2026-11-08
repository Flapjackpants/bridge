package io.github.flapjackpants.discordbridge.text;

import java.util.List;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

public final class EmojiText {
	private EmojiText() {}

	/**
	 * Replaces EMOJI siblings in order. Returns {@code content} itself when emojis is empty, content has no
	 * siblings, nothing was replaced, or on RuntimeException.
	 */
	public static Component rewrite(Component content, List<EmojiRef> emojis,
			Function<EmojiRef, @Nullable Component> make) {
		try {
			if (emojis.isEmpty() || content.getSiblings().isEmpty()) {
				return content;
			}
			MutableComponent out = content.plainCopy().setStyle(content.getStyle());
			boolean changed = false;
			int i = 0;
			for (Component kid : content.getSiblings()) {
				EmojiRef emoji = i < emojis.size() ? emojis.get(i) : null;
				if (emoji != null && kid.getSiblings().isEmpty() && kid.getStyle().getClickEvent() == null
						&& kid.getString().equals(emoji.text())) {
					Component replacement = make.apply(emoji);
					out.append(replacement == null ? kid : replacement);
					changed |= replacement != null;
					i++;
				} else {
					out.append(kid);
				}
			}
			return changed ? out : content;
		} catch (RuntimeException e) {
			return content;
		}
	}
}
