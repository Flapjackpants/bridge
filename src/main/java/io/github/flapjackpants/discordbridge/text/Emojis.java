package io.github.flapjackpants.discordbridge.text;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

public final class Emojis {
	public static final int MAX_EMOJIS = 64;

	private static final String CUSTOM_PREFIX = "https://cdn.discordapp.com/emojis/";
	private static final String TWEMOJI_PREFIX =
			"https://cdn.jsdelivr.net/gh/jdecked/twemoji@16.0.1/assets/72x72/";

	private static final Pattern CUSTOM_TAG = Pattern.compile("<(a?):([A-Za-z0-9_]{1,32}):(\\d{1,20})>");
	private static final Pattern GRAPHEME = Pattern.compile("\\X");
	private static final Pattern TRUSTED_CUSTOM = Pattern.compile(
			"^https://cdn\\.discordapp\\.com/emojis/\\d{1,20}\\.(png|gif)\\?size=48$");
	private static final Pattern TRUSTED_TWEMOJI = Pattern.compile(
			"^https://cdn\\.jsdelivr\\.net/gh/jdecked/twemoji@16\\.0\\.1/assets/72x72/[0-9a-f]{1,8}(-[0-9a-f]{1,8}){0,15}\\.png$");

	private static final int ZWJ = 0x200D;
	private static final int VS16 = 0xFE0F;
	private static final int KEYCAP = 0x20E3;

	private Emojis() {}

	public record CustomTag(String name, String id, boolean animated) {}

	public record Piece(String text, @Nullable EmojiRef emoji) {}

	private record Match(int start, int end, CustomTag tag) {}

	public static List<CustomTag> parseCustomTags(@Nullable String raw) {
		try {
			if (raw == null || raw.isBlank()) {
				return List.of();
			}
			Matcher matcher = CUSTOM_TAG.matcher(raw);
			List<CustomTag> tags = new ArrayList<>();
			while (matcher.find()) {
				tags.add(new CustomTag(matcher.group(2), matcher.group(3), "a".equals(matcher.group(1))));
			}
			return tags;
		} catch (RuntimeException e) {
			return List.of();
		}
	}

	public static List<Piece> split(@Nullable String body, List<CustomTag> tags) {
		try {
			if (body == null || body.isEmpty()) {
				return List.of();
			}
			List<Match> matches = findCustomMatches(body, tags);
			List<Piece> pieces = new ArrayList<>();
			StringBuilder text = new StringBuilder();
			int emojis = 0;
			int pos = 0;
			int next = 0;
			while (pos < body.length()) {
				if (next < matches.size() && pos == matches.get(next).start()) {
					Match match = matches.get(next);
					if (emojis < MAX_EMOJIS) {
						flush(pieces, text);
						String emojiText = body.substring(match.start(), match.end());
						pieces.add(new Piece(emojiText, new EmojiRef(emojiText,
								customUrl(match.tag().id(), match.tag().animated()), match.tag().animated())));
						emojis++;
					} else {
						text.append(body, match.start(), match.end());
					}
					pos = match.end();
					next++;
					continue;
				}
				int limit = next < matches.size() ? matches.get(next).start() : body.length();
				Matcher graphemes = GRAPHEME.matcher(body.substring(pos, limit));
				while (graphemes.find()) {
					String cluster = graphemes.group();
					if (emojis < MAX_EMOJIS && isEmojiCluster(cluster)) {
						flush(pieces, text);
						pieces.add(new Piece(cluster, new EmojiRef(cluster, twemojiUrl(cluster), false)));
						emojis++;
					} else {
						text.append(cluster);
					}
				}
				pos = limit;
			}
			flush(pieces, text);
			return pieces;
		} catch (RuntimeException e) {
			return List.of(new Piece(body, null));
		}
	}

	/** Custom matches in tag order; each search starts at the end of the previous match. */
	private static List<Match> findCustomMatches(String body, List<CustomTag> tags) {
		List<Match> matches = new ArrayList<>();
		int cursor = 0;
		for (CustomTag tag : tags) {
			String needle = ":" + tag.name() + ":";
			int index = body.indexOf(needle, cursor);
			if (index < 0) {
				continue;
			}
			int end = index + needle.length();
			matches.add(new Match(index, end, tag));
			cursor = end;
		}
		return matches;
	}

	private static void flush(List<Piece> pieces, StringBuilder text) {
		if (!text.isEmpty()) {
			pieces.add(new Piece(text.toString(), null));
			text.setLength(0);
		}
	}

	public static String customUrl(String id, boolean animated) {
		return CUSTOM_PREFIX + id + (animated ? ".gif" : ".png") + "?size=48";
	}

	public static String twemojiUrl(String cluster) {
		boolean hasZwj = cluster.codePoints().anyMatch(cp -> cp == ZWJ);
		StringBuilder hex = new StringBuilder();
		for (int i = 0; i < cluster.length(); ) {
			int cp = cluster.codePointAt(i);
			i += Character.charCount(cp);
			if (cp == VS16 && !hasZwj) {
				continue;
			}
			if (!hex.isEmpty()) {
				hex.append('-');
			}
			hex.append(Integer.toHexString(cp));
		}
		return TWEMOJI_PREFIX + hex + ".png";
	}

	public static boolean isTrustedUrl(@Nullable String url) {
		if (url == null) {
			return false;
		}
		return TRUSTED_CUSTOM.matcher(url).matches() || TRUSTED_TWEMOJI.matcher(url).matches();
	}

	static boolean isEmojiCluster(String cluster) {
		if (cluster.isEmpty()) {
			return false;
		}
		int first = cluster.codePointAt(0);
		if (cluster.codePointCount(0, cluster.length()) == 2
				&& isRegionalIndicator(first)
				&& isRegionalIndicator(cluster.codePointAt(cluster.offsetByCodePoints(0, 1)))) {
			return true;
		}
		if (isKeycapBase(first) && cluster.codePoints().anyMatch(cp -> cp == KEYCAP)) {
			return true;
		}
		if (!Character.isExtendedPictographic(first)) {
			return false;
		}
		if (Character.isEmojiPresentation(first)) {
			return true;
		}
		for (int i = 0; i < cluster.length(); ) {
			int cp = cluster.codePointAt(i);
			i += Character.charCount(cp);
			if (cp == VS16 || cp == ZWJ || Character.isEmojiModifier(cp)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isRegionalIndicator(int cp) {
		return cp >= 0x1F1E6 && cp <= 0x1F1FF;
	}

	private static boolean isKeycapBase(int cp) {
		return (cp >= '0' && cp <= '9') || cp == '#' || cp == '*';
	}
}
