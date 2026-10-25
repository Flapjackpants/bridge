package io.github.yourname.discordbridge.text;

import net.dv8tion.jda.api.utils.MarkdownSanitizer;

public final class TextSanitizer {
	private static final int DISCORD_LIMIT = 2000;
	private static final String ELLIPSIS = "…";

	private TextSanitizer() {}

	/** Removes every section sign and the single character after it. */
	public static String stripLegacyFormatting(String s) {
		if (s == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '§') {
				i++;
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	public static String forDiscord(String mcText) {
		String s = MarkdownSanitizer.escape(stripLegacyFormatting(mcText));
		s = s.replace("@everyone", "@​everyone").replace("@here", "@​here");
		return truncate(s, DISCORD_LIMIT);
	}

	public static String forMinecraft(String text, int maxLen, int maxLines) {
		String s = stripLegacyFormatting(text);
		StringBuilder clean = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '\n' || !Character.isISOControl(c)) {
				clean.append(c);
			}
		}
		s = clean.toString().trim();
		String[] lines = s.split("\n", -1);
		if (lines.length > maxLines) {
			s = String.join("\n", java.util.Arrays.copyOf(lines, maxLines)) + ELLIPSIS;
		}
		return truncate(s, maxLen).trim();
	}

	private static String truncate(String s, int limit) {
		if (s.length() <= limit) {
			return s;
		}
		int end = limit - ELLIPSIS.length();
		if (end > 0 && Character.isHighSurrogate(s.charAt(end - 1))) {
			end--;
		}
		return s.substring(0, Math.max(end, 0)) + ELLIPSIS;
	}
}
