package io.github.flapjackpants.discordbridge.client.media;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.serialization.MapCodec;
import io.github.flapjackpants.discordbridge.media.MediaGeometry;
import io.github.flapjackpants.discordbridge.media.MediaRef;
import io.github.flapjackpants.discordbridge.text.EmojiRef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.gui.font.PlainTextRenderable;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.objects.ObjectInfo;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

/** Inline emoji glyphs. Render thread. */
public final class EmojiGlyphs {
	private static final GlyphInfo ADVANCE = GlyphInfo.simple(9f);

	private EmojiGlyphs() {}

	public record EmojiFont(MediaRef ref) implements FontDescription {}

	/** Client-only, never serialized. */
	public record EmojiObject(EmojiRef emoji, MediaRef ref) implements ObjectInfo {
		@Override
		public FontDescription fontDescription() {
			return new EmojiFont(ref);
		}

		@Override
		public String defaultFallback() {
			return emoji.text();
		}

		@Override
		public MapCodec<? extends ObjectInfo> codec() {
			return MapCodec.unit(this);
		}
	}

	public static MediaRef ref(EmojiRef e) {
		return new MediaRef(e.animated() ? MediaRef.Kind.GIF : MediaRef.Kind.STATIC, e.url(), null, 0, 0, -1,
				e.animated() ? "image/gif" : "image/png", "emoji");
	}

	public static GlyphSource source(EmojiFont f) {
		BakedGlyph glyph = new BakedGlyph() {
			@Override
			public GlyphInfo info() {
				return ADVANCE;
			}

			@Override
			public TextRenderable.@Nullable Styled createGlyph(float x, float y, int color, int shadowColor,
					Style style, float boldOffset, float shadowOffset) {
				try {
					ClientMediaManager.Drawable d = ClientMediaManager.get().request(f.ref());
					if (d == null) {
						return null;
					}
					return new Instance(d, d.frame(ClientMediaManager.nowMs()), x, y, color, style);
				} catch (RuntimeException e) {
					return null;
				}
			}
		};
		return new GlyphSource() {
			@Override
			public BakedGlyph getGlyph(int codepoint) {
				return glyph;
			}

			@Override
			public BakedGlyph getRandomGlyph(RandomSource random, int codepoint) {
				return glyph;
			}
		};
	}

	private record Instance(ClientMediaManager.Drawable d, int frame, float x, float y, int color, Style style)
			implements PlainTextRenderable {
		@Override
		public int shadowColor() {
			return 0;
		}

		@Override
		public float shadowOffset() {
			return 0f;
		}

		@Override
		public void renderSprite(Matrix4fc pose, VertexConsumer vc, int light, float offsetX, float offsetY, float z,
				int color) {
			MediaGeometry.Sheet s = d.sheet();
			int fw = s.frameWidth();
			int fh = s.frameHeight();
			float scale = 8f / Math.max(fw, fh);
			float dw = fw * scale;
			float dh = fh * scale;
			float x0 = offsetX + left() + (8 - dw) / 2f;
			float y0 = offsetY + top() + (8 - dh) / 2f;
			float x1 = x0 + dw;
			float y1 = y0 + dh;
			float u0 = s.u(frame) / (float) s.width();
			float u1 = (s.u(frame) + fw) / (float) s.width();
			float v0 = s.v(frame) / (float) s.height();
			float v1 = (s.v(frame) + fh) / (float) s.height();
			int tint = (color & 0xFF000000) | 0x00FFFFFF;
			vertex(pose, vc, light, tint, x0, y0, z, u0, v0);
			vertex(pose, vc, light, tint, x0, y1, z, u0, v1);
			vertex(pose, vc, light, tint, x1, y1, z, u1, v1);
			vertex(pose, vc, light, tint, x1, y0, z, u1, v0);
		}

		private static void vertex(Matrix4fc pose, VertexConsumer vc, int light, int color, float x, float y, float z,
				float u, float v) {
			vc.addVertex(pose, x, y, z).setUv(u, v).setColor(color).setLight(light);
		}

		@Override
		public RenderType renderType(Font.DisplayMode mode) {
			return GlyphRenderTypes.createForColorTexture(d.texture()).select(mode);
		}

		@Override
		public RenderPipeline guiPipeline() {
			return GlyphRenderTypes.createForColorTexture(d.texture()).guiPipeline();
		}

		@Override
		public GpuTextureView textureView() {
			return Minecraft.getInstance().getTextureManager().getTexture(d.texture()).getTextureView();
		}
	}
}
