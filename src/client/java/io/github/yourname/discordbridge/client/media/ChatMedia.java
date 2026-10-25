package io.github.yourname.discordbridge.client.media;

import com.google.common.collect.MapMaker;
import io.github.yourname.discordbridge.client.ClientSettings;
import io.github.yourname.discordbridge.config.ClientMediaConfig;
import io.github.yourname.discordbridge.config.MediaMode;
import io.github.yourname.discordbridge.media.MediaGeometry;
import io.github.yourname.discordbridge.media.MediaLayout;
import io.github.yourname.discordbridge.media.MediaRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ChatMedia {
	private static final Logger LOG = LoggerFactory.getLogger("discordbridge");
	private static final Map<Component, List<MediaRef>> MEDIA = new MapMaker().weakKeys().makeMap();

	private ChatMedia() {}

	/** One reserved chat row of one media item. Emits no characters. */
	public record MediaRow(MediaRef ref, int boxWidth, int boxHeight, int row, int rowHeight)
			implements FormattedCharSequence {
		@Override
		public boolean accept(FormattedCharSink sink) {
			return true;
		}
	}

	/** Any thread. Ignores null/empty media. Weak identity key: entry dies with the Component. */
	public static void register(Component fallback, List<MediaRef> media) {
		if (fallback == null || media == null || media.isEmpty()) {
			return;
		}
		MEDIA.put(fallback, List.copyOf(media));
	}

	/** Rows for a chat message in top-to-bottom order (all rows of media[0], then media[1], …). Empty if not registered or mode OFF. */
	public static List<MediaRow> rowsFor(Component content, int maxWidth, double lineSpacing) {
		ClientMediaConfig cfg = ClientSettings.get();
		List<MediaRef> refs = MEDIA.get(content);
		if (cfg.mode == MediaMode.OFF || refs == null) {
			return List.of();
		}
		int rowH = MediaLayout.entryHeight(lineSpacing);
		int maxW = Math.max(16, Math.min(cfg.maxDisplayWidth, maxWidth));
		List<MediaRow> out = new ArrayList<>();
		for (MediaRef ref : refs) {
			MediaGeometry.Box box = MediaGeometry.Box.fit(ref.width(), ref.height(), maxW, cfg.maxDisplayHeight);
			int n = MediaLayout.rows(box.height(), rowH);
			for (int r = 0; r < n; r++) {
				out.add(new MediaRow(ref, box.width(), box.height(), r, rowH));
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
}
