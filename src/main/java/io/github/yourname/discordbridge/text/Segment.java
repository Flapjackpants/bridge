package io.github.yourname.discordbridge.text;

import org.jspecify.annotations.Nullable;

public record Segment(String text, Role role, @Nullable String clickUrl, @Nullable String hoverText) {
}
