package io.github.yourname.discordbridge.client.mixin;

import io.github.yourname.discordbridge.client.media.ChatMedia;
import java.util.List;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
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
