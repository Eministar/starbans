package dev.eministar.starbans.model;

import java.util.Locale;

public enum CasePriority {
    LOW,
    NORMAL,
    HIGH,
    CRITICAL;

    public static CasePriority fromConfig(String input) {
        if (input == null || input.isBlank()) {
            return NORMAL;
        }

        try {
            return valueOf(input.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return NORMAL;
        }
    }
}
