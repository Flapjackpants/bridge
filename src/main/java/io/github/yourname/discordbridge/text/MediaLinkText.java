package io.github.yourname.discordbridge.text;

import java.util.Map;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

public final class MediaLinkText {
	private MediaLinkText() {}

	/** Declaration order = priority: combine keeps the lower ordinal. */
	public enum LinkState { HIDE, TOO_LARGE, FAILED }

	public static LinkState combine(LinkState a, LinkState b) {
		return a.ordinal() <= b.ordinal() ? a : b;
	}

	/**
	 * Returns {@code content} itself (same instance) when states is empty, content has no siblings, or nothing matched.
	 * Never throws: on RuntimeException returns {@code content}.
	 * Suffixes are inserted as one sibling: Component.literal(" ").append(suffix).
	 */
	public static Component rewrite(Component content, Map<String, LinkState> states,
			Component failedSuffix, Component tooLargeSuffix) {
		try {
			if (states.isEmpty() || content.getSiblings().isEmpty()) {
				return content;
			}
			MutableComponent out = content.plainCopy().setStyle(content.getStyle());
			boolean changed = false;
			for (Component kid : content.getSiblings()) {
				if (!kid.getSiblings().isEmpty()) {
					out.append(kid);
				} else if (kid.getStyle().getClickEvent() instanceof ClickEvent.OpenUrl open
						&& states.get(open.uri().toString()) != null) {
					LinkState state = states.get(open.uri().toString());
					if (state != LinkState.HIDE) {
						out.append(kid);
						out.append(suffix(state, failedSuffix, tooLargeSuffix));
					}
					changed = true;
				} else {
					changed |= rewriteBody(kid, states, failedSuffix, tooLargeSuffix, out);
				}
			}
			return changed ? out : content;
		} catch (RuntimeException e) {
			return content;
		}
	}

	private static Component suffix(LinkState state, Component failedSuffix, Component tooLargeSuffix) {
		return Component.literal(" ").append(state == LinkState.TOO_LARGE ? tooLargeSuffix : failedSuffix);
	}

	/** Appends the rewritten form of a leaf sibling to out; returns whether anything matched. */
	private static boolean rewriteBody(Component kid, Map<String, LinkState> states, Component failedSuffix,
			Component tooLargeSuffix, MutableComponent out) {
		String text = kid.getString();
		Style style = kid.getStyle();
		MutableComponent parts = Component.empty();
		StringBuilder piece = new StringBuilder();
		boolean matched = false;
		int pos = 0;
		while (pos < text.length()) {
			int bestIdx = -1;
			String bestKey = null;
			for (String key : states.keySet()) {
				int idx = find(text, key, pos);
				if (idx >= 0 && (bestIdx < 0 || idx < bestIdx)) {
					bestIdx = idx;
					bestKey = key;
				}
			}
			if (bestKey == null) {
				break;
			}
			piece.append(text, pos, bestIdx);
			int end = bestIdx + bestKey.length();
			LinkState state = states.get(bestKey);
			if (state == LinkState.HIDE) {
				if (!piece.isEmpty() && piece.charAt(piece.length() - 1) == ' ') {
					piece.deleteCharAt(piece.length() - 1);
				} else if (end < text.length() && text.charAt(end) == ' ') {
					end++;
				}
			} else {
				piece.append(bestKey);
				parts.append(Component.literal(piece.toString()).withStyle(style));
				parts.append(suffix(state, failedSuffix, tooLargeSuffix));
				piece.setLength(0);
			}
			matched = true;
			pos = end;
		}
		if (!matched) {
			out.append(kid);
			return false;
		}
		piece.append(text, pos, text.length());
		if (!piece.toString().isBlank()) {
			parts.append(Component.literal(piece.toString()).withStyle(style));
		}
		for (Component part : parts.getSiblings()) {
			out.append(part);
		}
		return true;
	}

	/** First occurrence of key at or after from whose neighbours (if any) are whitespace; -1 if none. */
	private static int find(String text, String key, int from) {
		if (key.isBlank()) {
			return -1;
		}
		int idx = text.indexOf(key, from);
		while (idx >= 0) {
			int end = idx + key.length();
			boolean startOk = idx == 0 || Character.isWhitespace(text.charAt(idx - 1));
			boolean endOk = end >= text.length() || Character.isWhitespace(text.charAt(end));
			if (startOk && endOk) {
				return idx;
			}
			idx = text.indexOf(key, idx + 1);
		}
		return -1;
	}
}
