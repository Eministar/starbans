package dev.eministar.starbans.config;

import dev.eministar.starbans.utils.LoggerUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
        file = new File(plugin.getDataFolder(), FILE_NAME);
        boolean created = ensureFileExists();

        configuration = YamlConfiguration.loadConfiguration(file);
        if (created && importLegacyConfiguration(configuration)) {
            LoggerUtil.info("Migrated legacy Discord webhook settings from config.yml into discord-webhooks.yml.");
        }

        FileConfiguration defaults = loadDefaults();
        if (defaults != null) {
            configuration.setDefaults(defaults);
            configuration.options().copyDefaults(true);
            save();
        } else {
            lastModified = file.exists() ? file.lastModified() : 0L;
        }
    }

    public synchronized FileConfiguration getConfiguration() {
        ensureFresh();
        return configuration;
    }

    private boolean ensureFileExists() {
        if (file.exists()) {
            return false;
        }

        try {
            Files.createDirectories(file.toPath().getParent());
            InputStream resource = plugin.getResource(FILE_NAME);
            if (resource == null) {
                Files.createFile(file.toPath());
                return true;
            }

            try (resource) {
                Files.copy(resource, file.toPath());
            }
            return true;
        } catch (IOException exception) {
            LoggerUtil.error("The Discord webhook configuration could not be created.", exception);
            return false;
        }
    }

    private boolean importLegacyConfiguration(FileConfiguration target) {
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

    private FileConfiguration loadDefaults() {
        InputStream resource = plugin.getResource(FILE_NAME);
        if (resource == null) {
            return null;
        }

        try (InputStreamReader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (IOException exception) {
            LoggerUtil.error("The Discord webhook defaults could not be loaded.", exception);
            return null;
        }
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
