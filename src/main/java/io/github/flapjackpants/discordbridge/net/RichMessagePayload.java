package io.github.flapjackpants.discordbridge.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RichMessagePayload(Component fallback, RichMessage message) implements CustomPacketPayload {
	public static final Type<RichMessagePayload> TYPE =
			new Type<>(Identifier.fromNamespaceAndPath("discordbridge", "message"));
	public static final StreamCodec<RegistryFriendlyByteBuf, RichMessagePayload> CODEC = StreamCodec.composite(
			ComponentSerialization.TRUSTED_STREAM_CODEC, RichMessagePayload::fallback,
			RichMessage.STREAM_CODEC, RichMessagePayload::message,
			RichMessagePayload::new);

	@Override
	public Type<RichMessagePayload> type() {
		return TYPE;
	}
}
