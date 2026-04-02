package dev.eministar.starbans.model;

import java.util.Locale;

public enum AppealStatus {
    NONE,
    OPEN,
    REVIEWING,
    ACCEPTED,
    DENIED;

    public static AppealStatus fromConfig(String input) {
        if (input == null || input.isBlank()) {
            return NONE;
        }

        try {
            return valueOf(input.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return NONE;
        }
    }
}
