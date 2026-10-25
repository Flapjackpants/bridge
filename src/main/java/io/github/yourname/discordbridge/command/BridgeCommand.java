package io.github.yourname.discordbridge.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.yourname.discordbridge.DiscordBridgeMod;
import io.github.yourname.discordbridge.config.BridgeConfig;
import io.github.yourname.discordbridge.discord.DiscordBridge;
import java.io.IOException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

public final class BridgeCommand {
	private BridgeCommand() {}

	public static void register(CommandDispatcher<CommandSourceStack> d) {
		d.register(Commands.literal("discordbridge")
				.requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
				.then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
				.then(Commands.literal("reload").executes(ctx -> reload(ctx.getSource())))
				.then(Commands.literal("set")
						.then(Commands.argument("key", StringArgumentType.word())
								.suggests((ctx, b) -> SharedSuggestionProvider.suggest(BridgeConfig.settableKeys(), b))
								.then(Commands.argument("value", StringArgumentType.greedyString())
										.executes(ctx -> set(ctx.getSource(),
												StringArgumentType.getString(ctx, "key"),
												StringArgumentType.getString(ctx, "value"))))))
				.then(Commands.literal("token")
						.then(Commands.argument("token", StringArgumentType.greedyString())
								.executes(ctx -> token(ctx.getSource(), StringArgumentType.getString(ctx, "token"))))));
	}

	private static void say(CommandSourceStack src, String text) {
		src.sendSuccess(() -> Component.literal(text), false);
	}

	private static int status(CommandSourceStack src) {
		DiscordBridge bridge = DiscordBridgeMod.bridge();
		if (bridge == null) {
			say(src, "Bridge not running (singleplayer disabled or server not started)");
		} else {
			DiscordBridge.Status s = bridge.status();
			say(src, "State: " + s.state());
			say(src, "Bot: " + (s.botTag() == null ? "-" : s.botTag()));
			say(src, "Channel: " + (s.channelName() == null ? "-" : s.channelName()));
			say(src, "Webhook active: " + (s.webhookActive() ? "yes" : "no"));
			say(src, "Dropped messages: " + s.dropped());
			say(src, "Last error: " + (s.lastError() == null ? "-" : s.lastError()));
		}
		for (String problem : DiscordBridgeMod.config().validate()) {
			say(src, "Config problem: " + problem);
		}
		return 1;
	}

	private static int reload(CommandSourceStack src) {
		say(src, "Reloading…");
		MinecraftServer server = src.getServer();
		DiscordBridgeMod.restart().whenComplete((v, err) -> server.execute(() -> {
			DiscordBridge bridge = DiscordBridgeMod.bridge();
			say(src, "Reloaded: " + (err != null ? "failed" : bridge == null ? "not running" : bridge.status().state()));
		}));
		return 1;
	}

	private static int set(CommandSourceStack src, String key, String value) {
		BridgeConfig cfg = DiscordBridgeMod.config();
		if (!cfg.setByKey(key, value)) {
			src.sendFailure(Component.literal("Unknown key or invalid value. Valid keys: "
					+ String.join(", ", BridgeConfig.settableKeys())));
			return 0;
		}
		try {
			cfg.save(DiscordBridgeMod.configPath());
		} catch (IOException e) {
			src.sendFailure(Component.literal("Set " + key + " in memory, but saving failed: " + e.getMessage()));
			return 0;
		}
		if (key.equals("channelId") || key.equals("useWebhook")) {
			DiscordBridgeMod.restart();
		}
		say(src, "Set " + key);
		return 1;
	}

	private static int token(CommandSourceStack src, String token) {
		if (src.isPlayer()) {
			src.sendFailure(Component.literal("For safety, set the token from the server console or config file."));
			return 0;
		}
		BridgeConfig cfg = DiscordBridgeMod.config();
		cfg.token = token.trim();
		try {
			cfg.save(DiscordBridgeMod.configPath());
		} catch (IOException e) {
			src.sendFailure(Component.literal("Saving the config failed: " + e.getMessage()));
			return 0;
		}
		DiscordBridgeMod.restart();
		say(src, "Token updated; reconnecting.");
		return 1;
	}
}
