package dev.eministar.starbans.model;

public enum CaseVisibility {
    INTERNAL,
    PUBLIC;

    public static CaseVisibility fromConfig(String input) {
        if (input == null || input.isBlank()) {
            return INTERNAL;
        }

        try {
            return CaseVisibility.valueOf(input.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return INTERNAL;
        }
    }
}
