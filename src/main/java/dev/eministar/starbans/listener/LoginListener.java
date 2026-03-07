package dev.eministar.starbans.listener;

import dev.eministar.starbans.StarBans;
import dev.eministar.starbans.model.CaseRecord;
import dev.eministar.starbans.model.CommandActor;
import dev.eministar.starbans.model.PlayerIdentity;
import dev.eministar.starbans.service.IpUtil;
import dev.eministar.starbans.service.VpnCheckResult;
import dev.eministar.starbans.utils.LoggerUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

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
                    plugin.getDiscordWebhookService().send(
                            "vpn-detected",
                            "player", player.name(),
                            "ip", ipAddress,
                            "details", result.details(),
                            "risk", result.risk()
                    );

                    if (plugin.getConfig().getString("security.vpn-detection.action", "NOTE").equalsIgnoreCase("BLOCK")) {
                        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, plugin.getModerationService().buildVpnBlockScreen(ipAddress, result));
                    }
                }
            }
        } catch (Exception exception) {
            LoggerUtil.error("The login moderation checks failed.", exception);
        }
    }
}
