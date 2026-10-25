package io.github.flapjackpants.discordbridge.client.media;

import com.google.common.collect.MapMaker;
import io.github.flapjackpants.discordbridge.client.ClientSettings;
import io.github.flapjackpants.discordbridge.config.ClientMediaConfig;
import io.github.flapjackpants.discordbridge.config.MediaMode;
import io.github.flapjackpants.discordbridge.media.MediaGeometry;
import io.github.flapjackpants.discordbridge.media.MediaLayout;
import io.github.flapjackpants.discordbridge.media.MediaLinks;
import io.github.flapjackpants.discordbridge.media.MediaRef;
import io.github.flapjackpants.discordbridge.text.MediaLinkText;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ChatMedia {
	private static final Logger LOG = LoggerFactory.getLogger("discordbridge");
	private static final Map<Component, List<MediaRef>> MEDIA = new MapMaker().weakKeys().makeMap();
	private static final Set<MediaRef> REVEALED = Collections.newSetFromMap(new MapMaker().weakKeys().makeMap());
	private static final Map<MediaRef, ClientMediaManager.State> OUTCOMES = new MapMaker().weakKeys().makeMap();
	private static @Nullable MediaRow hovered;
	private static boolean dirty;

	private ChatMedia() {}

	/** One reserved chat row of one media item, or its caption row. Emits no characters. */
	public record MediaRow(MediaRef ref, int boxWidth, int boxHeight, int row, int rowHeight, boolean hidden,
			boolean caption)
			implements FormattedCharSequence {
		@Override
		public boolean accept(FormattedCharSink sink) {
			return true;
		}
	}

	/** Hover/hit area in the same local coordinates drawRow uses. */
	public record Rect(int left, int top, int right, int bottom) {}

	/** Any thread. Ignores null/empty media. Weak identity key: entry dies with the Component. */
	public static void register(Component fallback, List<MediaRef> media) {
		if (fallback == null || media == null || media.isEmpty()) {
			return;
		}
		MEDIA.put(fallback, List.copyOf(media));
	}

	/** True iff cfg.mode() != MediaMode.OFF && cfg.showInChatOverlay. When false: no rows, no fetches, text untouched. */
	private static boolean mediaEnabled(ClientMediaConfig cfg) {
		return cfg.mode() != MediaMode.OFF && cfg.showInChatOverlay;
	}

	/** Render thread. Records a terminal failure for ref (state FAILED or TOO_LARGE) and marks chat dirty. */
	public static void markFailed(MediaRef ref, ClientMediaManager.State state) {
		OUTCOMES.put(ref, state);
		dirty = true;
	}

	/** Returns true once after any markFailed call, then false until the next one. */
	public static boolean consumeDirty() {
		boolean d = dirty;
		dirty = false;
		return d;
	}

	private static ClientMediaManager.@Nullable State failure(MediaRef ref) {
		ClientMediaManager.State known = OUTCOMES.get(ref);
		if (known != null) {
			return known;
		}
		ClientMediaManager.State s = ClientMediaManager.get().state(ref);
		if (s == ClientMediaManager.State.FAILED || s == ClientMediaManager.State.TOO_LARGE) {
			OUTCOMES.put(ref, s);
			return s;
		}
		return null;
	}

	/** Chat text to display for content; returns content unchanged when not registered, !mediaEnabled, or on error. */
	public static Component displayText(Component content) {
		try {
			List<MediaRef> refs = MEDIA.get(content);
			if (refs == null || !mediaEnabled(ClientSettings.get())) {
				return content;
			}
			Map<String, MediaLinkText.LinkState> states = new LinkedHashMap<>();
			for (MediaRef ref : refs) {
				if (ref.linkUrl() == null || ref.kind() == MediaRef.Kind.VIDEO) {
					continue;
				}
				ClientMediaManager.State f = failure(ref);
				MediaLinkText.LinkState s = f == ClientMediaManager.State.TOO_LARGE ? MediaLinkText.LinkState.TOO_LARGE
						: f == ClientMediaManager.State.FAILED ? MediaLinkText.LinkState.FAILED
						: MediaLinkText.LinkState.HIDE;
				states.merge(ref.linkUrl(), s, MediaLinkText::combine);
			}
			return MediaLinkText.rewrite(content, states,
					Component.translatable("discordbridge.media.link.failed").withStyle(ChatFormatting.GRAY),
					Component.translatable("discordbridge.media.link.tooLarge").withStyle(ChatFormatting.GRAY));
		} catch (RuntimeException e) {
			return content;
		}
	}

	/** Rows for a chat message in top-to-bottom order (all rows of media[0], then media[1], …). Empty if not registered or mode OFF. */
	public static List<MediaRow> rowsFor(Component content, int maxWidth, double lineSpacing) {
		ClientMediaConfig cfg = ClientSettings.get();
		List<MediaRef> refs = MEDIA.get(content);
		if (!mediaEnabled(cfg) || refs == null) {
			return List.of();
		}
		int rowH = MediaLayout.entryHeight(lineSpacing);
		int maxW = Math.max(16, Math.min(cfg.displayWidth(), maxWidth));
		List<MediaRow> out = new ArrayList<>();
		for (MediaRef ref : refs) {
			if (failure(ref) != null) {
				continue;
			}
			MediaGeometry.Box box = MediaGeometry.Box.fit(ref.width(), ref.height(), maxW, cfg.displayHeight());
			if (cfg.clickToReveal && !isRevealed(ref)) {
				out.add(new MediaRow(ref, box.width(), box.height(), 0, rowH, true, false));
				continue;
			}
			if (ref.kind() == MediaRef.Kind.VIDEO) {
				out.add(new MediaRow(ref, box.width(), box.height(), 0, rowH, false, true));
			}
			int n = MediaLayout.rows(box.height(), rowH);
			for (int r = 0; r < n; r++) {
				out.add(new MediaRow(ref, box.width(), box.height(), r, rowH, false, false));
			}
		}
		return out;
	}

	/** Draws one row; no-op when nothing to draw. Never throws. */
	public static void drawRow(GuiGraphicsExtractor g, int textTop, float opacity, MediaRow row, boolean foreground) {
		try {
			if (!foreground && !ClientSettings.get().showInChatOverlay) {
				return;
			}
			Minecraft mc = Minecraft.getInstance();
			if (row.hidden()) {
				g.text(mc.font, Component.translatable("discordbridge.media.reveal"), 0, textTop,
						ARGB.multiplyAlpha(0xFF7289DA, opacity));
				return;
			}
			if (row.caption()) {
				g.text(mc.font, Component.translatable("discordbridge.media.videoAttached"), 0, textTop,
						ARGB.multiplyAlpha(0xFFAAAAAA, opacity));
				return;
			}
			int top = MediaLayout.rowTop(textTop, mc.options.chatLineSpacing().get());
			ClientMediaManager m = ClientMediaManager.get();
			ClientMediaManager.Drawable d = m.request(row.ref());
			if (d != null) {
				MediaGeometry.Sheet s = d.sheet();
				MediaLayout.Slice sl = MediaLayout.slice(row.row(), row.rowHeight(), row.boxHeight(), s.frameHeight());
				if (sl.destHeight() <= 0) {
					return;
				}
				int f = d.frame(ClientMediaManager.nowMs());
				g.blit(RenderPipelines.GUI_TEXTURED, d.texture(), 0, top, s.u(f), s.v(f) + sl.srcTop(),
						row.boxWidth(), sl.destHeight(), s.frameWidth(), sl.srcHeight(), s.width(), s.height(),
						ARGB.white(opacity));
				return;
			}
			ClientMediaManager.State state = m.state(row.ref());
			if (state == ClientMediaManager.State.LOADING) {
				MediaLayout.Slice sl = MediaLayout.slice(row.row(), row.rowHeight(), row.boxHeight(), row.boxHeight());
				if (sl.destHeight() > 0) {
					g.fill(0, top, row.boxWidth(), top + sl.destHeight(), ARGB.multiplyAlpha(0x40FFFFFF, opacity));
				}
			} else if (state == ClientMediaManager.State.FAILED && row.row() == 0) {
				g.text(mc.font, Component.translatable("discordbridge.media.failed"), 0, textTop,
						ARGB.multiplyAlpha(0xFFAAAAAA, opacity));
			}
		} catch (RuntimeException e) {
			LOG.debug("discordbridge media row draw failed", e);
		}
	}

	public static void reveal(MediaRef ref) {
		REVEALED.add(ref);
	}

	public static boolean isRevealed(MediaRef ref) {
		return REVEALED.contains(ref);
	}

	/** Hover area in the same local coords drawRow uses; null when the row shows nothing clickable. */
	public static @Nullable Rect hitRect(MediaRow row, int textTop) {
		Minecraft mc = Minecraft.getInstance();
		int top = MediaLayout.rowTop(textTop, mc.options.chatLineSpacing().get());
		if (row.hidden()) {
			return new Rect(0, top, mc.font.width(Component.translatable("discordbridge.media.reveal")),
					top + row.rowHeight());
		}
		if (row.caption()) {
			return new Rect(0, top, mc.font.width(Component.translatable("discordbridge.media.videoAttached")),
					top + row.rowHeight());
		}
		MediaLayout.Slice sl = MediaLayout.slice(row.row(), row.rowHeight(), row.boxHeight(), row.boxHeight());
		return sl.destHeight() <= 0 ? null : new Rect(0, top, row.boxWidth(), top + sl.destHeight());
	}

	public static List<Component> tooltip(MediaRow row) {
		String label = MediaLinks.label(row.ref());
		List<Component> out = new ArrayList<>(2);
		if (!label.isEmpty()) {
			out.add(Component.literal(label));
		}
		out.add(Component.translatable(row.hidden() ? "discordbridge.media.tooltip.reveal" : "discordbridge.media.tooltip.open")
				.withStyle(ChatFormatting.GRAY));
		return out;
	}

	public static void setHovered(MediaRow row) {
		hovered = row;
	}

	public static @Nullable MediaRow hovered() {
		return hovered;
	}

	public static void clearHover() {
		hovered = null;
	}
}
