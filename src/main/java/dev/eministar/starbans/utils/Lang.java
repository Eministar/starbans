package dev.eministar.starbans.utils;

import dev.eministar.starbans.config.BundledYamlConfigSynchronizer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
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

        String bundledResource = resolveBundledLanguageResource(configuredFile);
        BundledYamlConfigSynchronizer.SyncResult syncResult =
                BundledYamlConfigSynchronizer.synchronize(plugin, configuredFile, bundledResource);

        file = syncResult.file();
        configuration = syncResult.configuration();
        String syncMessage = BundledYamlConfigSynchronizer.describe(configuredFile, syncResult);
        if (syncMessage != null) {
            LoggerUtil.info(syncMessage);
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

    private String resolveBundledLanguageResource(String configuredFile) {
        if (plugin.getResource(configuredFile) != null) {
            return configuredFile;
        }

        if ("lang.de.yml".equalsIgnoreCase(configuredFile) || "lang-de.yml".equalsIgnoreCase(configuredFile)) {
            if (plugin.getResource("lang-de.yml") != null) {
                return "lang-de.yml";
            }
            if (plugin.getResource("lang.de.yml") != null) {
                return "lang.de.yml";
            }
        }
        return "lang-en.yml";
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
}
