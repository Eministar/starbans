package dev.eministar.starbans.config;

import dev.eministar.starbans.utils.LoggerUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DiscordWebhookConfig {

    private static final String FILE_NAME = "discord-webhooks.yml";

    private final JavaPlugin plugin;
    private File file;
    private FileConfiguration configuration;
    private long lastModified;

    public DiscordWebhookConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public synchronized void reload() {
        BundledYamlConfigSynchronizer.SyncResult syncResult =
                BundledYamlConfigSynchronizer.synchronize(plugin, FILE_NAME, this::importLegacyConfiguration);

        file = syncResult.file();
        configuration = syncResult.configuration();
        if (syncResult.customChangesApplied()) {
            LoggerUtil.info("Migrated legacy Discord webhook settings from config.yml into discord-webhooks.yml.");
        }

        String syncMessage = BundledYamlConfigSynchronizer.describe(FILE_NAME, syncResult);
        if (syncMessage != null) {
            LoggerUtil.info(syncMessage);
        }

        lastModified = file.exists() ? file.lastModified() : 0L;
    }

    public synchronized FileConfiguration getConfiguration() {
        ensureFresh();
        return configuration;
    }

    public synchronized void set(String path, Object value) {
        ensureFresh();
        configuration.set(path, value);
        save();
    }

    public synchronized void saveNow() {
        ensureFresh();
        save();
    }

    private boolean importLegacyConfiguration(FileConfiguration target, boolean created) {
        if (!created) {
            return false;
        }

        ConfigurationSection legacy = plugin.getConfig().getConfigurationSection("discord-webhooks");
        if (legacy == null || legacy.getValues(true).isEmpty()) {
            return false;
        }

        for (Map.Entry<String, Object> entry : legacy.getValues(true).entrySet()) {
            if (entry.getValue() instanceof ConfigurationSection) {
                continue;
            }
            target.set(entry.getKey(), copyValue(entry.getValue()));
        }
        return true;
    }

    private void ensureFresh() {
        if (file == null || !file.exists()) {
            return;
        }

        long currentLastModified = file.lastModified();
        if (currentLastModified != 0L && currentLastModified != lastModified) {
            reload();
        }
    }

    private void save() {
        try {
            configuration.save(file);
            lastModified = file.exists() ? file.lastModified() : 0L;
        } catch (IOException exception) {
            LoggerUtil.error("The Discord webhook configuration could not be saved.", exception);
        }
    }

    private Object copyValue(Object value) {
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object entry : list) {
                copy.add(copyValue(entry));
            }
            return copy;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), copyValue(entry.getValue()));
            }
            return copy;
        }
        return value;
    }
}
