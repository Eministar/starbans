package dev.eministar.starbans.discord;

public record DiscordWorkflowRequest(long caseId,
                                     DiscordWorkflowRequestKind kind,
                                     DiscordWorkflowOrigin origin,
                                     String requesterDiscordUserId,
                                     String requesterDisplayName,
                                     String guildId,
                                     String staffChannelId,
                                     String staffMessageId,
                                     long createdAt,
                                     long updatedAt,
                                     Long decisionNotifiedAt) {

    public DiscordWorkflowRequest {
        kind = kind == null ? DiscordWorkflowRequestKind.APPEAL : kind;
        origin = origin == null ? DiscordWorkflowOrigin.INGAME : origin;
        requesterDiscordUserId = normalize(requesterDiscordUserId);
        requesterDisplayName = normalize(requesterDisplayName);
        guildId = normalize(guildId);
        staffChannelId = normalize(staffChannelId);
        staffMessageId = normalize(staffMessageId);
        createdAt = createdAt <= 0L ? System.currentTimeMillis() : createdAt;
        updatedAt = updatedAt <= 0L ? createdAt : updatedAt;
    }

    public DiscordWorkflowRequest withRequester(String discordUserId, String displayName, String updatedGuildId) {
        return new DiscordWorkflowRequest(
                caseId,
                kind,
                origin,
                discordUserId,
                displayName,
                updatedGuildId,
                staffChannelId,
                staffMessageId,
                createdAt,
                System.currentTimeMillis(),
                null
        );
    }

    public DiscordWorkflowRequest withStaffMessage(String updatedStaffChannelId, String updatedStaffMessageId) {
        return new DiscordWorkflowRequest(
                caseId,
                kind,
                origin,
                requesterDiscordUserId,
                requesterDisplayName,
                guildId,
                updatedStaffChannelId,
                updatedStaffMessageId,
                createdAt,
                System.currentTimeMillis(),
                decisionNotifiedAt
        );
    }

    public DiscordWorkflowRequest withDecisionNotified(long notifiedAt) {
        return new DiscordWorkflowRequest(
                caseId,
                kind,
                origin,
                requesterDiscordUserId,
                requesterDisplayName,
                guildId,
                staffChannelId,
                staffMessageId,
                createdAt,
                System.currentTimeMillis(),
                notifiedAt
        );
    }

    public DiscordWorkflowRequest refresh() {
        return new DiscordWorkflowRequest(
                caseId,
                kind,
                origin,
                requesterDiscordUserId,
                requesterDisplayName,
                guildId,
                staffChannelId,
                staffMessageId,
                createdAt,
                System.currentTimeMillis(),
                null
        );
    }

    private static String normalize(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        return input.trim();
    }
}
