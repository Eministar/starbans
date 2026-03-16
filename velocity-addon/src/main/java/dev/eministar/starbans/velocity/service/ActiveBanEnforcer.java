package dev.eministar.starbans.velocity.service;

import com.velocitypowered.api.proxy.Player;
import dev.eministar.starbans.velocity.StarBansVelocityAddon;
import dev.eministar.starbans.velocity.model.CaseRecord;
import dev.eministar.starbans.velocity.model.CaseType;
import dev.eministar.starbans.velocity.util.IpUtil;

import java.net.InetAddress;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ActiveBanEnforcer {

    private final StarBansVelocityAddon plugin;
    private final Map<Long, Long> processedKickCases = new ConcurrentHashMap<>();

    public ActiveBanEnforcer(StarBansVelocityAddon plugin) {
        this.plugin = plugin;
    }

    public void enforceAll() {
        cleanupProcessedKickCases(System.currentTimeMillis());
        for (Player player : plugin.getServer().getAllPlayers()) {
            enforcePlayer(player);
        }
    }

    public void enforcePlayer(UUID uniqueId) {
        if (uniqueId == null) {
            enforceAll();
            return;
        }

        plugin.getServer().getPlayer(uniqueId).ifPresent(this::enforcePlayer);
    }

    public void enforceIp(String ipAddress) {
        String normalizedIp = IpUtil.normalize(ipAddress);
        if (normalizedIp.isBlank()) {
            enforceAll();
            return;
        }

        for (Player player : plugin.getServer().getAllPlayers()) {
            if (normalizedIp.equals(resolveIpAddress(player))) {
                enforcePlayer(player);
            }
        }
    }

    public void enforceKick(UUID uniqueId, Long caseId) {
        if (uniqueId == null) {
            return;
        }

        plugin.getServer().getPlayer(uniqueId).ifPresent(player -> disconnectForKick(player, caseId));
    }

    public void enforcePlayer(Player player) {
        try {
            String ipAddress = resolveIpAddress(player);

            if (plugin.getConfig().getBoolean("checks.ip-blacklist", true)) {
                Optional<CaseRecord> ipBlacklist = plugin.getModerationService().getActiveIpBlacklist(ipAddress);
                if (ipBlacklist.isPresent()) {
                    player.disconnect(plugin.getLang().component(plugin.getModerationService().buildIpBlacklistScreen(ipBlacklist.get())));
                    return;
                }
            }

            if (plugin.getConfig().getBoolean("checks.ip-ban", true)) {
                Optional<CaseRecord> ipBan = plugin.getModerationService().getActiveIpBan(ipAddress);
                if (ipBan.isPresent()) {
                    player.disconnect(plugin.getLang().component(plugin.getModerationService().buildIpBanScreen(ipBan.get())));
                    return;
                }
            }

            if (plugin.getConfig().getBoolean("checks.player-ban", true)) {
                Optional<CaseRecord> playerBan = plugin.getModerationService().getActivePlayerBan(player.getUniqueId());
                if (playerBan.isPresent()) {
                    player.disconnect(plugin.getLang().component(plugin.getModerationService().buildBanScreen(playerBan.get())));
                    return;
                }
            }

            if (plugin.getConfig().getBoolean("sync.network-kick.enabled", true)) {
                disconnectForKick(player, null);
            }
        } catch (Exception exception) {
            plugin.getLogger().error("Active proxy ban enforcement failed for player {}.", player.getUsername(), exception);
            if (plugin.isFailClosedOnStorageError()) {
                player.disconnect(plugin.getLang().component(plugin.getStorageUnavailableText()));
            }
        }
    }

    private String resolveIpAddress(Player player) {
        InetAddress address = player.getRemoteAddress().getAddress();
        return IpUtil.normalize(address == null ? "" : address.getHostAddress());
    }

    private void disconnectForKick(Player player, Long caseId) {
        try {
            Optional<CaseRecord> kickRecord = caseId == null
                    ? plugin.getModerationService().getLatestPlayerKick(player.getUniqueId())
                    : plugin.getModerationService().getCase(caseId);
            if (kickRecord.isEmpty()) {
                return;
            }

            CaseRecord record = kickRecord.get();
            if (record.getType() != CaseType.KICK || !player.getUniqueId().equals(record.getTargetPlayerUniqueId())) {
                return;
            }

            long now = System.currentTimeMillis();
            long windowSeconds = Math.max(3L, plugin.getConfig().getLong("sync.network-kick.recent-window-seconds", 15L));
            if (record.getCreatedAt() < now - (windowSeconds * 1000L)) {
                return;
            }

            if (processedKickCases.putIfAbsent(record.getId(), now) != null) {
                return;
            }

            player.disconnect(plugin.getLang().component(plugin.getModerationService().buildKickScreen(record)));
        } catch (Exception exception) {
            plugin.getLogger().error("Active proxy kick enforcement failed for player {}.", player.getUsername(), exception);
            if (plugin.isFailClosedOnStorageError()) {
                player.disconnect(plugin.getLang().component(plugin.getStorageUnavailableText()));
            }
        }
    }

    private void cleanupProcessedKickCases(long now) {
        long maxAgeMillis = Math.max(30_000L, plugin.getConfig().getLong("sync.network-kick.recent-window-seconds", 15L) * 2000L);
        processedKickCases.entrySet().removeIf(entry -> now - entry.getValue() > maxAgeMillis);
    }
}
