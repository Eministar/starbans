package dev.eministar.starbans.utils;

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
import java.util.List;

public final class Lang {

    private final JavaPlugin plugin;
    private File file;
    private FileConfiguration configuration;
    private long lastModified;

    public Lang(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        String configuredFile = plugin.getConfig().getString("settings.language-file", "lang-en.yml");
        if (configuredFile == null || configuredFile.isBlank()) {
            configuredFile = "lang-en.yml";
        }

        file = new File(plugin.getDataFolder(), configuredFile);
        ensureLanguageFileExists(configuredFile);

        configuration = YamlConfiguration.loadConfiguration(file);
        FileConfiguration defaults = loadDefaults(configuredFile);
        if (defaults != null) {
            configuration.setDefaults(defaults);
            configuration.options().copyDefaults(true);
            save();
        }

        lastModified = file.exists() ? file.lastModified() : 0L;
    }

    public String prefix() {
        return get("general.prefix");
    }

    public String get(String path, Object... replacements) {
        ensureFresh();
        String raw = configuration.getString(path, "");
        return ColorUtil.color(raw, replacements);
    }

    public List<String> getList(String path, Object... replacements) {
        ensureFresh();
        List<String> raw = configuration.getStringList(path);
        if (raw.isEmpty()) {
            String single = configuration.getString(path);
            if (single == null || single.isEmpty()) {
                return List.of();
            }
            raw = List.of(single);
        }
        return ColorUtil.color(new ArrayList<>(raw), replacements);
    }

    public String prefixed(String path, Object... replacements) {
        return prefix() + get(path, replacements);
    }

    public String getRaw(String path, String fallback) {
        ensureFresh();
        String raw = configuration.getString(path);
        return raw == null ? fallback : raw;
    }

    public List<String> getRawList(String path) {
        ensureFresh();
        List<String> raw = configuration.getStringList(path);
        if (raw.isEmpty()) {
            String single = configuration.getString(path);
            if (single == null || single.isEmpty()) {
                return List.of();
            }
            return List.of(single);
        }
        return new ArrayList<>(raw);
    }

    public List<String> prefixedList(String path, Object... replacements) {
        List<String> lines = getList(path, replacements);
        if (lines.isEmpty()) {
            return List.of();
        }

        List<String> output = new ArrayList<>(lines.size());
        String prefix = prefix();
        for (String line : lines) {
            output.add(prefix + line);
        }
        return output;
    }

    public String format(String input, Object... replacements) {
        return ColorUtil.color(input, replacements);
    }

    public List<String> format(List<String> lines, Object... replacements) {
        return ColorUtil.color(lines, replacements);
    }

    private void ensureLanguageFileExists(String configuredFile) {
        if (file.exists()) {
            return;
        }

        try {
            Files.createDirectories(file.toPath().getParent());
            InputStream resource = plugin.getResource(configuredFile);
            if (resource == null) {
                resource = plugin.getResource("lang-en.yml");
            }

            if (resource == null) {
                Files.createFile(file.toPath());
                return;
            }

            InputStream source = resource;
            try (source) {
                Files.copy(source, file.toPath());
            }
        } catch (IOException exception) {
            LoggerUtil.error("The language file '" + configuredFile + "' could not be created.", exception);
        }
    }

    private FileConfiguration loadDefaults(String configuredFile) {
        InputStream resource = plugin.getResource(configuredFile);
        if (resource == null && !"lang-en.yml".equalsIgnoreCase(configuredFile)) {
            resource = plugin.getResource("lang-en.yml");
        }
        if (resource == null) {
            return null;
        }

        try (InputStreamReader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (IOException exception) {
            LoggerUtil.error("The language defaults could not be loaded.", exception);
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
        } catch (IOException exception) {
            LoggerUtil.error("The language file could not be saved.", exception);
        }
    }
}
