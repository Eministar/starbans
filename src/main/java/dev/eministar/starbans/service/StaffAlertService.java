package dev.eministar.starbans.service;

import dev.eministar.starbans.StarBans;
import dev.eministar.starbans.model.CaseRecord;
import dev.eministar.starbans.model.PlayerIdentity;
import dev.eministar.starbans.model.PlayerProfile;
import dev.eministar.starbans.model.PlayerSummary;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

public final class StaffAlertService {

    private final StarBans plugin;

    public StaffAlertService(StarBans plugin) {
        this.plugin = plugin;
    }

    public void sendJoinAlert(PlayerIdentity player,
                              String ipAddress,
                              PlayerSummary summary,
                              List<PlayerProfile> relatedProfiles,
                              List<PlayerProfile> flaggedRelatedProfiles) {
        if (!plugin.getConfig().getBoolean("staff-alerts.enabled", true)
                || !plugin.getConfig().getBoolean("staff-alerts.joins.enabled", true)) {
            return;
        }

        CaseRecord activeMute = summary.activeMute();
        CaseRecord latestCase = summary.latestCase();
        int previewLimit = Math.max(1, plugin.getConfig().getInt("staff-alerts.joins.related-preview-limit", 3));

        sendToStaff(
                "messages.staff-alert-join",
                "player", safe(player.name()),
                "ip", safe(ipAddress),
                "case_count", summary.visibleCaseCount(),
                "note_count", summary.noteCount(),
                "alt_count", summary.altFlagCount(),
                "active_mute_reason", activeMute == null ? plugin.getLang().get("labels.none") : activeMute.getReason(),
                "active_mute_expires", activeMute == null ? plugin.getLang().get("labels.none") : plugin.getModerationService().formatExpiry(activeMute),
                "last_case_type", latestCase == null ? plugin.getLang().get("labels.none") : plugin.getModerationService().formatCaseType(latestCase.getType()),
                "last_case_reason", latestCase == null ? plugin.getLang().get("labels.none") : latestCase.getReason(),
                "related_count", relatedProfiles.size(),
                "flagged_related_count", flaggedRelatedProfiles.size(),
                "related_players", buildRelatedPreview(flaggedRelatedProfiles, previewLimit)
        );
    }

    public void sendVpnAlert(PlayerIdentity player, String ipAddress, VpnCheckResult result, boolean blocked) {
        if (!plugin.getConfig().getBoolean("staff-alerts.enabled", true)
                || !plugin.getConfig().getBoolean("staff-alerts.vpn-detected.enabled", true)) {
            return;
        }

        sendToStaff(
                "messages.staff-alert-vpn",
                "player", safe(player.name()),
                "ip", safe(ipAddress),
                "risk", String.valueOf(result.risk()),
                "details", safe(result.details()),
                "action", blocked ? "BLOCK" : "NOTE"
        );
    }

    private void sendToStaff(String path, Object... replacements) {
        List<String> lines = plugin.getLang().prefixedList(path, replacements);
        if (lines.isEmpty()) {
            return;
        }

        String permission = plugin.getConfig().getString("staff-alerts.permission", "starbans.alerts.receive");
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (permission == null || permission.isBlank() || online.hasPermission(permission) || online.hasPermission("starbans.admin")) {
                sendLines(online, lines);
            }
        }

        if (plugin.getConfig().getBoolean("staff-alerts.send-to-console", true)) {
            sendLines(Bukkit.getConsoleSender(), lines);
        }
    }

    private void sendLines(CommandSender sender, List<String> lines) {
        for (String line : lines) {
            sender.sendMessage(line);
        }
    }

    private String buildRelatedPreview(List<PlayerProfile> profiles, int previewLimit) {
        if (profiles.isEmpty()) {
            return plugin.getLang().get("labels.none");
        }

        String preview = profiles.stream()
                .limit(previewLimit)
                .map(PlayerProfile::getLastName)
                .collect(Collectors.joining(", "));
        int remaining = Math.max(0, profiles.size() - previewLimit);
        if (remaining <= 0) {
            return preview;
        }
        return preview + " +" + remaining;
    }

    private String safe(String input) {
        return input == null || input.isBlank() ? plugin.getLang().get("labels.none") : input;
    }
}
