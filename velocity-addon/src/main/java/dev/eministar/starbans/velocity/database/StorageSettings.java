package dev.eministar.starbans.velocity.database;

import dev.eministar.starbans.velocity.config.YamlConfig;

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

    public static StorageSettings fromConfig(YamlConfig config) {
        return new StorageSettings(
                StorageType.fromName(config.getString("database.type", "MARIADB")),
                config.getString("database.table-prefix", "starbans"),
                config.getString("database.json.file", "storage/database.json"),
                config.getString("database.sqlite.file", "database/starbans.db"),
                config.getString("database.mariadb.host", "127.0.0.1"),
                config.getInt("database.mariadb.port", 3306),
                config.getString("database.mariadb.database", "starbans"),
                config.getString("database.mariadb.username", "root"),
                config.getString("database.mariadb.password", ""),
                config.getString("database.mariadb.parameters", ""),
                config.getInt("database.pool.maximum-pool-size", 6),
                config.getInt("database.pool.minimum-idle", 1),
                config.getLong("database.pool.connection-timeout-ms", 10000L)
        );
    }
}
