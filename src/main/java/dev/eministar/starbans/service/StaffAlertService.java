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
                              List<PlayerProfile> flaggedRelatedProfiles,
                              AltDetectionService.AltAnalysisResult altAnalysis) {
        if (!plugin.getConfig().getBoolean("staff-alerts.enabled", true)
                || !plugin.getConfig().getBoolean("staff-alerts.joins.enabled", true)) {
            return;
        }

        CaseRecord activeMute = summary.activeMute();
        CaseRecord activeWatchlist = summary.activeWatchlist();
        CaseRecord latestCase = summary.latestCase();
        int previewLimit = Math.max(1, plugin.getConfig().getInt("staff-alerts.joins.related-preview-limit", 3));

        sendToStaff(
                "messages.staff-alert-join",
                "player", safe(player.name()),
                "ip", safe(ipAddress),
                "case_count", summary.visibleCaseCount(),
                "note_count", summary.noteCount(),
                "alt_count", summary.altFlagCount(),
                "warn_count", summary.warnCount(),
                "warning_points", summary.warningPoints(),
                "active_mute_reason", activeMute == null ? plugin.getLang().get("labels.none") : activeMute.getReason(),
                "active_mute_expires", activeMute == null ? plugin.getLang().get("labels.none") : plugin.getModerationService().formatExpiry(activeMute),
                "active_watchlist_reason", activeWatchlist == null ? plugin.getLang().get("labels.none") : activeWatchlist.getReason(),
                "last_case_type", latestCase == null ? plugin.getLang().get("labels.none") : plugin.getModerationService().formatCaseType(latestCase.getType()),
                "last_case_reason", latestCase == null ? plugin.getLang().get("labels.none") : latestCase.getReason(),
                "related_count", relatedProfiles.size(),
                "flagged_related_count", flaggedRelatedProfiles.size(),
                "related_players", buildRelatedPreview(flaggedRelatedProfiles, previewLimit),
                "alt_detection_score", altAnalysis == null ? 0 : altAnalysis.score(),
                "alt_detection_reasons", altAnalysis == null || altAnalysis.reasons().isEmpty() ? plugin.getLang().get("labels.none") : String.join("; ", altAnalysis.reasons())
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

    public void sendAltDetectionAlert(PlayerIdentity player, String ipAddress, AltDetectionService.AltAnalysisResult result) {
        if (!plugin.getConfig().getBoolean("staff-alerts.enabled", true)
                || !plugin.getConfig().getBoolean("staff-alerts.alt-detected.enabled", true)
                || result == null
                || !result.flagged()) {
            return;
        }

        sendToStaff(
                "messages.staff-alert-alt",
                "player", safe(player.name()),
                "ip", safe(ipAddress),
                "score", result.score(),
                "reasons", result.reasons().isEmpty() ? plugin.getLang().get("labels.none") : String.join("; ", result.reasons()),
                "related_count", result.relatedProfiles().size(),
                "suspicious_count", result.suspiciousProfiles().size(),
                "related_players", buildRelatedPreview(result.suspiciousProfiles(), Math.max(1, plugin.getConfig().getInt("staff-alerts.joins.related-preview-limit", 3)))
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
