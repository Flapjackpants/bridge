package io.github.flapjackpants.discordbridge.text;

import io.github.flapjackpants.discordbridge.config.BridgeConfig;
import java.util.ArrayList;
import java.util.List;

public final class InboundFormatter {
	private InboundFormatter() {}

	/** Emoji-split body pieces; sanitized the same way as {@link #format}. Empty when the body is blank. */
	public static List<Emojis.Piece> bodyPieces(InboundMessage m, BridgeConfig cfg) {
		String body = m.content() == null || m.content().isBlank()
				? ""
				: TextSanitizer.forMinecraft(m.content(), cfg.maxInboundLength, cfg.maxInboundLines);
		if (body.isEmpty()) {
			return List.of();
		}
		return Emojis.split(body, m.customEmojis());
	}

	/** Returns an empty list when there is nothing to show (caller must skip). */
	public static List<Segment> format(InboundMessage m, BridgeConfig cfg) {
		String body = m.content() == null || m.content().isBlank()
				? ""
				: TextSanitizer.forMinecraft(m.content(), cfg.maxInboundLength, cfg.maxInboundLines);
		if (body.isBlank() && m.attachments().isEmpty() && m.stickerNames().isEmpty()) {
			return List.of();
		}

		List<Segment> out = new ArrayList<>();
		out.add(new Segment(cfg.discordPrefix, Role.PREFIX, null, null));
		if (m.replyToName() != null) {
			out.add(new Segment("↪ " + TextSanitizer.stripLegacyFormatting(m.replyToName()) + " ", Role.REPLY, null, null));
		}
		out.add(new Segment("<" + TextSanitizer.stripLegacyFormatting(m.authorDisplayName()) + ">", Role.NAME,
				m.jumpUrl(), "Sent from Discord by @" + m.authorUsername()));
		if (!body.isBlank()) {
			List<Emojis.Piece> pieces = bodyPieces(m, cfg);
			if (pieces.stream().noneMatch(piece -> piece.emoji() != null)) {
				out.add(new Segment(" " + body, Role.BODY, null, null));
			} else {
				for (int i = 0; i < pieces.size(); i++) {
					Emojis.Piece piece = pieces.get(i);
					String text = piece.text();
					if (i == 0) {
						if (piece.emoji() == null) {
							text = " " + text;
						} else {
							out.add(new Segment(" ", Role.BODY, null, null));
						}
					}
					out.add(new Segment(text, piece.emoji() == null ? Role.BODY : Role.EMOJI, null, null));
				}
			}
		}
		for (AttachmentRef a : m.attachments()) {
			out.add(new Segment(a.isImage() ? " [image]" : " [file: " + a.fileName() + "]", Role.ATTACHMENT,
					a.url(), "Open in browser"));
		}
		for (String sticker : m.stickerNames()) {
			out.add(new Segment(" [sticker: " + sticker + "]", Role.ATTACHMENT, null, null));
		}
		return out;
	}
}
