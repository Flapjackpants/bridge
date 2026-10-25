package io.github.flapjackpants.discordbridge.client.mixin;

import io.github.flapjackpants.discordbridge.client.media.ChatMedia;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import java.util.Optional;
import net.minecraft.client.gui.Font;
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
	@Shadow @Final private Font font;
	@Shadow @Final private int globalMouseX;
	@Shadow @Final private int globalMouseY;

	@Shadow private boolean isMouseOver(int left, int top, int right, int bottom) {
		throw new AssertionError();
	}

	@Inject(method = "handleMessage", at = @At("HEAD"), cancellable = true)
	private void discordbridge$drawMedia(int textTop, float opacity, FormattedCharSequence message,
			CallbackInfoReturnable<Boolean> cir) {
		if (message instanceof ChatMedia.MediaRow row) {
			ChatMedia.drawRow(graphics, textTop, opacity, row, true);
			try {
				ChatMedia.Rect r = ChatMedia.hitRect(row, textTop);
				if (r != null && isMouseOver(r.left(), r.top(), r.right(), r.bottom())) {
					ChatMedia.setHovered(row);
					graphics.setTooltipForNextFrame(font, ChatMedia.tooltip(row), Optional.empty(), globalMouseX, globalMouseY);
					graphics.requestCursor(CursorTypes.POINTING_HAND);
				}
			} catch (RuntimeException e) {
				// hover is cosmetic; never throw into render
			}
			cir.setReturnValue(false);
		}
	}
}
