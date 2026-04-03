package dev.eministar.starbans.config;

import dev.eministar.starbans.utils.LoggerUtil;
import dev.eministar.starbans.utils.Version;
import org.bukkit.configuration.ConfigurationSection;
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

public final class BundledYamlConfigSynchronizer {

    public static final String VERSION_PATH = "config-version";

    private BundledYamlConfigSynchronizer() {
    }

    @FunctionalInterface
    public interface ConfigMutator {
        boolean apply(YamlConfiguration configuration, boolean created);
    }

    public record SyncResult(
            YamlConfiguration configuration,
            File file,
            boolean created,
            boolean changed,
            boolean versionChanged,
            boolean customChangesApplied,
            int addedKeys,
            String previousVersion,
            String targetVersion
    ) {
    }

    public static SyncResult synchronize(JavaPlugin plugin, String resourcePath) {
        return synchronize(plugin, resourcePath, resourcePath, (configuration, created) -> false);
    }

    public static SyncResult synchronize(JavaPlugin plugin, String targetPath, String defaultsResourcePath) {
        return synchronize(plugin, targetPath, defaultsResourcePath, (configuration, created) -> false);
    }

    public static SyncResult synchronize(JavaPlugin plugin, String resourcePath, ConfigMutator mutator) {
        return synchronize(plugin, resourcePath, resourcePath, mutator);
    }

    public static SyncResult synchronize(JavaPlugin plugin,
                                         String targetPath,
                                         String defaultsResourcePath,
                                         ConfigMutator mutator) {
        File file = new File(plugin.getDataFolder(), targetPath);
        boolean created = ensureFileExists(plugin, defaultsResourcePath, file);

        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        boolean customChangesApplied = mutator != null && mutator.apply(configuration, created);

        String previousVersion = normalizeVersion(configuration.getString(VERSION_PATH));
        YamlConfiguration defaults = loadDefaults(plugin, defaultsResourcePath);
        int addedKeys = defaults == null ? 0 : mergeMissingValues(configuration, defaults, true);
        String targetVersion = normalizeVersion(Version.get());
        if ((targetVersion == null || targetVersion.isBlank()) && defaults != null) {
            targetVersion = normalizeVersion(defaults.getString(VERSION_PATH, ""));
        }

        boolean versionChanged = syncVersion(configuration, previousVersion, targetVersion);
        boolean changed = customChangesApplied || addedKeys > 0 || versionChanged;
        if (changed) {
            save(configuration, file, targetPath);
        }

        return new SyncResult(
                configuration,
                file,
                created,
                changed,
                versionChanged,
                customChangesApplied,
                addedKeys,
                previousVersion,
                targetVersion
        );
    }

    public static String describe(String fileName, SyncResult result) {
        if (result == null || result.created()) {
            return null;
        }

        if (result.versionChanged() && result.addedKeys() > 0) {
            return fileName + " was updated from config version "
                    + readableVersion(result.previousVersion()) + " to "
                    + readableVersion(result.targetVersion()) + " and supplemented with "
                    + formatSettingCount(result.addedKeys()) + " without overwriting existing values.";
        }

        if (result.versionChanged()) {
            return fileName + " was updated from config version "
                    + readableVersion(result.previousVersion()) + " to "
                    + readableVersion(result.targetVersion()) + " without overwriting existing values.";
        }

        if (result.addedKeys() > 0) {
            return fileName + " was supplemented with " + formatSettingCount(result.addedKeys())
                    + " without overwriting existing values.";
        }

        return null;
    }

    private static boolean ensureFileExists(JavaPlugin plugin, String resourcePath, File file) {
        if (file.exists()) {
            return false;
        }

        try {
            Files.createDirectories(file.toPath().getParent());
            InputStream resource = plugin.getResource(resourcePath);
            if (resource == null) {
                Files.createFile(file.toPath());
                return true;
            }

            try (resource) {
                Files.copy(resource, file.toPath());
            }
            return true;
        } catch (IOException exception) {
            LoggerUtil.error("The configuration file '" + resourcePath + "' could not be created.", exception);
            return false;
        }
    }

    private static YamlConfiguration loadDefaults(JavaPlugin plugin, String resourcePath) {
        InputStream resource = plugin.getResource(resourcePath);
        if (resource == null) {
            return null;
        }

        try (InputStreamReader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (IOException exception) {
            LoggerUtil.error("The bundled defaults for '" + resourcePath + "' could not be loaded.", exception);
            return null;
        }
    }

    private static int mergeMissingValues(ConfigurationSection target, ConfigurationSection defaults, boolean root) {
        int addedKeys = 0;
        for (String key : defaults.getKeys(false)) {
            if (root && VERSION_PATH.equals(key)) {
                continue;
            }

            if (defaults.isConfigurationSection(key)) {
                ConfigurationSection defaultSection = defaults.getConfigurationSection(key);
                if (defaultSection == null) {
                    continue;
                }

                if (!target.contains(key)) {
                    ConfigurationSection createdSection = target.createSection(key);
                    addedKeys += mergeMissingValues(createdSection, defaultSection, false);
                    continue;
                }

                if (target.isConfigurationSection(key)) {
                    ConfigurationSection targetSection = target.getConfigurationSection(key);
                    if (targetSection != null) {
                        addedKeys += mergeMissingValues(targetSection, defaultSection, false);
                    }
                }
                continue;
            }

            if (!target.contains(key)) {
                target.set(key, copyValue(defaults.get(key)));
                addedKeys++;
            }
        }
        return addedKeys;
    }

    private static boolean syncVersion(YamlConfiguration configuration, String previousVersion, String targetVersion) {
        if (targetVersion == null || targetVersion.isBlank() || targetVersion.equals(previousVersion)) {
            return false;
        }

        configuration.set(VERSION_PATH, targetVersion);
        return true;
    }

    private static void save(YamlConfiguration configuration, File file, String resourcePath) {
        try {
            configuration.save(file);
        } catch (IOException exception) {
            LoggerUtil.error("The configuration file '" + resourcePath + "' could not be saved.", exception);
        }
    }

    private static String readableVersion(String version) {
        return version == null || version.isBlank() ? "unversioned" : version;
    }

    private static String formatSettingCount(int count) {
        return count + " missing setting" + (count == 1 ? "" : "s");
    }

    private static String normalizeVersion(String version) {
        if (version == null) {
            return "";
        }
        return version.trim();
    }

    private static Object copyValue(Object value) {
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
