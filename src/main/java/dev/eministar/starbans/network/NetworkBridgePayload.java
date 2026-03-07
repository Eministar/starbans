package dev.eministar.starbans.network;

import java.util.UUID;

public record NetworkBridgePayload(
        BridgeEventType type,
        UUID playerUniqueId,
        String ipAddress,
        Long caseId,
        long sentAt
) {

    public static NetworkBridgePayload syncNow() {
        return new NetworkBridgePayload(BridgeEventType.SYNC_NOW, null, null, null, System.currentTimeMillis());
    }

    public static NetworkBridgePayload snapshotRefresh() {
        return new NetworkBridgePayload(BridgeEventType.SNAPSHOT_REFRESH, null, null, null, System.currentTimeMillis());
    }

    public static NetworkBridgePayload player(BridgeEventType type, UUID playerUniqueId, Long caseId) {
        return new NetworkBridgePayload(type, playerUniqueId, null, caseId, System.currentTimeMillis());
    }

    public static NetworkBridgePayload ip(BridgeEventType type, String ipAddress, Long caseId) {
        return new NetworkBridgePayload(type, null, ipAddress, caseId, System.currentTimeMillis());
    }
}
