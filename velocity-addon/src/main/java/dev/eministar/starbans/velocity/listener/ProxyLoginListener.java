package dev.eministar.starbans.velocity.listener;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.proxy.Player;
import dev.eministar.starbans.velocity.StarBansVelocityAddon;
import dev.eministar.starbans.velocity.model.CaseRecord;
import dev.eministar.starbans.velocity.util.IpUtil;
import net.kyori.adventure.text.Component;

import java.net.InetAddress;
import java.util.Optional;

public final class ProxyLoginListener {

    private final StarBansVelocityAddon plugin;

    public ProxyLoginListener(StarBansVelocityAddon plugin) {
        this.plugin = plugin;
    }

    @Subscribe(order = PostOrder.EARLY)
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        InetAddress address = player.getRemoteAddress().getAddress();
        String ipAddress = IpUtil.normalize(address == null ? "" : address.getHostAddress());

        try {
            if (plugin.getConfig().getBoolean("tracking.store-last-ip", true)) {
                plugin.getModerationService().trackPlayer(player.getUniqueId(), player.getUsername(), ipAddress, System.currentTimeMillis());
            }
        } catch (Exception exception) {
            plugin.getLogger().error("Proxy profile tracking failed for {}.", player.getUsername(), exception);
        }

        try {
            if (plugin.getConfig().getBoolean("checks.ip-blacklist", true)) {
                Optional<CaseRecord> ipBlacklist = plugin.getModerationService().getActiveIpBlacklist(ipAddress);
                if (ipBlacklist.isPresent()) {
                    deny(event, plugin.getModerationService().buildIpBlacklistScreen(ipBlacklist.get()));
                    return;
                }
            }

            if (plugin.getConfig().getBoolean("checks.ip-ban", true)) {
                Optional<CaseRecord> ipBan = plugin.getModerationService().getActiveIpBan(ipAddress);
                if (ipBan.isPresent()) {
                    deny(event, plugin.getModerationService().buildIpBanScreen(ipBan.get()));
                    return;
                }
            }

            if (plugin.getConfig().getBoolean("checks.player-ban", true)) {
                Optional<CaseRecord> playerBan = plugin.getModerationService().getActivePlayerBan(player.getUniqueId());
                if (playerBan.isPresent()) {
                    deny(event, plugin.getModerationService().buildBanScreen(playerBan.get()));
                }
            }
        } catch (Exception exception) {
            plugin.getLogger().error("Proxy login moderation check failed.", exception);
            if (plugin.isFailClosedOnStorageError()) {
                deny(event, plugin.getStorageUnavailableText());
            }
        }
    }

    private void deny(LoginEvent event, String message) {
        event.setResult(ResultedEvent.ComponentResult.denied(plugin.getLang().component(message)));
    }
}
