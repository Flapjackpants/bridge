package io.github.flapjackpants.discordbridge.text;

import io.github.flapjackpants.discordbridge.media.MediaRef;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record InboundMessage(String authorDisplayName, String authorUsername, String content,
		@Nullable String replyToName, List<AttachmentRef> attachments,
		List<String> stickerNames, String jumpUrl,
		@Nullable String authorAvatarUrl, List<MediaRef> media) {
	public InboundMessage(String authorDisplayName, String authorUsername, String content,
			@Nullable String replyToName, List<AttachmentRef> attachments,
			List<String> stickerNames, String jumpUrl) {
		this(authorDisplayName, authorUsername, content, replyToName, attachments, stickerNames, jumpUrl,
				null, List.of());
	}
}
