package io.github.flapjackpants.discordbridge.client;

import io.github.flapjackpants.discordbridge.config.BridgeConfig;
import io.github.flapjackpants.discordbridge.config.ClientMediaConfig;
import java.util.Locale;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public final class ClientSettingsScreen extends Screen {
	private final @Nullable Screen parent;
	private final ClientMediaConfig working = ClientSettings.get().copy();
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);

	public ClientSettingsScreen(@Nullable Screen parent) {
		super(Component.translatable("discordbridge.config.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		layout.addTitleHeader(this.title, this.font);
		LinearLayout col = layout.addToContents(LinearLayout.vertical().spacing(4));

		LinearLayout prefixRow = col.addChild(LinearLayout.horizontal().spacing(4));
		Component prefixLabel = Component.translatable("discordbridge.config.prefix");
		prefixRow.addChild(new StringWidget(60, 20, prefixLabel, this.font));
		EditBox prefixBox = prefixRow.addChild(new EditBox(this.font, 136, 20, prefixLabel));
		prefixBox.setMaxLength(32);
		prefixBox.setValue(working.discordPrefix == null ? "" : working.discordPrefix);
		prefixBox.setResponder(v -> working.discordPrefix = v);

		LinearLayout colorRow = col.addChild(LinearLayout.horizontal().spacing(4));
		Component colorLabel = Component.translatable("discordbridge.config.color");
		colorRow.addChild(new StringWidget(60, 20, colorLabel, this.font));
		EditBox colorBox = colorRow.addChild(new EditBox(this.font, 136, 20, colorLabel));
		colorBox.setMaxLength(7);
		colorBox.setHint(Component.translatable("discordbridge.config.color.hint"));
		colorBox.setValue(working.discordColor == null ? "" : working.discordColor);
		colorBox.setTextColor(colorTextColor(colorBox.getValue()));
		colorBox.setResponder(v -> {
			working.discordColor = v;
			colorBox.setTextColor(colorTextColor(v));
		});

		col.addChild(Button.builder(modeLabel(), b -> {
			working.mode = working.mode().next();
			b.setMessage(modeLabel());
		}).width(200).build());

		col.addChild(new ValueSlider("discordbridge.config.maxHeight", (working.displayHeight() - 16) / 224.0,
				working::displayHeight,
				v -> working.maxDisplayHeight = 16 + (int) Math.round(v * 224)));

		col.addChild(new ValueSlider("discordbridge.config.memoryBudget", (working.memoryBudgetMiB() - 8) / 1016.0,
				working::memoryBudgetMiB,
				v -> working.memoryBudgetMiB = (int) Math.clamp(8L * Math.round((8 + v * 1016) / 8.0), 8, 1024)));
		col.addChild(new ValueSlider("discordbridge.config.maxDownload", (working.maxDownloadMiB() - 1) / 31.0,
				working::maxDownloadMiB,
				v -> working.maxDownloadBytes = (1 + (int) Math.round(v * 31)) * 1024 * 1024));

		col.addChild(Button.builder(toggleLabel("discordbridge.config.clickToReveal", working.clickToReveal), b -> {
			working.clickToReveal = !working.clickToReveal;
			b.setMessage(toggleLabel("discordbridge.config.clickToReveal", working.clickToReveal));
		}).width(200).build());

		col.addChild(Button.builder(toggleLabel("discordbridge.config.showInChatOverlay", working.showInChatOverlay), b -> {
			working.showInChatOverlay = !working.showInChatOverlay;
			b.setMessage(toggleLabel("discordbridge.config.showInChatOverlay", working.showInChatOverlay));
		}).width(200).build());

		layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).width(200).build());
		layout.visitWidgets(this::addRenderableWidget);
		repositionElements();
	}

	@Override
	protected void repositionElements() {
		layout.arrangeElements();
	}

	@Override
	public void onClose() {
		working.discordColor = BridgeConfig.parseHexColor(working.discordColor, -1) < 0
				? ClientMediaConfig.DEFAULT_DISCORD_COLOR
				: String.format("#%06X", BridgeConfig.parseHexColor(working.discordColor, 0));
		ClientSettings.apply(working);
		this.minecraft.setScreenAndShow(parent);
	}

	private static int colorTextColor(String hex) {
		return BridgeConfig.parseHexColor(hex, -1) < 0 ? 0xFFFF5555 : 0xFF000000 | BridgeConfig.parseHexColor(hex, 0);
	}

	private Component modeLabel() {
		return CommonComponents.optionNameValue(Component.translatable("discordbridge.config.mode"),
				Component.translatable("discordbridge.config.mode." + working.mode().name().toLowerCase(Locale.ROOT)));
	}

	private static Component toggleLabel(String key, boolean value) {
		return CommonComponents.optionStatus(Component.translatable(key), value);
	}

	private static final class ValueSlider extends AbstractSliderButton {
		private final String key;
		private final IntSupplier shown;
		private final java.util.function.DoubleConsumer setter;

		ValueSlider(String key, double initial, IntSupplier shown, java.util.function.DoubleConsumer setter) {
			super(0, 0, 200, 20, Component.empty(), initial);
			this.key = key;
			this.shown = shown;
			this.setter = setter;
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.translatable(key, shown.getAsInt()));
		}

		@Override
		protected void applyValue() {
			setter.accept(this.value);
		}
	}
}
