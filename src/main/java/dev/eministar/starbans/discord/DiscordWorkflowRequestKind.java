package dev.eministar.starbans.discord;

import java.util.Locale;

public enum DiscordWorkflowRequestKind {
    APPEAL,
    UNBAN_REQUEST;

    public static DiscordWorkflowRequestKind fromConfig(String input) {
        if (input == null || input.isBlank()) {
            return APPEAL;
        }

        try {
            return valueOf(input.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return APPEAL;
        }
    }
}
