package io.github.flapjackpants.discordbridge.client.mixin;

import io.github.flapjackpants.discordbridge.client.ClientSettings;
import io.github.flapjackpants.discordbridge.client.media.ChatMedia;
import io.github.flapjackpants.discordbridge.client.media.ChatMediaHost;
import io.github.flapjackpants.discordbridge.media.MediaLinks;
import java.net.URI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
	@Shadow private boolean handleComponentClicked(Style style, boolean insert) {
		throw new AssertionError();
	}

	@Inject(method = "mouseClicked", at = @At(value = "NEW", target = "net/minecraft/client/gui/ActiveTextCollector$ClickableStyleFinder"), cancellable = true)
	private void discordbridge$clickMedia(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		ChatMedia.MediaRow row = ChatMedia.hovered();
		if (row == null) {
			return;
		}
		if (row.hidden()) {
			ChatMedia.reveal(row.ref());
			ChatMedia.clearHover();
			((ChatMediaHost) Minecraft.getInstance().gui.hud.getChat()).discordbridge$refreshKeepScroll();
			cir.setReturnValue(true);
			return;
		}
		URI u = MediaLinks.openUri(row.ref(), ClientSettings.get().policy());
		if (u != null && handleComponentClicked(Style.EMPTY.withClickEvent(new ClickEvent.OpenUrl(u)), false)) {
			cir.setReturnValue(true);
		}
	}
}
