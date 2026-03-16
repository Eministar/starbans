package dev.eministar.starbans.velocity.database;

import dev.eministar.starbans.velocity.StarBansVelocityAddon;
import dev.eministar.starbans.velocity.config.YamlConfig;

public final class StorageFactory {

    private StorageFactory() {
    }

    public static VelocityStorage create(StarBansVelocityAddon plugin) {
        return create(plugin, plugin.getConfig());
    }

    public static VelocityStorage create(StarBansVelocityAddon plugin, YamlConfig config) {
        StorageSettings settings = StorageSettings.fromConfig(config);
        return switch (settings.type()) {
            case JSON -> new JsonStorage(plugin, settings);
            case SQLITE, MARIADB -> new SqlStorage(plugin, settings);
        };
    }
}
