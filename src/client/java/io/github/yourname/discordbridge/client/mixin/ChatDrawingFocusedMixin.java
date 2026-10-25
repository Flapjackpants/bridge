package io.github.yourname.discordbridge.client.mixin;

import io.github.yourname.discordbridge.client.media.ChatMedia;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$DrawingFocusedGraphicsAccess")
public abstract class ChatDrawingFocusedMixin {
	@Shadow @Final private GuiGraphicsExtractor graphics;

	@Inject(method = "handleMessage", at = @At("HEAD"), cancellable = true)
	private void discordbridge$drawMedia(int textTop, float opacity, FormattedCharSequence message,
			CallbackInfoReturnable<Boolean> cir) {
		if (message instanceof ChatMedia.MediaRow row) {
			ChatMedia.drawRow(graphics, textTop, opacity, row, true);
			cir.setReturnValue(false);
		}
	}
}
