# Discord Bridge

Fabric mod (Minecraft 26.3, Java 25) that links a dedicated server's chat to a Discord channel.

- Minecraft → Discord: chat (via webhook with the player's name and head avatar), join/leave, deaths, server start/stop.
- Discord → Minecraft: messages appear as colored system messages with a hover showing the Discord account and a clickable link to the message. Attachments appear as clickable `[image]` / `[file: name]` labels.
- Works with vanilla clients. Mass mentions (`@everyone`, `@here`) are neutralized in both directions.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/server/) for your Minecraft version.
2. Put `discordbridge-<version>.jar` and Fabric API into the server's `mods/` folder.
3. Start the server once. It writes `config/discordbridge-server.json` and logs one warning until the token and channel are set.

## Discord bot setup

1. Open the [Discord Developer Portal](https://discord.com/developers/applications) → **New Application**.
2. **Bot** tab → copy the token (**Reset Token** if needed).
3. On the same tab, enable **Message Content Intent**.
4. **OAuth2 → URL Generator**: scope `bot`; permissions **View Channel**, **Send Messages**, **Read Message History**, **Embed Links**, **Manage Webhooks**. Open the generated URL and invite the bot to your server.
5. In Discord enable Developer Mode (User Settings → Advanced), right-click the target channel → **Copy ID**.
6. Put the token and channel ID in the config (below), then run `discordbridge reload` from the console.

Without **Manage Webhooks** the bridge falls back to bot messages formatted as `**Name**: text`.

## Configuration

File: `config/discordbridge-server.json`. The token can instead come from the environment variable `DISCORDBRIDGE_TOKEN`, which takes precedence when non-blank.

| Key | Default | Meaning |
|-----|---------|---------|
| `token` | `""` | Bot token. Never share it. |
| `channelId` | `""` | Numeric ID of the bridged channel. |
| `enableInSingleplayer` | `false` | Run the bridge in singleplayer worlds. |
| `useWebhook` | `true` | Send chat through a webhook named `DiscordBridge`. |
| `avatarUrlTemplate` | `https://mc-heads.net/avatar/{uuid}/64` | Avatar URL; `{uuid}` and `{name}` are replaced. |
| `discordPrefix` | `[Discord] ` | Prefix on Discord messages shown to vanilla clients (modded clients use their own setting). |
| `prefixColor` | `#5865F2` | Prefix color. |
| `nameColor` | `#7289DA` | Author name color. |
| `messageColor` | `#FFFFFF` | Message text color. |
| `relayJoinLeave` | `true` | Relay joins and leaves. |
| `relayDeaths` | `true` | Relay player deaths. |
| `relayServerStatus` | `true` | Relay server start/stop. |
| `maxInboundLength` | `512` | Max characters of an inbound Discord message. |
| `maxInboundLines` | `5` | Max lines of an inbound Discord message. |
| `richMedia` | `true` | Send rich messages to modded clients that completed the handshake. |
| `mediaAllowedHosts` | Discord CDN/proxy, Tenor, Giphy, Imgur, Klipy | HTTPS hosts media URLs may use; `*.` prefix matches subdomains. File-only. |
| `mediaMaxBytes` | `8388608` | Larger attachments are not sent as media. File-only. |
| `mediaMaxDimension` | `8192` | Max pixels per side. File-only. |
| `mediaMaxItems` | `4` | Media items per message (hard cap 16). File-only. |
| `mediaPreviewSize` | `256` | Longest side in pixels of proxy-resized previews. File-only. |

Existing config files keep their saved `mediaAllowedHosts`; delete the key to pick up new defaults.
Existing server config files also keep their saved `discordPrefix`.

## Modded clients

Clients with the mod get rich messages (author, avatar, media list) after a `discordbridge:hello` v1 handshake.
Vanilla clients, and everyone when `richMedia` is `false`, see the same chat line as before.
Media URLs are limited to `mediaAllowedHosts`; the server never fetches media itself.
The client prefix and color below apply only when the server has `richMedia` on.

### Client settings

File: `config/discordbridge-client.json`. Editable in-game through [Mod Menu](https://modrinth.com/mod/modmenu) (optional): Mods → Discord Bridge → Configure. Esc and Done both save.

| Key | Default | Meaning |
|-----|---------|---------|
| `mode` | `ANIMATED` | `OFF` (no media fetched, text only), `STATIC` (first frame only), `ANIMATED`. |
| `maxDisplayHeight` | `90` | Max media height in GUI pixels, 16–240. |
| `memoryBudgetMiB` | `96` | Decoded-media memory budget, 8–1024. |
| `clickToReveal` | `false` | Hide media until clicked. |
| `showInChatOverlay` | `true` | Show media in the HUD chat, not only the open chat screen. |
| `discordPrefix` | `[Discord]` | Prefix before Discord messages; blank hides it. |
| `discordColor` | `#5865F2` | Prefix color (`#RRGGBB`). |

Other keys in the file are file-only.

## Commands

Require operator level (same as `/whitelist`).

- `/discordbridge status` — state, bot, channel, webhook, dropped messages, last error, config problems.
- `/discordbridge reload` — reload the config from disk and reconnect.
- `/discordbridge set <key> <value>` — change a setting (`richMedia` and every other settable key above; the `media*` keys are file-only, `token` is console only). `channelId` and `useWebhook` reconnect; the rest apply immediately.
- `/discordbridge token <token>` — console only; players are refused.

## Security

The bot token grants full control of the bot. Never share it, commit it, or type it in in-game chat. Set it in the config file, the `DISCORDBRIDGE_TOKEN` environment variable, or the server console. The mod never logs or echoes the token.

## Build

```
./gradlew build
```

The shaded jar is `build/libs/discordbridge-<version>.jar`.
# bridge
