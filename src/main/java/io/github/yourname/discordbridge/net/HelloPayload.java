package io.github.yourname.discordbridge.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record HelloPayload(int protocolVersion) implements CustomPacketPayload {
	public static final Type<HelloPayload> TYPE =
			new Type<>(Identifier.fromNamespaceAndPath("discordbridge", "hello"));
	public static final StreamCodec<ByteBuf, HelloPayload> CODEC =
			StreamCodec.composite(ByteBufCodecs.VAR_INT, HelloPayload::protocolVersion, HelloPayload::new);

	@Override
	public Type<HelloPayload> type() {
		return TYPE;
	}
}
