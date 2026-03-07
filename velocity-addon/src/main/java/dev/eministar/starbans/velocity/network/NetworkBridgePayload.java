package dev.eministar.starbans.velocity.network;

import java.util.UUID;

public record NetworkBridgePayload(
        BridgeEventType type,
        UUID playerUniqueId,
        String ipAddress,
        Long caseId,
        long sentAt
) {
}
