package io.github.flapjackpants.discordbridge.net;

import static org.junit.jupiter.api.Assertions.*;

import io.github.flapjackpants.discordbridge.config.BridgeConfig;
import io.github.flapjackpants.discordbridge.media.MediaRef;
import io.github.flapjackpants.discordbridge.text.InboundMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.List;
import net.minecraft.network.Utf8String;
import net.minecraft.network.VarInt;
import org.junit.jupiter.api.Test;

class RichMessageTest {
	private static RichMessage full() {
		return new RichMessage("Display", "user", "https://cdn.discordapp.com/av.png", "hello wörld 🎉", "Other",
				"https://discord.com/channels/1/2/3", List.of("wave", "hi"),
				List.of(new MediaRef(MediaRef.Kind.GIFV, "https://media.tenor.com/a/b.gif",
								"https://media.discordapp.net/x?width=1&height=1", 640, 360, 1234L, "image/gif", "b.gif"),
						new MediaRef(MediaRef.Kind.VIDEO, "https://cdn.discordapp.com/v.mp4", null, 10, 20, -1,
								"video/mp4", "v.mp4")));
	}

	private static void writeHeader(ByteBuf buf) {
		for (int i = 0; i < 6; i++) {
			Utf8String.write(buf, "", 256);
		}
	}

	@Test
	void roundTrip() {
		ByteBuf buf = Unpooled.buffer();
		RichMessage.STREAM_CODEC.encode(buf, full());
		assertEquals(full(), RichMessage.STREAM_CODEC.decode(buf));
		assertEquals(0, buf.readableBytes());
	}

	@Test
	void linkRoundTrip() {
		RichMessage f = full();
		List<MediaRef> linked = f.media().stream().map(r -> new MediaRef(r.kind(), r.url(), r.proxyUrl(), r.width(),
				r.height(), r.sizeBytes(), r.contentType(), r.fileName(), "https://tenor.com/view/x")).toList();
		RichMessage m = new RichMessage(f.authorDisplayName(), f.authorUsername(), f.avatarUrl(), f.content(),
				f.replyToName(), f.jumpUrl(), f.stickerNames(), linked);
		ByteBuf buf = Unpooled.buffer();
		RichMessage.STREAM_CODEC.encode(buf, m);
		RichMessage back = RichMessage.STREAM_CODEC.decode(buf);
		assertEquals(m, back);
		assertEquals("https://tenor.com/view/x", back.media().get(1).linkUrl());
		assertEquals(0, buf.readableBytes());
	}

	@Test
	void decodesWithoutTrailer() {
		ByteBuf buf = Unpooled.buffer();
		RichMessage.STREAM_CODEC.encode(buf, full());
		buf.writerIndex(buf.writerIndex() - 2);
		RichMessage back = RichMessage.STREAM_CODEC.decode(buf);
		assertEquals(full(), back);
		assertNull(back.media().get(0).linkUrl());
	}

	@Test
	void nullsRoundTrip() {
		RichMessage m = new RichMessage("a", "b", null, "c", null, "", List.of(),
				List.of(new MediaRef(MediaRef.Kind.STATIC, null, null, 0, 0, -1, "", "")));
		ByteBuf buf = Unpooled.buffer();
		RichMessage.STREAM_CODEC.encode(buf, m);
		RichMessage back = RichMessage.STREAM_CODEC.decode(buf);
		assertEquals(m, back);
		assertNull(back.avatarUrl());
		assertNull(back.replyToName());
		assertNull(back.media().get(0).url());
		assertNull(back.media().get(0).proxyUrl());
	}

	@Test
	void clipsLongStringsAndCounts() {
		List<String> stickers = java.util.Collections.nCopies(10, "s");
		MediaRef ref = new MediaRef(MediaRef.Kind.STATIC, null, null, 0, 0, -1, "", "");
		RichMessage m = new RichMessage("n".repeat(300), "u", null, "c", null, "", stickers,
				java.util.Collections.nCopies(20, ref));
		ByteBuf buf = Unpooled.buffer();
		RichMessage.STREAM_CODEC.encode(buf, m);
		RichMessage back = RichMessage.STREAM_CODEC.decode(buf);
		assertEquals(256, back.authorDisplayName().length());
		assertEquals(8, back.stickerNames().size());
		assertEquals(16, back.media().size());
	}

	@Test
	void rejectsBadCounts() {
		ByteBuf buf = Unpooled.buffer();
		writeHeader(buf);
		VarInt.write(buf, 0);
		VarInt.write(buf, 17);
		assertThrows(DecoderException.class, () -> RichMessage.STREAM_CODEC.decode(buf));
		ByteBuf buf2 = Unpooled.buffer();
		writeHeader(buf2);
		VarInt.write(buf2, 9);
		assertThrows(DecoderException.class, () -> RichMessage.STREAM_CODEC.decode(buf2));
	}

	@Test
	void rejectsBadKind() {
		ByteBuf buf = Unpooled.buffer();
		writeHeader(buf);
		VarInt.write(buf, 0);
		VarInt.write(buf, 1);
		VarInt.write(buf, 9);
		Utf8String.write(buf, "", 2048);
		Utf8String.write(buf, "", 2048);
		VarInt.write(buf, 0);
		VarInt.write(buf, 0);
		buf.writeLong(-1);
		Utf8String.write(buf, "", 128);
		Utf8String.write(buf, "", 256);
		assertThrows(DecoderException.class, () -> RichMessage.STREAM_CODEC.decode(buf));
	}

	@Test
	void fromSanitizes() {
		InboundMessage in = new InboundMessage("§aBob", "§bbob", "§chi", "§dAl", List.of(), List.of("§eS"),
				"https://j", "https://cdn.discordapp.com/av.png", List.of());
		RichMessage r = RichMessage.from(in, new BridgeConfig());
		assertEquals("hi", r.content());
		assertEquals("Bob", r.authorDisplayName());
		assertEquals("bob", r.authorUsername());
		assertEquals("Al", r.replyToName());
		assertEquals(List.of("S"), r.stickerNames());
		assertEquals("https://cdn.discordapp.com/av.png", r.avatarUrl());
		assertEquals("", RichMessage.from(new InboundMessage("a", "b", "  ", null, List.of(), List.of(), "j"),
				new BridgeConfig()).content());
	}
}
