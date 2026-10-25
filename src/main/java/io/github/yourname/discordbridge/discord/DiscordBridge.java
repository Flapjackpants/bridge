package io.github.yourname.discordbridge.discord;

import io.github.yourname.discordbridge.config.BridgeConfig;
import io.github.yourname.discordbridge.media.MediaPipeline;
import io.github.yourname.discordbridge.net.BridgeNetworking;
import io.github.yourname.discordbridge.net.RichMessage;
import io.github.yourname.discordbridge.text.AttachmentRef;
import io.github.yourname.discordbridge.text.ComponentRenderer;
import io.github.yourname.discordbridge.text.Emphasis;
import io.github.yourname.discordbridge.text.InboundFormatter;
import io.github.yourname.discordbridge.text.InboundMessage;
import io.github.yourname.discordbridge.text.Segment;
import io.github.yourname.discordbridge.text.TextSanitizer;
import java.time.Duration;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.EmbedType;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.session.SessionDisconnectEvent;
import net.dv8tion.jda.api.events.session.SessionResumeEvent;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ImageProxy;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DiscordBridge {
	private static final Logger LOG = LoggerFactory.getLogger("discordbridge");
	private static final String WEBHOOK_NAME = "DiscordBridge";
	private static final long EMBED_WAIT_MS = 1500;
	// Discord can take seconds to unfurl a large media link (a 4.6 MB GIF took ~2.6 s); update events end the wait early
	private static final long MEDIA_EMBED_WAIT_MS = 8000;

	public enum State { DISABLED, CONNECTING, CONNECTED, FAILED }

	public record Status(State state, @Nullable String botTag, @Nullable String channelName,
			boolean webhookActive, long dropped, @Nullable String lastError) {}

	private final MinecraftServer server;
	private final BridgeConfig cfg;
	private final OutboundBatcher batcher = new OutboundBatcher(200);
	private final Map<Long, Message> awaitingEmbeds = new ConcurrentHashMap<>();

	private volatile State state = State.DISABLED;
	private volatile @Nullable String botTag;
	private volatile @Nullable String channelName;
	private volatile @Nullable String lastError;
	private volatile @Nullable JDA jda;
	private volatile @Nullable TextChannel channel;
	private volatile @Nullable Webhook webhook;
	private volatile @Nullable ScheduledExecutorService scheduler;

	public DiscordBridge(MinecraftServer server, BridgeConfig cfg) {
		this.server = server;
		this.cfg = cfg;
	}

	public synchronized void start() {
		List<String> problems = cfg.validate();
		if (!problems.isEmpty()) {
			state = State.DISABLED;
			LOG.warn("Discord bridge disabled: {}. See README.", String.join("; ", problems));
			return;
		}
		state = State.CONNECTING;
		try {
			jda = JDABuilder.createLight(cfg.effectiveToken(),
							EnumSet.of(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT))
					.addEventListeners(new Listener())
					.build();
		} catch (InvalidTokenException | IllegalArgumentException e) {
			fail("Invalid bot token");
		} catch (Exception e) {
			fail("Could not start Discord client: " + e.getClass().getSimpleName());
		}
	}

	public synchronized void stop(Duration timeout) {
		JDA j = jda;
		jda = null;
		if (state == State.CONNECTED) {
			batcher.flushNow(this::deliver);
		}
		awaitingEmbeds.clear();
		ScheduledExecutorService s = scheduler;
		scheduler = null;
		if (s != null) {
			s.shutdownNow();
		}
		if (j != null) {
			j.shutdown();
			try {
				if (!j.awaitShutdown(timeout)) {
					j.shutdownNow();
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				j.shutdownNow();
			}
		}
		channel = null;
		webhook = null;
		state = State.DISABLED;
	}

	public void sendChat(ServerPlayer player, String rawMessage) {
		String content = TextSanitizer.forDiscord(rawMessage);
		if (content.isBlank()) {
			return;
		}
		String name = player.getName().getString();
		String avatar = cfg.avatarUrlTemplate
				.replace("{uuid}", player.getUUID().toString().replace("-", ""))
				.replace("{name}", name);
		batcher.enqueue(new OutboundBatcher.Outbound(name, avatar, content));
	}

	public void sendEvent(String plainText, Emphasis emphasis) {
		String content = TextSanitizer.forDiscordEvent(plainText, emphasis);
		if (!content.isBlank()) {
			batcher.enqueue(new OutboundBatcher.Outbound(null, null, content));
		}
	}

	public Status status() {
		return new Status(state, botTag, channelName, webhook != null, batcher.droppedCount(), lastError);
	}

	private void fail(String error) {
		lastError = error;
		state = State.FAILED;
		LOG.error("Discord bridge failed: {}", error);
	}

	private void connected() {
		ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "discordbridge-outbound");
			t.setDaemon(true);
			return t;
		});
		scheduler = ses;
		state = State.CONNECTED;
		lastError = null;
		LOG.info("Discord bridge connected as {} in {}", botTag, channelName);
		batcher.start(ses, Duration.ofMillis(750), this::deliver);
	}

	private void webhookFallback(Throwable err) {
		LOG.warn("Missing Manage Webhooks permission; falling back to bot messages ({})", err.getMessage());
		webhook = null;
		connected();
	}

	private void deliver(OutboundBatcher.Outbound o) {
		TextChannel ch = channel;
		if (ch == null) {
			return;
		}
		Webhook wh = webhook;
		if (o.authorName() == null || wh == null) {
			String text = o.authorName() == null
					? o.content()
					: "**" + MarkdownSanitizer.escape(o.authorName()) + "**: " + o.content();
			ch.sendMessage(text)
					.setAllowedMentions(EnumSet.noneOf(Message.MentionType.class))
					.queue(null, err -> LOG.warn("Failed to send Discord message: {}", err.getMessage()));
		} else {
			wh.sendMessage(o.content())
					.setUsername(o.authorName())
					.setAvatarUrl(o.avatarUrl())
					.setAllowedMentions(EnumSet.noneOf(Message.MentionType.class))
					.queue(null, err -> LOG.warn("Failed to send Discord webhook message: {}", err.getMessage()));
		}
	}

	private InboundMessage toInbound(Message message) {
		User author = message.getAuthor();
		String displayName = message.getMember() != null
				? message.getMember().getEffectiveName()
				: author.getEffectiveName();
		Message ref = message.getReferencedMessage();
		String replyTo = ref == null ? null : ref.getAuthor().getEffectiveName();
		List<AttachmentRef> attachments = message.getAttachments().stream()
				.map(a -> new AttachmentRef(a.getFileName(), a.getUrl(), a.isImage()))
				.toList();
		List<String> stickers = message.getStickers().stream().map(s -> s.getName()).toList();
		ImageProxy avatar = message.getMember() != null
				? message.getMember().getEffectiveAvatar()
				: author.getEffectiveAvatar();
		MediaPipeline.Policy policy = MediaPipeline.Policy.from(cfg);
		String avatarUrl = avatar.getUrl(64);
		return new InboundMessage(displayName, author.getName(), message.getContentDisplay(), replyTo,
				attachments, stickers, message.getJumpUrl(),
				policy.allows(avatarUrl) ? avatarUrl : null,
				MediaPipeline.process(mediaCandidates(message), policy));
	}

	private static List<MediaPipeline.Candidate> mediaCandidates(Message m) {
		List<MediaPipeline.Candidate> out = new ArrayList<>();
		for (Message.Attachment a : m.getAttachments()) {
			out.add(new MediaPipeline.Candidate(MediaPipeline.Source.ATTACHMENT, a.getUrl(), a.getProxyUrl(),
					a.getWidth(), a.getHeight(), a.getSize(), a.getContentType(), a.getFileName(),
					null, null, 0, 0, null, a.getUrl()));
		}
		for (MessageEmbed e : m.getEmbeds()) {
			MessageEmbed.Thumbnail thumb = e.getThumbnail();
			switch (e.getType()) {
				case IMAGE -> {
					MessageEmbed.Thumbnail t = e.getThumbnail();
					MessageEmbed.ImageInfo img = e.getImage();
					if (t != null) {
						out.add(new MediaPipeline.Candidate(MediaPipeline.Source.EMBED_IMAGE, t.getUrl(),
								t.getProxyUrl(), t.getWidth(), t.getHeight(), -1, null, null,
								null, null, 0, 0, null, e.getUrl()));
					} else if (img != null) {
						out.add(new MediaPipeline.Candidate(MediaPipeline.Source.EMBED_IMAGE, img.getUrl(),
								img.getProxyUrl(), img.getWidth(), img.getHeight(), -1, null, null,
								null, null, 0, 0, null, e.getUrl()));
					}
				}
				case GIFV, VIDEO -> {
					MessageEmbed.VideoInfo v = e.getVideoInfo();
					if (v == null && thumb == null) {
						continue;
					}
					out.add(new MediaPipeline.Candidate(
							e.getType() == EmbedType.GIFV
									? MediaPipeline.Source.EMBED_GIFV
									: MediaPipeline.Source.EMBED_VIDEO,
							v == null ? null : v.getUrl(), null,
							v == null ? 0 : v.getWidth(), v == null ? 0 : v.getHeight(), -1, null, null,
							thumb == null ? null : thumb.getUrl(), thumb == null ? null : thumb.getProxyUrl(),
							thumb == null ? 0 : thumb.getWidth(), thumb == null ? 0 : thumb.getHeight(), null, e.getUrl()));
				}
				default -> { }
			}
		}
		return out;
	}

	private void dispatch(Message message) {
		InboundMessage in = toInbound(message);
		List<Segment> segments = InboundFormatter.format(in, cfg);
		if (segments.isEmpty()) {
			return;
		}
		Component fallback = ComponentRenderer.render(segments, cfg);
		RichMessage rich = cfg.richMedia && BridgeNetworking.anyRich() ? RichMessage.from(in, cfg) : null;
		server.execute(() -> BridgeNetworking.broadcast(server, fallback, rich));
	}

	private final class Listener extends ListenerAdapter {
		@Override
		public void onReady(ReadyEvent e) {
			if (state == State.CONNECTED) {
				return;
			}
			JDA j = e.getJDA();
			botTag = j.getSelfUser().getAsTag();
			TextChannel ch = j.getTextChannelById(cfg.channelIdLong().orElse(0));
			if (ch == null) {
				fail("Channel not found or bot cannot see it");
				return;
			}
			channel = ch;
			channelName = "#" + ch.getName();
			if (!cfg.useWebhook) {
				connected();
				return;
			}
			ch.retrieveWebhooks().queue(list -> {
				Webhook found = list.stream()
						.filter(w -> WEBHOOK_NAME.equals(w.getName())
								&& w.getOwnerAsUser() != null
								&& w.getOwnerAsUser().getIdLong() == j.getSelfUser().getIdLong())
						.findFirst().orElse(null);
				if (found != null) {
					webhook = found;
					connected();
				} else {
					ch.createWebhook(WEBHOOK_NAME).queue(w -> {
						webhook = w;
						connected();
					}, DiscordBridge.this::webhookFallback);
				}
			}, DiscordBridge.this::webhookFallback);
		}

		@Override
		public void onSessionDisconnect(SessionDisconnectEvent e) {
			LOG.info("Discord session disconnected; JDA will reconnect");
		}

		@Override
		public void onSessionResume(SessionResumeEvent e) {
			LOG.info("Discord session resumed");
		}

		@Override
		public void onMessageReceived(MessageReceivedEvent e) {
			if (e.getChannel().getIdLong() != cfg.channelIdLong().orElse(0)) {
				return;
			}
			if (e.getAuthor().isBot() || e.getAuthor().isSystem() || e.isWebhookMessage()) {
				return;
			}
			Message msg = e.getMessage();
			ScheduledExecutorService ses = scheduler;
			if (ses != null && cfg.richMedia && BridgeNetworking.anyRich()
					&& MediaPipeline.mayGainEmbeds(msg.getContentRaw(), msg.getEmbeds().size(),
							msg.getAttachments().size())) {
				long id = msg.getIdLong();
				long wait = MediaPipeline.linksAllowedMedia(msg.getContentRaw(), MediaPipeline.Policy.from(cfg))
						? MEDIA_EMBED_WAIT_MS
						: EMBED_WAIT_MS;
				awaitingEmbeds.put(id, msg);
				ses.schedule(() -> {
					Message m = awaitingEmbeds.remove(id);
					if (m != null) {
						dispatch(m);
					}
				}, wait, TimeUnit.MILLISECONDS);
				return;
			}
			dispatch(msg);
		}

		@Override
		public void onMessageUpdate(MessageUpdateEvent e) {
			if (e.getChannel().getIdLong() != cfg.channelIdLong().orElse(0)) {
				return;
			}
			Message m = e.getMessage();
			if (!m.getEmbeds().isEmpty() && awaitingEmbeds.remove(m.getIdLong()) != null) {
				dispatch(m);
			}
		}
	}
}
