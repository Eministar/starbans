package dev.eministar.starbans.velocity.listener;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import dev.eministar.starbans.velocity.StarBansVelocityAddon;
import dev.eministar.starbans.velocity.network.BridgeEventType;
import dev.eministar.starbans.velocity.network.NetworkBridgePayload;

import java.nio.charset.StandardCharsets;

public final class BridgeMessageListener {

    private final Gson gson = new Gson();
    private final StarBansVelocityAddon plugin;

    public BridgeMessageListener(StarBansVelocityAddon plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!plugin.getConfig().getBoolean("bridge.enabled", true)) {
            return;
        }

        String configuredChannel = plugin.getConfig().getString("bridge.channel", "starbans:sync").toLowerCase();
        MinecraftChannelIdentifier identifier = MinecraftChannelIdentifier.from(configuredChannel);
        if (!event.getIdentifier().equals(identifier)) {
            return;
        }

        event.setResult(PluginMessageEvent.ForwardResult.handled());
        String payload = new String(event.getData(), StandardCharsets.UTF_8);
        NetworkBridgePayload message = parsePayload(payload);
        plugin.getServer().getScheduler().buildTask(plugin, () -> handlePayload(message)).schedule();
    }

    private NetworkBridgePayload parsePayload(String payload) {
        if (payload.equalsIgnoreCase("SYNC_NOW")) {
            return new NetworkBridgePayload(BridgeEventType.SYNC_NOW, null, null, null, System.currentTimeMillis());
        }

        try {
            NetworkBridgePayload message = gson.fromJson(payload, NetworkBridgePayload.class);
            if (message == null || message.type() == null) {
                return new NetworkBridgePayload(BridgeEventType.SYNC_NOW, null, null, null, System.currentTimeMillis());
            }
            return message;
        } catch (JsonSyntaxException exception) {
            plugin.getLogger().warn("Ignoring malformed StarBans bridge payload: {}", payload);
            return new NetworkBridgePayload(BridgeEventType.SYNC_NOW, null, null, null, System.currentTimeMillis());
        }
    }

    private void handlePayload(NetworkBridgePayload payload) {
        switch (payload.type()) {
            case SNAPSHOT_REFRESH -> plugin.getSharedNetworkSnapshotService().forceRefresh();
            case BAN_PLAYER -> plugin.getActiveBanEnforcer().enforcePlayer(payload.playerUniqueId());
            case BAN_IP, IP_BLACKLIST -> plugin.getActiveBanEnforcer().enforceIp(payload.ipAddress());
            case UNBAN_PLAYER, UNBAN_IP, IP_UNBLACKLIST -> {
            }
            case SYNC_NOW -> {
                plugin.getSharedNetworkSnapshotService().forceRefresh();
                plugin.getActiveBanEnforcer().enforceAll();
            }
        }
    }
}
