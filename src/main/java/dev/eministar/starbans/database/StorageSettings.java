package dev.eministar.starbans.database;

public record StorageSettings(StorageType type,
                              String table,
                              String jsonFileName,
                              String sqliteFileName,
                              String mariaHost,
                              int mariaPort,
                              String mariaDatabase,
                              String mariaUsername,
                              String mariaPassword,
                              String mariaParameters,
                              int maximumPoolSize,
                              int minimumIdle,
                              long connectionTimeoutMillis) {
}
