package io.github.flapjackpants.discordbridge.client.mixin;

import io.github.flapjackpants.discordbridge.client.media.EmojiGlyphs;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.network.chat.FontDescription;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.gui.font.FontManager$CachedFontProvider")
public abstract class FontManagerCachedFontProviderMixin {
	@Inject(method = "getGlyphSource", at = @At("HEAD"), cancellable = true)
	private void discordbridge$emojiSource(FontDescription description, CallbackInfoReturnable<GlyphSource> cir) {
		if (description instanceof EmojiGlyphs.EmojiFont f) {
			cir.setReturnValue(EmojiGlyphs.source(f));
		}
	}
}
