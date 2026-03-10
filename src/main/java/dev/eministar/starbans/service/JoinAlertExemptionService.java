package dev.eministar.starbans.service;

import dev.eministar.starbans.StarBans;
import dev.eministar.starbans.model.PlayerIdentity;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Locale;

public final class JoinAlertExemptionService {

    private final StarBans plugin;
    private final ServerRuleService serverRuleService;

    public JoinAlertExemptionService(StarBans plugin, ServerRuleService serverRuleService) {
        this.plugin = plugin;
        this.serverRuleService = serverRuleService;
    }

    public boolean isExempt(PlayerIdentity player, String ipAddress) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("staff-alerts.joins.exemptions");
        if (section == null || !section.getBoolean("enabled", true)) {
            return false;
        }

        String playerName = player.name() == null ? "" : player.name().toLowerCase(Locale.ROOT);
        String playerUuid = player.uniqueId() == null ? "" : player.uniqueId().toString();
        String normalizedIp = ipAddress == null ? "" : ipAddress.trim();
        String profileId = serverRuleService.getActiveProfileId();

        return containsIgnoreCase(section.getStringList("player-names"), playerName)
                || containsIgnoreCase(section.getStringList("player-uuids"), playerUuid)
                || containsIgnoreCase(section.getStringList("trusted-ips"), normalizedIp)
                || containsIgnoreCase(section.getStringList("server-profiles"), profileId);
    }

    private boolean containsIgnoreCase(List<String> input, String value) {
        for (String entry : input) {
            if (entry != null && entry.trim().equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }
}
