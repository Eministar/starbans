package dev.eministar.starbans.velocity.database;

import dev.eministar.starbans.velocity.StarBansVelocityAddon;

public final class StorageFactory {

    private StorageFactory() {
    }

    public static VelocityStorage create(StarBansVelocityAddon plugin) {
        StorageSettings settings = StorageSettings.fromConfig(plugin.getConfig());
        return switch (settings.type()) {
            case JSON -> new JsonStorage(plugin, settings);
            case SQLITE, MARIADB -> new SqlStorage(plugin, settings);
        };
    }
}
