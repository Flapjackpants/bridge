package io.github.yourname.discordbridge.client.mixin;

import io.github.yourname.discordbridge.client.media.ChatMedia;
import io.github.yourname.discordbridge.client.media.ChatMediaHost;
import java.util.List;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin implements ChatMediaHost {
	@Shadow @Final private Minecraft minecraft;
	@Shadow @Final private List<GuiMessage.Line> trimmedMessages;
	@Shadow private int chatScrollbarPos;
	@Shadow private boolean newMessageSinceScroll;

	@Shadow public abstract boolean isChatFocused();

	@Shadow public abstract void scrollChat(int amount);

	@Shadow private int getWidth() {
		throw new AssertionError();
	}

	@Shadow private double getScale() {
		throw new AssertionError();
	}

	@Shadow private void refreshTrimmedMessages() {
		throw new AssertionError();
	}

	@Override
	public void discordbridge$refreshKeepScroll() {
		int pos = chatScrollbarPos;
		chatScrollbarPos = 0;
		refreshTrimmedMessages();
		chatScrollbarPos = pos;
		scrollChat(0);
	}

	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V", at = @At("HEAD"))
	private void discordbridge$resetHover(GuiGraphicsExtractor graphics, Font font, int ticks, int mouseX, int mouseY,
			ChatComponent.DisplayMode displayMode, boolean changeCursorOnInsertions, CallbackInfo ci) {
		if (ChatMedia.consumeDirty()) {
			discordbridge$refreshKeepScroll();
		}
		if (displayMode.foreground) {
			ChatMedia.clearHover();
		}
	}

	@Redirect(method = "addMessageToDisplayQueue", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/chat/GuiMessage;splitLines(Lnet/minecraft/client/gui/Font;I)Ljava/util/List;"))
	private List<FormattedCharSequence> discordbridge$splitShown(GuiMessage message, Font font, int width) {
		Component shown = ChatMedia.displayText(message.content());
		if (shown == message.content()) {
			return message.splitLines(font, width);
		}
		return new GuiMessage(message.addedTime(), shown, message.signature(), message.source(), message.tag())
				.splitLines(font, width);
	}

	@Inject(method = "addMessageToDisplayQueue", at = @At("TAIL"))
	private void discordbridge$addMediaRows(GuiMessage message, CallbackInfo ci) {
		List<ChatMedia.MediaRow> rows = ChatMedia.rowsFor(message.content(),
				Mth.floor(getWidth() / getScale()), minecraft.options.chatLineSpacing().get());
		if (rows.isEmpty()) {
			return;
		}
		for (int i = 0; i < rows.size(); i++) {
			trimmedMessages.addFirst(new GuiMessage.Line(message, rows.get(i), i == rows.size() - 1));
			if (isChatFocused() && chatScrollbarPos > 0) {
				newMessageSinceScroll = true;
				scrollChat(1);
			}
		}
		while (trimmedMessages.size() > 100) {
			trimmedMessages.removeLast();
		}
	}
}
