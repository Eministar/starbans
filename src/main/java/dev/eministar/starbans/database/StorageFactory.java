package dev.eministar.starbans.database;

import dev.eministar.starbans.StarBans;

public final class StorageFactory {

    private StorageFactory() {
    }

    public static ModerationStorage create(StarBans plugin) {
        StorageSettings settings = readSettings(plugin);
        return switch (settings.type()) {
            case JSON -> new JsonStorage(plugin, settings);
            case SQLITE, MARIADB -> new SqlStorage(plugin, settings);
        };
    }

    public static StorageSettings readSettings(StarBans plugin) {
        String tablePrefix = plugin.getConfig().getString("database.table-prefix", "starbans");
        tablePrefix = tablePrefix == null ? "starbans" : tablePrefix.replaceAll("[^A-Za-z0-9_]", "");
        if (tablePrefix.isBlank()) {
            tablePrefix = "starbans";
        }

        return new StorageSettings(
                StorageType.fromConfig(plugin.getConfig().getString("database.type", "SQLITE")),
                tablePrefix,
                plugin.getConfig().getString("database.json.file", "storage/database.json"),
                plugin.getConfig().getString("database.sqlite.file", "database/starbans.db"),
                plugin.getConfig().getString("database.mariadb.host", "127.0.0.1"),
                plugin.getConfig().getInt("database.mariadb.port", 3306),
                plugin.getConfig().getString("database.mariadb.database", "starbans"),
                plugin.getConfig().getString("database.mariadb.username", "root"),
                plugin.getConfig().getString("database.mariadb.password", ""),
                plugin.getConfig().getString("database.mariadb.parameters", "useUnicode=true&characterEncoding=utf8"),
                Math.max(1, plugin.getConfig().getInt("database.pool.maximum-pool-size", 8)),
                Math.max(0, plugin.getConfig().getInt("database.pool.minimum-idle", 2)),
                Math.max(1000L, plugin.getConfig().getLong("database.pool.connection-timeout-ms", 10000L))
        );
    }
}
