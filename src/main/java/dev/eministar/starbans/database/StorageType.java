package dev.eministar.starbans.database;

public enum StorageType {
    JSON,
    SQLITE,
    MARIADB;

    public static StorageType fromConfig(String raw) {
        if (raw == null || raw.isBlank()) {
            return SQLITE;
        }

        for (StorageType value : values()) {
            if (value.name().equalsIgnoreCase(raw)) {
                return value;
            }
        }
        return SQLITE;
    }
}
