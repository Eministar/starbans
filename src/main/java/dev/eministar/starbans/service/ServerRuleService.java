package dev.eministar.starbans.service;

import dev.eministar.starbans.StarBans;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ServerRuleService {

    private final StarBans plugin;

    public ServerRuleService(StarBans plugin) {
        this.plugin = plugin;
    }

    public String getActiveProfileId() {
        String configured = plugin.getConfig().getString("server-rules.active-profile",
                plugin.getConfig().getString("settings.server-id", "default"));
        if (configured == null || configured.isBlank()) {
            return "default";
        }
        return configured.trim().toLowerCase(Locale.ROOT);
    }

    public String getDisplayName() {
        ConfigurationSection profile = getActiveProfileSection();
        if (profile == null) {
            return getActiveProfileId();
        }
        String displayName = profile.getString("display-name");
        return displayName == null || displayName.isBlank() ? getActiveProfileId() : displayName.trim();
    }

    public String decorateSource(String source) {
        String base = source == null || source.isBlank() ? "UNKNOWN" : source.trim();
        return base + '@' + getActiveProfileId();
    }

    public String resolveWebhookAction(String baseAction) {
        ConfigurationSection profile = getActiveProfileSection();
        if (profile == null) {
            return baseAction;
        }

        String override = profile.getString("webhook-actions." + baseAction, baseAction);
        return override == null || override.isBlank() ? baseAction : override.trim();
    }

    public List<String> getDefaultTags(String actionKey) {
        ConfigurationSection profile = getActiveProfileSection();
        if (profile == null) {
            return List.of();
        }

        List<String> tags = new ArrayList<>();
        tags.addAll(profile.getStringList("default-tags.global"));
        if (actionKey != null && !actionKey.isBlank()) {
            tags.addAll(profile.getStringList("default-tags." + actionKey.toLowerCase(Locale.ROOT)));
        }
        return tags.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    public String resolveDefaultText(String key, String fallbackPath, String fallback) {
        ConfigurationSection profile = getActiveProfileSection();
        if (profile != null) {
            String override = profile.getString("defaults." + key);
            if (override != null && !override.isBlank()) {
                return override;
            }
        }
        return plugin.getConfig().getString(fallbackPath, fallback);
    }

    public boolean resolveBoolean(String key, boolean fallback) {
        ConfigurationSection profile = getActiveProfileSection();
        if (profile != null && profile.contains("flags." + key)) {
            return profile.getBoolean("flags." + key, fallback);
        }
        return fallback;
    }

    public ConfigurationSection getActiveProfileSection() {
        return plugin.getConfig().getConfigurationSection("server-rules.profiles." + getActiveProfileId());
    }
}
