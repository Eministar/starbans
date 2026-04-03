package dev.eministar.starbans.discord;

import java.util.Locale;

public enum DiscordWorkflowOrigin {
    DISCORD,
    INGAME;

    public static DiscordWorkflowOrigin fromConfig(String input) {
        if (input == null || input.isBlank()) {
            return INGAME;
        }

        try {
            return valueOf(input.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return INGAME;
        }
    }
}
