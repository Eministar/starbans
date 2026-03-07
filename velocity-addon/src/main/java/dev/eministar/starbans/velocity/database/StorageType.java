package dev.eministar.starbans.velocity.database;

public enum StorageType {
    JSON,
    SQLITE,
    MARIADB;

    public static StorageType fromName(String input) {
        if (input == null || input.isBlank()) {
            return MARIADB;
        }
        try {
            return StorageType.valueOf(input.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return MARIADB;
        }
    }
}
