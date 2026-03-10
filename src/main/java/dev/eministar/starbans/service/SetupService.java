package dev.eministar.starbans.service;

import dev.eministar.starbans.StarBans;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

public final class SetupService {

    private final StarBans plugin;

    public SetupService(StarBans plugin) {
        this.plugin = plugin;
    }

    public boolean setLanguageFile(String fileName) {
        plugin.getConfig().set("settings.language-file", fileName);
        plugin.saveConfig();
        return plugin.reloadPluginState();
    }

    public boolean setTimezone(String timezone) {
        plugin.getConfig().set("settings.timezone", timezone);
        plugin.saveConfig();
        return plugin.reloadPluginState();
    }

    public boolean setServerProfile(String profile) {
        plugin.getConfig().set("server-rules.active-profile", profile);
        plugin.saveConfig();
        return plugin.reloadPluginState();
    }

    public void setWebhookGlobalEnabled(boolean enabled) {
        plugin.getDiscordWebhookConfig().set("enabled", enabled);
        plugin.getDiscordWebhookConfig().reload();
    }

    public void setWebhookDefaultUrl(String url) {
        plugin.getDiscordWebhookConfig().set("default-url", url);
        plugin.getDiscordWebhookConfig().set("default-urls", List.of());
        plugin.getDiscordWebhookConfig().reload();
    }

    public void clearWebhookDefaultUrl() {
        plugin.getDiscordWebhookConfig().set("default-url", "");
        plugin.getDiscordWebhookConfig().set("default-urls", List.of());
        plugin.getDiscordWebhookConfig().reload();
    }

    public void setWebhookActionEnabled(String action, boolean enabled) {
        plugin.getDiscordWebhookConfig().set("actions." + action + ".enabled", enabled);
        plugin.getDiscordWebhookConfig().reload();
    }

    public void setWebhookActionUrl(String action, String url) {
        plugin.getDiscordWebhookConfig().set("actions." + action + ".url", url);
        plugin.getDiscordWebhookConfig().set("actions." + action + ".urls", List.of());
        plugin.getDiscordWebhookConfig().reload();
    }

    public void clearWebhookActionUrl(String action) {
        plugin.getDiscordWebhookConfig().set("actions." + action + ".url", "");
        plugin.getDiscordWebhookConfig().set("actions." + action + ".urls", List.of());
        plugin.getDiscordWebhookConfig().reload();
    }

    public List<String> getWebhookActions() {
        ConfigurationSection section = plugin.getDiscordWebhookConfig().getConfiguration().getConfigurationSection("actions");
        if (section == null) {
            return List.of();
        }
        return new ArrayList<>(section.getKeys(false));
    }

    public String describeWebhookAction(String action) {
        FileConfiguration configuration = plugin.getDiscordWebhookConfig().getConfiguration();
        String path = "actions." + action;
        String url = configuration.getString(path + ".url", "");
        if (url == null || url.isBlank()) {
            List<String> urls = configuration.getStringList(path + ".urls");
            url = urls.isEmpty() ? configuration.getString("default-url", "") : String.join(", ", urls);
        }
        if (url == null || url.isBlank()) {
            url = "not configured";
        }
        return "enabled=" + configuration.getBoolean(path + ".enabled", false) + " | url=" + url;
    }
}
