package dev.eministar.starbans.listener;

import dev.eministar.starbans.StarBans;
import dev.eministar.starbans.model.CaseRecord;
import dev.eministar.starbans.model.CommandActor;
import dev.eministar.starbans.model.PlayerIdentity;
import dev.eministar.starbans.model.PlayerProfile;
import dev.eministar.starbans.model.PlayerSummary;
import dev.eministar.starbans.service.AltDetectionService;
import dev.eministar.starbans.service.IpUtil;
import dev.eministar.starbans.service.VpnCheckResult;
import dev.eministar.starbans.utils.LoggerUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class LoginListener implements Listener {

    private final StarBans plugin;

    public LoginListener(StarBans plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
        String ipAddress = IpUtil.normalize(event.getAddress() == null ? "" : event.getAddress().getHostAddress());
        PlayerIdentity player = new PlayerIdentity(event.getUniqueId(), event.getName());

        try {
            if (plugin.getConfig().getBoolean("security.ip-tracking.store-last-ip", true)) {
                plugin.getModerationService().trackPlayer(player, ipAddress, System.currentTimeMillis());
            }

            Optional<CaseRecord> ipBlacklist = plugin.getModerationService().getActiveIpBlacklist(ipAddress);
            if (ipBlacklist.isPresent()) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, plugin.getModerationService().buildIpBlacklistScreen(ipBlacklist.get()));
                return;
            }

            Optional<CaseRecord> ipBan = plugin.getModerationService().getActiveIpBan(ipAddress);
            if (ipBan.isPresent()) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, plugin.getModerationService().buildIpBanScreen(ipBan.get()));
                return;
            }

            Optional<CaseRecord> playerBan = plugin.getModerationService().getActivePlayerBan(event.getUniqueId());
            if (playerBan.isPresent()) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, plugin.getModerationService().buildBanScreen(playerBan.get()));
                return;
            }

            if (plugin.getConfig().getBoolean("security.vpn-detection.enabled", false)) {
                VpnCheckResult result = plugin.getVpnCheckService().check(ipAddress);
                if (result.flagged()) {
                    if (plugin.getConfig().getBoolean("security.vpn-detection.create-note-on-detect", true)) {
                        plugin.getModerationService().addNote(
                                player,
                                CommandActor.system(),
                                plugin.getConfig().getString("security.vpn-detection.note-label", "vpn"),
                                plugin.getConfig().getString("security.vpn-detection.note-template", "VPN / proxy suspected") + " | " + result.details(),
                                "SYSTEM:VPN"
                        );
                    }
                    boolean blocked = plugin.getConfig().getString("security.vpn-detection.action", "NOTE").equalsIgnoreCase("BLOCK");
                    plugin.getDiscordWebhookService().send(
                            "vpn-detected",
                            "player", player.name(),
                            "player_uuid", player.uniqueId(),
                            "ip", ipAddress,
                            "details", result.details(),
                            "risk", result.risk(),
                            "action", blocked ? "BLOCK" : "NOTE",
                            "blocked", blocked
                    );
                    plugin.getServer().getScheduler().runTask(plugin, () -> plugin.getStaffAlertService().sendVpnAlert(player, ipAddress, result, blocked));

                    if (blocked) {
                        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, plugin.getModerationService().buildVpnBlockScreen(ipAddress, result));
                        return;
                    }
                }
            }

            notifyJoinAlerts(player, ipAddress);
        } catch (Exception exception) {
            LoggerUtil.error("The login moderation checks failed.", exception);
        }
    }

    private void notifyJoinAlerts(PlayerIdentity player, String ipAddress) throws Exception {
        if (!plugin.getConfig().getBoolean("staff-alerts.enabled", true)
                || !plugin.getConfig().getBoolean("staff-alerts.joins.enabled", true)) {
            return;
        }
        if (plugin.getJoinAlertExemptionService().isExempt(player, ipAddress)) {
            return;
        }

        PlayerSummary summary = plugin.getModerationService().getPlayerSummary(player);
        List<PlayerProfile> relatedProfiles = plugin.getModerationService().getRelatedProfiles(player.uniqueId());
        List<PlayerProfile> flaggedRelatedProfiles = collectFlaggedRelatedProfiles(relatedProfiles);
        AltDetectionService.AltAnalysisResult altAnalysis = analyzeAltPattern(player, ipAddress);

        boolean hasHistory = summary.visibleCaseCount() >= Math.max(1, plugin.getConfig().getInt("staff-alerts.joins.minimum-visible-cases", 1));
        boolean hasActiveMute = plugin.getConfig().getBoolean("staff-alerts.joins.notify-for-active-mutes", true)
                && summary.activeMute() != null;
        boolean hasActiveWatchlist = plugin.getConfig().getBoolean("staff-alerts.joins.notify-for-watchlist", true)
                && summary.activeWatchlist() != null;
        boolean hasFlaggedRelatedProfiles = plugin.getConfig().getBoolean("staff-alerts.joins.notify-for-flagged-related-accounts", true)
                && !flaggedRelatedProfiles.isEmpty();
        boolean hasAltDetection = altAnalysis != null && altAnalysis.flagged();

        if (!hasHistory && !hasActiveMute && !hasActiveWatchlist && !hasFlaggedRelatedProfiles && !hasAltDetection) {
            return;
        }

        String none = plugin.getLang().get("labels.none");
        String activeMuteReason = summary.activeMute() == null ? none : summary.activeMute().getReason();
        String activeMuteExpires = summary.activeMute() == null ? none : plugin.getModerationService().formatExpiry(summary.activeMute());
        String activeWatchlistReason = summary.activeWatchlist() == null ? none : summary.activeWatchlist().getReason();
        String lastCaseType = summary.latestCase() == null ? none : plugin.getModerationService().formatCaseType(summary.latestCase().getType());
        String lastCaseReason = summary.latestCase() == null ? none : summary.latestCase().getReason();
        String relatedPreview = buildRelatedPreview(
                hasAltDetection && !altAnalysis.suspiciousProfiles().isEmpty() ? altAnalysis.suspiciousProfiles() : flaggedRelatedProfiles,
                Math.max(1, plugin.getConfig().getInt("staff-alerts.joins.related-preview-limit", 3))
        );

        plugin.getDiscordWebhookService().send(
                "join-alert",
                "player", player.name(),
                "player_uuid", player.uniqueId(),
                "ip", ipAddress,
                "case_count", summary.visibleCaseCount(),
                "note_count", summary.noteCount(),
                "alt_count", summary.altFlagCount(),
                "warn_count", summary.warnCount(),
                "warning_points", summary.warningPoints(),
                "active_mute_reason", activeMuteReason,
                "active_mute_expires", activeMuteExpires,
                "active_watchlist_reason", activeWatchlistReason,
                "last_case_type", lastCaseType,
                "last_case_reason", lastCaseReason,
                "related_count", relatedProfiles.size(),
                "flagged_related_count", flaggedRelatedProfiles.size(),
                "related_players", relatedPreview,
                "alt_detection_score", altAnalysis == null ? 0 : altAnalysis.score(),
                "alt_detection_reasons", altAnalysis == null || altAnalysis.reasons().isEmpty() ? none : String.join("; ", altAnalysis.reasons()),
                "server_profile", plugin.getServerRuleService().getActiveProfileId()
        );
        plugin.getServer().getScheduler().runTask(
                plugin,
                () -> {
                    plugin.getStaffAlertService().sendJoinAlert(player, ipAddress, summary, relatedProfiles, flaggedRelatedProfiles, altAnalysis);
                    if (altAnalysis != null && altAnalysis.flagged()) {
                        plugin.getStaffAlertService().sendAltDetectionAlert(player, ipAddress, altAnalysis);
                    }
                }
        );
    }

    private List<PlayerProfile> collectFlaggedRelatedProfiles(List<PlayerProfile> relatedProfiles) throws Exception {
        int scanLimit = Math.max(0, plugin.getConfig().getInt("staff-alerts.joins.related-scan-limit", 10));
        if (scanLimit == 0 || relatedProfiles.isEmpty()) {
            return List.of();
        }

        List<PlayerProfile> flaggedProfiles = new ArrayList<>();
        int scanned = 0;
        for (PlayerProfile profile : relatedProfiles) {
            if (scanned >= scanLimit) {
                break;
            }
            scanned++;

            if (plugin.getModerationService().countPlayerCases(profile.getUniqueId()) > 0) {
                flaggedProfiles.add(profile);
            }
        }
        return flaggedProfiles;
    }

    private String buildRelatedPreview(List<PlayerProfile> profiles, int previewLimit) {
        if (profiles.isEmpty()) {
            return plugin.getLang().get("labels.none");
        }

        StringBuilder builder = new StringBuilder();
        int limit = Math.max(1, previewLimit);
        int size = Math.min(limit, profiles.size());
        for (int index = 0; index < size; index++) {
            if (index > 0) {
                builder.append(", ");
            }
            builder.append(profiles.get(index).getLastName());
        }

        int remaining = profiles.size() - size;
        if (remaining > 0) {
            builder.append(" +").append(remaining);
        }
        return builder.toString();
    }

    private AltDetectionService.AltAnalysisResult analyzeAltPattern(PlayerIdentity player, String ipAddress) throws Exception {
        if (!plugin.getConfig().getBoolean("alt-detection.enabled", true)) {
            return null;
        }

        AltDetectionService.AltAnalysisResult result = plugin.getAltDetectionService().analyze(player, ipAddress);
        if (!result.flagged()) {
            return result;
        }

        if (plugin.getConfig().getBoolean("alt-detection.auto-note.enabled", true)) {
            plugin.getModerationService().addNote(
                    player,
                    CommandActor.system(),
                    plugin.getConfig().getString("alt-detection.auto-note.label", "alt-detected"),
                    plugin.getConfig().getString("alt-detection.auto-note.template", "Suspicious alt pattern detected")
                            + " | score=" + result.score()
                            + " | reasons=" + String.join("; ", result.reasons()),
                    "SYSTEM:ALT-DETECTION"
            );
        }

        if (plugin.getConfig().getBoolean("alt-detection.auto-alt-flag.enabled", false) && !result.suspiciousProfiles().isEmpty()) {
            PlayerProfile related = result.suspiciousProfiles().get(0);
            plugin.getModerationService().addAltFlag(
                    player,
                    new PlayerIdentity(related.getUniqueId(), related.getLastName()),
                    CommandActor.system(),
                    plugin.getConfig().getString("alt-detection.auto-alt-flag.label", "auto-alt-detected"),
                    plugin.getConfig().getString("alt-detection.auto-alt-flag.note", "Automatically flagged by join analysis."),
                    "SYSTEM:ALT-DETECTION"
            );
        }

        plugin.getDiscordWebhookService().send(
                "alt-detected",
                "player", player.name(),
                "player_uuid", player.uniqueId(),
                "ip", ipAddress,
                "score", result.score(),
                "reasons", result.reasons().isEmpty() ? plugin.getLang().get("labels.none") : String.join("; ", result.reasons()),
                "related_count", result.relatedProfiles().size(),
                "suspicious_count", result.suspiciousProfiles().size(),
                "related_players", buildRelatedPreview(result.suspiciousProfiles(), Math.max(1, plugin.getConfig().getInt("staff-alerts.joins.related-preview-limit", 3)))
        );
        return result;
    }
}
