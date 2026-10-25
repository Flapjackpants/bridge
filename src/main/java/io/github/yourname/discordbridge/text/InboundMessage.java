package io.github.yourname.discordbridge.text;

import java.util.List;
import org.jspecify.annotations.Nullable;

// Spec 2 will add media metadata fields; keep construction centralized in DiscordBridge.toInbound().
public record InboundMessage(String authorDisplayName, String authorUsername, String content,
		@Nullable String replyToName, List<AttachmentRef> attachments,
		List<String> stickerNames, String jumpUrl) {
}
