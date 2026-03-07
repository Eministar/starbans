package dev.eministar.starbans.velocity.service;

import com.velocitypowered.api.proxy.Player;
import dev.eministar.starbans.velocity.StarBansVelocityAddon;
import dev.eministar.starbans.velocity.model.CaseRecord;
import dev.eministar.starbans.velocity.util.IpUtil;

import java.net.InetAddress;
import java.util.Optional;
import java.util.UUID;

public final class ActiveBanEnforcer {

    private final StarBansVelocityAddon plugin;

    public ActiveBanEnforcer(StarBansVelocityAddon plugin) {
        this.plugin = plugin;
    }

    public void enforceAll() {
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
                }
            }
        } catch (Exception exception) {
            plugin.getLogger().error("Active proxy ban enforcement failed for player {}.", player.getUsername(), exception);
        }
    }

    private String resolveIpAddress(Player player) {
        InetAddress address = player.getRemoteAddress().getAddress();
        return IpUtil.normalize(address == null ? "" : address.getHostAddress());
    }
}
