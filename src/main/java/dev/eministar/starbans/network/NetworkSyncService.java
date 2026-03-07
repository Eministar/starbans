package dev.eministar.starbans.network;

import com.google.gson.Gson;
import dev.eministar.starbans.StarBans;
import dev.eministar.starbans.service.IpUtil;
import dev.eministar.starbans.utils.LoggerUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class NetworkSyncService {

    public static final String DEFAULT_CHANNEL = "starbans:sync";

    private final Gson gson = new Gson();
    private final StarBans plugin;
    private String channel;

    public NetworkSyncService(StarBans plugin) {
        this.plugin = plugin;
        this.channel = DEFAULT_CHANNEL;
    }

    public void reload() {
        unregister();
        channel = plugin.getConfig().getString("network.velocity-bridge.channel", DEFAULT_CHANNEL).toLowerCase();
        if (!isEnabled()) {
            return;
        }

        try {
            plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channel);
        } catch (Exception exception) {
            LoggerUtil.error("The Velocity bridge channel could not be registered.", exception);
        }
    }

    public void requestImmediateSync() {
        sendPayload(NetworkBridgePayload.syncNow());
    }

    public void requestSnapshotRefresh() {
        sendPayload(NetworkBridgePayload.snapshotRefresh());
    }

    public void notifyPlayerBan(UUID playerUniqueId, long caseId) {
        sendPayload(NetworkBridgePayload.player(BridgeEventType.BAN_PLAYER, playerUniqueId, caseId));
    }

    public void notifyPlayerUnban(UUID playerUniqueId, long caseId) {
        sendPayload(NetworkBridgePayload.player(BridgeEventType.UNBAN_PLAYER, playerUniqueId, caseId));
    }

    public void notifyIpBan(String ipAddress, long caseId) {
        sendPayload(NetworkBridgePayload.ip(BridgeEventType.BAN_IP, IpUtil.normalize(ipAddress), caseId));
    }

    public void notifyIpUnban(String ipAddress, long caseId) {
        sendPayload(NetworkBridgePayload.ip(BridgeEventType.UNBAN_IP, IpUtil.normalize(ipAddress), caseId));
    }

    public void notifyIpBlacklist(String ipAddress, long caseId) {
        sendPayload(NetworkBridgePayload.ip(BridgeEventType.IP_BLACKLIST, IpUtil.normalize(ipAddress), caseId));
    }

    public void notifyIpUnblacklist(String ipAddress, long caseId) {
        sendPayload(NetworkBridgePayload.ip(BridgeEventType.IP_UNBLACKLIST, IpUtil.normalize(ipAddress), caseId));
    }

    private void sendPayload(NetworkBridgePayload payload) {
        if (!isEnabled()) {
            return;
        }

        Runnable action = () -> dispatchPayload(payload);
        if (Bukkit.isPrimaryThread()) {
            action.run();
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, action);
    }

    private void dispatchPayload(NetworkBridgePayload payload) {
        byte[] bytes = gson.toJson(payload).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try {
            plugin.getServer().sendPluginMessage(plugin, channel, bytes);
            return;
        } catch (Exception ignored) {
        }

        Player carrier = plugin.getServer().getOnlinePlayers().stream().findFirst().orElse(null);
        if (carrier == null) {
            return;
        }

        try {
            carrier.sendPluginMessage(plugin, channel, bytes);
        } catch (Exception exception) {
            LoggerUtil.error("The Velocity bridge payload could not be sent.", exception);
        }
    }

    public void unregister() {
        try {
            plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin);
        } catch (Exception ignored) {
        }
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("network.velocity-bridge.enabled", false);
    }
}
