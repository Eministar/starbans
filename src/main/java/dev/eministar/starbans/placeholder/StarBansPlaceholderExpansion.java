package dev.eministar.starbans.placeholder;

import dev.eministar.starbans.StarBans;
import dev.eministar.starbans.model.PlayerIdentity;
import dev.eministar.starbans.model.PlayerSummary;
import dev.eministar.starbans.model.PluginStats;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StarBansPlaceholderExpansion extends PlaceholderExpansion {

    private static final long PLAYER_CACHE_TTL = 3_000L;
    private static final long STATS_CACHE_TTL = 5_000L;

    private final StarBans plugin;
    private final Map<UUID, CachedSummary> summaryCache = new ConcurrentHashMap<>();

    private volatile CachedStats cachedStats;

    public StarBansPlaceholderExpansion(StarBans plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "starbans";
    }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        String key = params == null ? "" : params.toLowerCase();
        try {
            return switch (key) {
                case "status" -> status(player);
                case "is_banned" -> isBanned(player);
                case "ban_reason" -> banReason(player);
                case "ban_remaining" -> banRemaining(player);
                case "mute_reason" -> muteReason(player);
                case "is_muted" -> isMuted(player);
                case "mute_remaining" -> muteRemaining(player);
                case "last_ip" -> lastIp(player);
                case "case_count" -> caseCount(player);
                case "note_count" -> noteCount(player);
                case "alt_count" -> altCount(player);
                case "last_case_type" -> lastCaseType(player);
                case "last_case_reason" -> lastCaseReason(player);
                case "active_bans" -> String.valueOf(stats().activeBans());
                case "active_ip_bans" -> String.valueOf(stats().activeIpBans());
                case "active_mutes" -> String.valueOf(stats().activeMutes());
                case "total_cases" -> String.valueOf(stats().totalCases());
                default -> "";
            };
        } catch (Exception exception) {
            return "";
        }
    }

    private String status(OfflinePlayer player) throws Exception {
        PlayerSummary summary = summary(player);
        if (summary == null) {
            return plugin.getLang().get("labels.none");
        }
        if (summary.activeBan() != null) {
            return plugin.getLang().get("labels.status-banned");
        }
        if (summary.activeMute() != null) {
            return plugin.getLang().get("labels.status-muted");
        }
        return plugin.getLang().get("labels.status-clean");
    }

    private String isBanned(OfflinePlayer player) throws Exception {
        PlayerSummary summary = summary(player);
        return summary != null && summary.activeBan() != null ? "true" : "false";
    }

    private String banReason(OfflinePlayer player) throws Exception {
        PlayerSummary summary = summary(player);
        return summary != null && summary.activeBan() != null ? summary.activeBan().getReason() : plugin.getLang().get("labels.none");
    }

    private String banRemaining(OfflinePlayer player) throws Exception {
        PlayerSummary summary = summary(player);
        return summary != null && summary.activeBan() != null ? plugin.getModerationService().formatRemaining(summary.activeBan()) : plugin.getLang().get("labels.none");
    }

    private String isMuted(OfflinePlayer player) throws Exception {
        PlayerSummary summary = summary(player);
        return summary != null && summary.activeMute() != null ? "true" : "false";
    }

    private String muteReason(OfflinePlayer player) throws Exception {
        PlayerSummary summary = summary(player);
        return summary != null && summary.activeMute() != null ? summary.activeMute().getReason() : plugin.getLang().get("labels.none");
    }

    private String muteRemaining(OfflinePlayer player) throws Exception {
        PlayerSummary summary = summary(player);
        return summary != null && summary.activeMute() != null ? plugin.getModerationService().formatRemaining(summary.activeMute()) : plugin.getLang().get("labels.none");
    }

    private String lastIp(OfflinePlayer player) throws Exception {
        PlayerSummary summary = summary(player);
        return summary == null || summary.lastKnownIp() == null ? plugin.getLang().get("labels.none") : summary.lastKnownIp();
    }

    private String caseCount(OfflinePlayer player) throws Exception {
        PlayerSummary summary = summary(player);
        return String.valueOf(summary == null ? 0 : summary.visibleCaseCount());
    }

    private String noteCount(OfflinePlayer player) throws Exception {
        PlayerSummary summary = summary(player);
        return String.valueOf(summary == null ? 0 : summary.noteCount());
    }

    private String altCount(OfflinePlayer player) throws Exception {
        PlayerSummary summary = summary(player);
        return String.valueOf(summary == null ? 0 : summary.altFlagCount());
    }

    private String lastCaseType(OfflinePlayer player) throws Exception {
        PlayerSummary summary = summary(player);
        return summary == null || summary.latestCase() == null ? plugin.getLang().get("labels.none") : plugin.getModerationService().formatCaseType(summary.latestCase().getType());
    }

    private String lastCaseReason(OfflinePlayer player) throws Exception {
        PlayerSummary summary = summary(player);
        return summary == null || summary.latestCase() == null ? plugin.getLang().get("labels.none") : summary.latestCase().getReason();
    }

    private PlayerSummary summary(OfflinePlayer player) throws Exception {
        if (player == null || player.getUniqueId() == null) {
            return null;
        }

        CachedSummary cached = summaryCache.get(player.getUniqueId());
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.createdAt < PLAYER_CACHE_TTL) {
            return cached.summary;
        }

        PlayerIdentity identity = new PlayerIdentity(player.getUniqueId(), player.getName() == null ? player.getUniqueId().toString() : player.getName());
        PlayerSummary loaded = plugin.getModerationService().getPlayerSummary(identity);
        summaryCache.put(player.getUniqueId(), new CachedSummary(now, loaded));
        return loaded;
    }

    private PluginStats stats() throws Exception {
        long now = System.currentTimeMillis();
        CachedStats local = cachedStats;
        if (local != null && now - local.createdAt < STATS_CACHE_TTL) {
            return local.stats;
        }

        PluginStats loaded = plugin.getModerationService().getStats();
        cachedStats = new CachedStats(now, loaded);
        return loaded;
    }

    private static final class CachedSummary {
        private final long createdAt;
        private final PlayerSummary summary;

        private CachedSummary(long createdAt, PlayerSummary summary) {
            this.createdAt = createdAt;
            this.summary = summary;
        }
    }

    private static final class CachedStats {
        private final long createdAt;
        private final PluginStats stats;

        private CachedStats(long createdAt, PluginStats stats) {
            this.createdAt = createdAt;
            this.stats = stats;
        }
    }
}
