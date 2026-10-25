package io.github.flapjackpants.discordbridge.net;

import io.github.flapjackpants.discordbridge.config.BridgeConfig;
import io.github.flapjackpants.discordbridge.media.MediaRef;
import io.github.flapjackpants.discordbridge.text.InboundMessage;
import io.github.flapjackpants.discordbridge.text.TextSanitizer;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.Utf8String;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.Nullable;

/** Wire format is documented in the Spec 2 handoff; v2 appends one linkUrl string per media entry after the media list. */
public record RichMessage(String authorDisplayName, String authorUsername, @Nullable String avatarUrl,
		String content, @Nullable String replyToName, String jumpUrl,
		List<String> stickerNames, List<MediaRef> media) {
	private static final int MAX_NAME = 256;
	private static final int MAX_URL = 2048;
	private static final int MAX_CONTENT = 4096;
	private static final int MAX_TYPE = 128;
	private static final int MAX_STICKERS = 8;
	private static final int MAX_MEDIA = 16;

	public static final StreamCodec<ByteBuf, RichMessage> STREAM_CODEC = StreamCodec.of(
			RichMessage::encode, RichMessage::decode);

	public static RichMessage from(InboundMessage m, BridgeConfig cfg) {
		String content = m.content() == null || m.content().isBlank()
				? ""
				: TextSanitizer.forMinecraft(m.content(), cfg.maxInboundLength, cfg.maxInboundLines);
		return new RichMessage(
				TextSanitizer.stripLegacyFormatting(m.authorDisplayName()),
				TextSanitizer.stripLegacyFormatting(m.authorUsername()),
				m.authorAvatarUrl(),
				content,
				m.replyToName() == null ? null : TextSanitizer.stripLegacyFormatting(m.replyToName()),
				m.jumpUrl(),
				m.stickerNames().stream().map(TextSanitizer::stripLegacyFormatting).toList(),
				m.media());
	}

	private static void encode(ByteBuf buf, RichMessage v) {
		writeStr(buf, v.authorDisplayName, MAX_NAME);
		writeStr(buf, v.authorUsername, MAX_NAME);
		writeStr(buf, v.avatarUrl, MAX_URL);
		writeStr(buf, v.content, MAX_CONTENT);
		writeStr(buf, v.replyToName, MAX_NAME);
		writeStr(buf, v.jumpUrl, MAX_URL);
		int stickers = Math.min(v.stickerNames.size(), MAX_STICKERS);
		VarInt.write(buf, stickers);
		for (int i = 0; i < stickers; i++) {
			writeStr(buf, v.stickerNames.get(i), MAX_NAME);
		}
		int media = Math.min(v.media.size(), MAX_MEDIA);
		VarInt.write(buf, media);
		for (int i = 0; i < media; i++) {
			MediaRef r = v.media.get(i);
			VarInt.write(buf, r.kind().ordinal());
			writeStr(buf, r.url(), MAX_URL);
			writeStr(buf, r.proxyUrl(), MAX_URL);
			VarInt.write(buf, r.width());
			VarInt.write(buf, r.height());
			buf.writeLong(r.sizeBytes());
			writeStr(buf, r.contentType(), MAX_TYPE);
			writeStr(buf, r.fileName(), MAX_NAME);
		}
		for (int i = 0; i < media; i++) {
			writeStr(buf, v.media.get(i).linkUrl(), MAX_URL);
		}
	}

	private static RichMessage decode(ByteBuf buf) {
		String display = readStr(buf, MAX_NAME);
		String username = readStr(buf, MAX_NAME);
		String avatar = readStr(buf, MAX_URL);
		String content = readStr(buf, MAX_CONTENT);
		String replyTo = readStr(buf, MAX_NAME);
		String jump = readStr(buf, MAX_URL);
		int stickerCount = VarInt.read(buf);
		if (stickerCount < 0 || stickerCount > MAX_STICKERS) {
			throw new DecoderException("Bad sticker count " + stickerCount);
		}
		List<String> stickers = new ArrayList<>(stickerCount);
		for (int i = 0; i < stickerCount; i++) {
			String s = readStr(buf, MAX_NAME);
			stickers.add(s == null ? "" : s);
		}
		int mediaCount = VarInt.read(buf);
		if (mediaCount < 0 || mediaCount > MAX_MEDIA) {
			throw new DecoderException("Bad media count " + mediaCount);
		}
		List<MediaRef> media = new ArrayList<>(mediaCount);
		MediaRef.Kind[] kinds = MediaRef.Kind.values();
		for (int i = 0; i < mediaCount; i++) {
			int ordinal = VarInt.read(buf);
			if (ordinal < 0 || ordinal >= kinds.length) {
				throw new DecoderException("Bad media kind " + ordinal);
			}
			String url = readStr(buf, MAX_URL);
			String proxy = readStr(buf, MAX_URL);
			int w = VarInt.read(buf);
			int h = VarInt.read(buf);
			long size = buf.readLong();
			String type = readStr(buf, MAX_TYPE);
			String name = readStr(buf, MAX_NAME);
			media.add(new MediaRef(kinds[ordinal], url, proxy, w, h, size, type, name));
		}
		if (buf.isReadable()) {
			for (int i = 0; i < mediaCount; i++) {
				String link = readStr(buf, MAX_URL);
				MediaRef r = media.get(i);
				media.set(i, new MediaRef(r.kind(), r.url(), r.proxyUrl(), r.width(), r.height(), r.sizeBytes(),
						r.contentType(), r.fileName(), link));
			}
		}
		return new RichMessage(display == null ? "" : display, username == null ? "" : username, avatar,
				content == null ? "" : content, replyTo, jump == null ? "" : jump,
				List.copyOf(stickers), List.copyOf(media));
	}

	private static void writeStr(ByteBuf buf, @Nullable String s, int max) {
		String t = s == null ? "" : s;
		if (t.length() > max) {
			int end = max;
			if (Character.isHighSurrogate(t.charAt(end - 1))) {
				end--;
			}
			t = t.substring(0, end);
		}
		Utf8String.write(buf, t, max);
	}

	private static @Nullable String readStr(ByteBuf buf, int max) {
		String s = Utf8String.read(buf, max);
		return s.isEmpty() ? null : s;
	}
}
