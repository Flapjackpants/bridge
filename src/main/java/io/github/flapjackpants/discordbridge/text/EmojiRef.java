package io.github.flapjackpants.discordbridge.text;

/** text = exact substring of the display body (":name:" or a Unicode cluster); url = image URL. */
public record EmojiRef(String text, String url, boolean animated) {
}
