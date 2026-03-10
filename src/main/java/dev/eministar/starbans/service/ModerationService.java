package dev.eministar.starbans.service;

import dev.eministar.starbans.StarBans;
import dev.eministar.starbans.database.ModerationStorage;
import dev.eministar.starbans.discord.DiscordWebhookService;
import dev.eministar.starbans.model.CaseRecord;
import dev.eministar.starbans.model.CaseStatus;
import dev.eministar.starbans.model.CaseType;
import dev.eministar.starbans.model.CommandActor;
import dev.eministar.starbans.model.ModerationActionResult;
import dev.eministar.starbans.model.ModerationActionType;
import dev.eministar.starbans.model.PlayerIdentity;
import dev.eministar.starbans.model.PlayerProfile;
import dev.eministar.starbans.model.PlayerSummary;
import dev.eministar.starbans.model.PluginStats;
import dev.eministar.starbans.utils.Lang;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ModerationService {

    private final StarBans plugin;
    private final ModerationStorage storage;
    private final Lang lang;
    private final DiscordWebhookService webhookService;

    public ModerationService(StarBans plugin, ModerationStorage storage, Lang lang, DiscordWebhookService webhookService) {
        this.plugin = plugin;
        this.storage = storage;
        this.lang = lang;
        this.webhookService = webhookService;
    }

    public Optional<CaseRecord> getCase(long caseId) throws Exception {
        return storage.findCaseById(caseId);
    }

    public Optional<CaseRecord> getActivePlayerBan(UUID playerUniqueId) throws Exception {
        return ensureNotExpired(storage.findActiveCaseForPlayer(playerUniqueId, CaseType.BAN));
    }

    public Optional<CaseRecord> getActivePlayerMute(UUID playerUniqueId) throws Exception {
        return ensureNotExpired(storage.findActiveCaseForPlayer(playerUniqueId, CaseType.MUTE));
    }

    public Optional<CaseRecord> getActiveIpBan(String ipAddress) throws Exception {
        return ensureNotExpired(storage.findActiveCaseForIp(IpUtil.normalize(ipAddress), CaseType.IP_BAN));
    }

    public Optional<CaseRecord> getActiveIpBlacklist(String ipAddress) throws Exception {
        return ensureNotExpired(storage.findActiveCaseForIp(IpUtil.normalize(ipAddress), CaseType.IP_BLACKLIST));
    }

    public PlayerSummary getPlayerSummary(PlayerIdentity player) throws Exception {
        Optional<PlayerProfile> profile = storage.findPlayerProfile(player.uniqueId());
        return new PlayerSummary(
                player,
                profile.map(PlayerProfile::getLastIp).orElse(null),
                getActivePlayerBan(player.uniqueId()).orElse(null),
                getActivePlayerMute(player.uniqueId()).orElse(null),
                storage.findLatestCaseForPlayer(player.uniqueId()).orElse(null),
                storage.countVisibleCasesForPlayer(player.uniqueId()),
                storage.countCasesByTypeForPlayer(player.uniqueId(), CaseType.NOTE),
                storage.getActiveAltFlags(player.uniqueId()).size()
        );
    }

    public List<CaseRecord> getPlayerCases(UUID playerUniqueId, int pageSize, int page) throws Exception {
        int offset = Math.max(0, page) * Math.max(1, pageSize);
        return storage.getCasesForPlayer(playerUniqueId, Math.max(1, pageSize), offset);
    }

    public int countPlayerCases(UUID playerUniqueId) throws Exception {
        return storage.countVisibleCasesForPlayer(playerUniqueId);
    }

    public List<CaseRecord> getPlayerNotes(UUID playerUniqueId, int pageSize, int page) throws Exception {
        int offset = Math.max(0, page) * Math.max(1, pageSize);
        return storage.getCasesByTypeForPlayer(playerUniqueId, CaseType.NOTE, Math.max(1, pageSize), offset);
    }

    public List<CaseRecord> getRecentCases(int pageSize, int page) throws Exception {
        int offset = Math.max(0, page) * Math.max(1, pageSize);
        return storage.getRecentCases(Math.max(1, pageSize), offset);
    }

    public List<PlayerProfile> getKnownProfiles(int pageSize, int page) throws Exception {
        int offset = Math.max(0, page) * Math.max(1, pageSize);
        return storage.getKnownProfiles(Math.max(1, pageSize), offset);
    }

    public int countKnownProfiles() throws Exception {
        return storage.countKnownProfiles();
    }

    public List<CaseRecord> getAltFlags(UUID playerUniqueId) throws Exception {
        return storage.getActiveAltFlags(playerUniqueId);
    }

    public Optional<PlayerProfile> getProfile(UUID playerUniqueId) throws Exception {
        return storage.findPlayerProfile(playerUniqueId);
    }

    public List<PlayerProfile> getProfilesByIp(String ipAddress) throws Exception {
        return storage.findPlayerProfilesByIp(IpUtil.normalize(ipAddress));
    }

    public List<PlayerProfile> getRelatedProfiles(UUID playerUniqueId) throws Exception {
        Optional<PlayerProfile> profile = storage.findPlayerProfile(playerUniqueId);
        if (profile.isEmpty() || profile.get().getLastIp() == null || profile.get().getLastIp().isBlank()) {
            return List.of();
        }

        List<PlayerProfile> result = new ArrayList<>();
        for (PlayerProfile candidate : storage.findPlayerProfilesByIp(profile.get().getLastIp())) {
            if (!playerUniqueId.equals(candidate.getUniqueId())) {
                result.add(candidate);
            }
        }
        return result;
    }

    public List<PlayerProfile> searchKnownProfiles(String input, int limit) throws Exception {
        return storage.searchProfilesByName(input, limit);
    }

    public void trackPlayer(PlayerIdentity player, String ipAddress, long seenAt) throws Exception {
        String normalizedIp = IpUtil.normalize(ipAddress);
        PlayerProfile existing = storage.findPlayerProfile(player.uniqueId()).orElse(new PlayerProfile(player.uniqueId(), player.name(), normalizedIp, seenAt, seenAt));
        storage.upsertPlayerProfile(existing.withSeen(player.name(), normalizedIp, seenAt));
    }

    public ModerationActionResult banPlayer(PlayerIdentity target,
                                            CommandActor actor,
                                            String reason,
                                            Long expiresAt,
                                            String source) throws Exception {
        Optional<CaseRecord> active = getActivePlayerBan(target.uniqueId());
        if (active.isPresent()) {
            return new ModerationActionResult(ModerationActionType.ALREADY_ACTIVE, active.get());
        }

        String effectiveReason = sanitize(reason, plugin.getConfig().getString("punishments.defaults.ban-reason", "No reason specified."));
        CaseRecord stored = createAndStore(CaseType.BAN, null, target, null, null, actor, effectiveReason, source, expiresAt);
        notifyCreatedCase(stored);

        if (plugin.getConfig().getBoolean("punishments.player-ban.kick-online-player", true)) {
            Player online = Bukkit.getPlayer(target.uniqueId());
            if (online != null) {
                online.kickPlayer(buildBanScreen(stored));
            }
        }

        if (plugin.getConfig().getBoolean("broadcasts.player-ban.enabled", true)) {
            broadcast("messages.broadcasts.player-ban", recordReplacements(stored));
        }

        webhookService.send(stored.isTemporary() ? "tempban" : "ban", recordReplacements(stored));
        return new ModerationActionResult(ModerationActionType.CREATED, stored);
    }

    public ModerationActionResult unbanPlayer(PlayerIdentity target, CommandActor actor, String note) throws Exception {
        return resolveActivePlayerCase(target, actor, note, CaseType.BAN, "unban");
    }

    public ModerationActionResult mutePlayer(PlayerIdentity target,
                                             CommandActor actor,
                                             String reason,
                                             Long expiresAt,
                                             String source) throws Exception {
        Optional<CaseRecord> active = getActivePlayerMute(target.uniqueId());
        if (active.isPresent()) {
            return new ModerationActionResult(ModerationActionType.ALREADY_ACTIVE, active.get());
        }

        String effectiveReason = sanitize(reason, plugin.getConfig().getString("punishments.defaults.mute-reason", "No reason specified."));
        CaseRecord stored = createAndStore(CaseType.MUTE, null, target, null, null, actor, effectiveReason, source, expiresAt);

        Player online = Bukkit.getPlayer(target.uniqueId());
        if (online != null && plugin.getConfig().getBoolean("punishments.player-mute.notify-player", true)) {
            online.sendMessage(buildMuteMessage(stored));
        }

        if (plugin.getConfig().getBoolean("broadcasts.player-mute.enabled", false)) {
            broadcast("messages.broadcasts.player-mute", recordReplacements(stored));
        }

        webhookService.send(stored.isTemporary() ? "tempmute" : "mute", recordReplacements(stored));
        return new ModerationActionResult(ModerationActionType.CREATED, stored);
    }

    public ModerationActionResult unmutePlayer(PlayerIdentity target, CommandActor actor, String note) throws Exception {
        return resolveActivePlayerCase(target, actor, note, CaseType.MUTE, "unmute");
    }

    public ModerationActionResult banIp(String ipAddress,
                                        PlayerIdentity targetPlayer,
                                        CommandActor actor,
                                        String reason,
                                        Long expiresAt,
                                        String source) throws Exception {
        String normalizedIp = IpUtil.normalize(ipAddress);
        Optional<CaseRecord> active = getActiveIpBan(normalizedIp);
        if (active.isPresent()) {
            return new ModerationActionResult(ModerationActionType.ALREADY_ACTIVE, active.get());
        }

        String effectiveReason = sanitize(reason, plugin.getConfig().getString("punishments.defaults.ip-ban-reason", "No reason specified."));
        CaseRecord stored = createAndStore(CaseType.IP_BAN, null, targetPlayer, normalizedIp, null, actor, effectiveReason, source, expiresAt);
        notifyCreatedCase(stored);

        if (targetPlayer != null && plugin.getConfig().getBoolean("punishments.ip-ban.kick-online-player", true)) {
            Player online = Bukkit.getPlayer(targetPlayer.uniqueId());
            if (online != null) {
                online.kickPlayer(buildIpBanScreen(stored));
            }
        }

        if (plugin.getConfig().getBoolean("broadcasts.ip-ban.enabled", true)) {
            broadcast("messages.broadcasts.ip-ban", recordReplacements(stored));
        }

        webhookService.send(stored.isTemporary() ? "tempipban" : "ipban", recordReplacements(stored));
        return new ModerationActionResult(ModerationActionType.CREATED, stored);
    }

    public ModerationActionResult unbanIp(String ipAddress, CommandActor actor, String note) throws Exception {
        String normalizedIp = IpUtil.normalize(ipAddress);
        Optional<CaseRecord> active = getActiveIpBan(normalizedIp);
        if (active.isEmpty()) {
            return new ModerationActionResult(ModerationActionType.NOT_ACTIVE, null);
        }

        String effectiveNote = sanitize(note, plugin.getConfig().getString("punishments.defaults.unban-note", "No reason specified."));
        CaseRecord updated = storage.updateCaseStatus(active.get().getId(), CaseStatus.RESOLVED, System.currentTimeMillis(), actor.uniqueId(), actor.name(), effectiveNote);
        webhookService.send("unipban", recordReplacements(updated));
        notifyResolvedCase(updated);
        return new ModerationActionResult(ModerationActionType.REMOVED, updated);
    }

    public ModerationActionResult blacklistIp(String ipAddress, CommandActor actor, String reason, String source) throws Exception {
        String normalizedIp = IpUtil.normalize(ipAddress);
        Optional<CaseRecord> active = getActiveIpBlacklist(normalizedIp);
        if (active.isPresent()) {
            return new ModerationActionResult(ModerationActionType.ALREADY_ACTIVE, active.get());
        }

        String effectiveReason = sanitize(reason, plugin.getConfig().getString("punishments.defaults.ip-blacklist-reason", "No reason specified."));
        CaseRecord stored = createAndStore(CaseType.IP_BLACKLIST, null, null, normalizedIp, null, actor, effectiveReason, source, null);
        webhookService.send("ipblacklist", recordReplacements(stored));
        notifyCreatedCase(stored);
        return new ModerationActionResult(ModerationActionType.CREATED, stored);
    }

    public ModerationActionResult unblacklistIp(String ipAddress, CommandActor actor, String note) throws Exception {
        String normalizedIp = IpUtil.normalize(ipAddress);
        Optional<CaseRecord> active = getActiveIpBlacklist(normalizedIp);
        if (active.isEmpty()) {
            return new ModerationActionResult(ModerationActionType.NOT_ACTIVE, null);
        }

        String effectiveNote = sanitize(note, plugin.getConfig().getString("punishments.defaults.unban-note", "No reason specified."));
        CaseRecord updated = storage.updateCaseStatus(active.get().getId(), CaseStatus.RESOLVED, System.currentTimeMillis(), actor.uniqueId(), actor.name(), effectiveNote);
        webhookService.send("ipunblacklist", recordReplacements(updated));
        notifyResolvedCase(updated);
        return new ModerationActionResult(ModerationActionType.REMOVED, updated);
    }

    public ModerationActionResult addNote(PlayerIdentity target,
                                          CommandActor actor,
                                          String label,
                                          String note,
                                          String source) throws Exception {
        String effectiveLabel = sanitize(label, plugin.getConfig().getString("notes.default-label", "note"));
        String effectiveNote = sanitize(note, plugin.getConfig().getString("notes.default-text", "No additional note provided."));
        CaseRecord stored = createAndStore(CaseType.NOTE, effectiveLabel, target, null, null, actor, effectiveNote, source, null);
        webhookService.send("note", recordReplacements(stored));
        return new ModerationActionResult(ModerationActionType.CREATED, stored);
    }

    public ModerationActionResult addAltFlag(PlayerIdentity first,
                                             PlayerIdentity second,
                                             CommandActor actor,
                                             String label,
                                             String note,
                                             String source) throws Exception {
        for (CaseRecord existing : storage.getActiveAltFlags(first.uniqueId())) {
            boolean samePair = first.uniqueId().equals(existing.getTargetPlayerUniqueId()) && second.uniqueId().equals(existing.getRelatedPlayerUniqueId())
                    || second.uniqueId().equals(existing.getTargetPlayerUniqueId()) && first.uniqueId().equals(existing.getRelatedPlayerUniqueId());
            if (samePair && sanitize(label, "alt").equalsIgnoreCase(sanitize(existing.getLabel(), "alt"))) {
                return new ModerationActionResult(ModerationActionType.ALREADY_ACTIVE, existing);
            }
        }

        String effectiveLabel = sanitize(label, plugin.getConfig().getString("alt-flags.default-label", "alt-account"));
        String effectiveNote = sanitize(note, plugin.getConfig().getString("alt-flags.default-note", "Linked as a suspicious / alternate account."));
        CaseRecord stored = createAndStore(CaseType.ALT_FLAG, effectiveLabel, first, null, second, actor, effectiveNote, source, null);
        webhookService.send("altflag", recordReplacements(stored));
        return new ModerationActionResult(ModerationActionType.CREATED, stored);
    }

    public ModerationActionResult resolveCase(long caseId, CommandActor actor, String note) throws Exception {
        Optional<CaseRecord> existing = storage.findCaseById(caseId);
        if (existing.isEmpty()) {
            return new ModerationActionResult(ModerationActionType.NOT_FOUND, null);
        }
        if (existing.get().getStatus() != CaseStatus.ACTIVE) {
            return new ModerationActionResult(ModerationActionType.NOT_ACTIVE, existing.get());
        }

        String effectiveNote = sanitize(note, plugin.getConfig().getString("punishments.defaults.resolve-note", "Resolved manually."));
        CaseRecord updated = storage.updateCaseStatus(caseId, CaseStatus.RESOLVED, System.currentTimeMillis(), actor.uniqueId(), actor.name(), effectiveNote);
        webhookService.send("resolve", recordReplacements(updated));
        notifyResolvedCase(updated);
        return new ModerationActionResult(ModerationActionType.REMOVED, updated);
    }

    public ModerationActionResult kickPlayer(PlayerIdentity target,
                                             CommandActor actor,
                                             String reason,
                                             String source) throws Exception {
        String effectiveReason = sanitize(reason, plugin.getConfig().getString("punishments.defaults.kick-reason", "No reason specified."));
        CaseRecord stored = createAndStore(CaseType.KICK, null, target, null, null, actor, effectiveReason, source, null);
        Player online = Bukkit.getPlayer(target.uniqueId());
        if (online != null) {
            online.kickPlayer(buildKickScreen(stored));
        }

        if (plugin.getConfig().getBoolean("broadcasts.kick.enabled", false)) {
            broadcast("messages.broadcasts.kick", recordReplacements(stored));
        }

        webhookService.send("kick", recordReplacements(stored));
        return new ModerationActionResult(ModerationActionType.EXECUTED, stored);
    }

    public PluginStats getStats() throws Exception {
        return new PluginStats(
                storage.countActiveCases(CaseType.BAN),
                storage.countActiveCases(CaseType.IP_BAN),
                storage.countActiveCases(CaseType.MUTE),
                storage.countAllCases()
        );
    }

    public String buildBanScreen(CaseRecord record) {
        return buildScreen("screens.player-ban", record);
    }

    public String buildIpBanScreen(CaseRecord record) {
        return buildScreen("screens.ip-ban", record);
    }

    public String buildKickScreen(CaseRecord record) {
        return buildScreen("screens.kick", record);
    }

    public String buildMuteMessage(CaseRecord record) {
        return lang.prefixed(
                "messages.mute-blocked",
                "reason", record.getReason(),
                "actor", record.getActorName(),
                "expires_at", formatExpiry(record),
                "remaining", formatRemaining(record)
        );
    }

    public String buildIpBlacklistScreen(CaseRecord record) {
        return buildScreen("screens.ip-blacklist", record);
    }

    public String buildVpnBlockScreen(String ipAddress, VpnCheckResult result) {
        List<String> lines = lang.getList(
                "screens.vpn-block",
                "ip", ipAddress,
                "risk", result.risk(),
                "details", result.details()
        );
        return String.join("\n", lines);
    }

    public String formatRemaining(CaseRecord record) {
        return TimeUtil.formatRemaining(record.getExpiresAt(), lang);
    }

    public String formatExpiry(CaseRecord record) {
        if (record.getExpiresAt() == null) {
            return lang.get("time.permanent");
        }
        return formatDate(record.getExpiresAt());
    }

    public String formatDate(long epochMillis) {
        return TimeUtil.formatDate(plugin, epochMillis);
    }

    public String formatCaseType(CaseType type) {
        return lang.get("labels.case-type-" + type.name().toLowerCase().replace('_', '-'));
    }

    private ModerationActionResult resolveActivePlayerCase(PlayerIdentity target,
                                                           CommandActor actor,
                                                           String note,
                                                           CaseType type,
                                                           String webhookKey) throws Exception {
        Optional<CaseRecord> active = ensureNotExpired(storage.findActiveCaseForPlayer(target.uniqueId(), type));
        if (active.isEmpty()) {
            return new ModerationActionResult(ModerationActionType.NOT_ACTIVE, null);
        }

        String effectiveNote = sanitize(note, plugin.getConfig().getString("punishments.defaults.unban-note", "No reason specified."));
        CaseRecord updated = storage.updateCaseStatus(active.get().getId(), CaseStatus.RESOLVED, System.currentTimeMillis(), actor.uniqueId(), actor.name(), effectiveNote);
        webhookService.send(webhookKey, recordReplacements(updated));
        notifyResolvedCase(updated);
        return new ModerationActionResult(ModerationActionType.REMOVED, updated);
    }

    private Optional<CaseRecord> ensureNotExpired(Optional<CaseRecord> input) throws Exception {
        if (input.isEmpty()) {
            return Optional.empty();
        }

        CaseRecord record = input.get();
        if (!record.isExpired(System.currentTimeMillis())) {
            return Optional.of(record);
        }

        CaseRecord expired = storage.updateCaseStatus(
                record.getId(),
                CaseStatus.EXPIRED,
                System.currentTimeMillis(),
                null,
                "SYSTEM",
                plugin.getConfig().getString("punishments.defaults.expired-note", "Case duration elapsed.")
        );
        webhookService.send("expired", recordReplacements(expired));
        return Optional.empty();
    }

    private CaseRecord createAndStore(CaseType type,
                                      String label,
                                      PlayerIdentity target,
                                      String ipAddress,
                                      PlayerIdentity related,
                                      CommandActor actor,
                                      String reason,
                                      String source,
                                      Long expiresAt) throws Exception {
        ensureProfileExists(target);
        ensureProfileExists(related);
        CaseRecord record = CaseRecord.create(type, label, target, ipAddress, related, actor, reason, source, expiresAt);
        return storage.createCase(record);
    }

    private void ensureProfileExists(PlayerIdentity player) throws Exception {
        if (player == null || player.uniqueId() == null) {
            return;
        }

        Optional<PlayerProfile> existing = storage.findPlayerProfile(player.uniqueId());
        if (existing.isPresent()) {
            PlayerProfile profile = existing.get();
            if (player.name() != null && !player.name().isBlank() && !player.name().equals(profile.getLastName())) {
                storage.upsertPlayerProfile(new PlayerProfile(
                        profile.getUniqueId(),
                        player.name(),
                        profile.getLastIp(),
                        profile.getFirstSeen(),
                        profile.getLastSeen()
                ));
            }
            return;
        }

        long now = System.currentTimeMillis();
        storage.upsertPlayerProfile(new PlayerProfile(player.uniqueId(), player.name(), null, now, now));
    }

    private String buildScreen(String path, CaseRecord record) {
        List<String> lines = lang.getList(
                path,
                recordReplacements(record)
        );
        return String.join("\n", lines);
    }

    private void broadcast(String path, Object... replacements) {
        for (String line : lang.getList(path, replacements)) {
            Bukkit.broadcastMessage(line);
        }
    }

    public Object[] recordReplacements(CaseRecord record) {
        return new Object[]{
                "id", record.getId(),
                "case_id", record.getId(),
                "label", defaultString(record.getLabel()),
                "player", defaultString(record.getTargetPlayerName()),
                "target_player", defaultString(record.getTargetPlayerName()),
                "target_uuid", uuidString(record.getTargetPlayerUniqueId()),
                "related_player", defaultString(record.getRelatedPlayerName()),
                "related_uuid", uuidString(record.getRelatedPlayerUniqueId()),
                "ip", defaultString(record.getTargetIp()),
                "reason", defaultString(record.getReason()),
                "actor", defaultString(record.getActorName()),
                "actor_uuid", uuidString(record.getActorUniqueId()),
                "source", defaultString(record.getSource()),
                "created_at", formatDate(record.getCreatedAt()),
                "created_at_iso", toIsoTimestamp(record.getCreatedAt()),
                "expires_at", formatExpiry(record),
                "expires_at_iso", toIsoTimestamp(record.getExpiresAt()),
                "remaining", formatRemaining(record),
                "status", lang.get("labels.case-status-" + record.getStatus().name().toLowerCase()),
                "status_key", record.getStatus().name(),
                "status_changed_at", record.getStatusChangedAt() == null ? lang.get("labels.none") : formatDate(record.getStatusChangedAt()),
                "status_changed_at_iso", toIsoTimestamp(record.getStatusChangedAt()),
                "type", formatCaseType(record.getType()),
                "type_key", record.getType().name(),
                "status_actor", defaultString(record.getStatusActorName()),
                "status_actor_uuid", uuidString(record.getStatusActorUniqueId()),
                "temporary", String.valueOf(record.isTemporary()),
                "status_note", defaultString(record.getStatusNote())
        };
    }

    private String defaultString(String input) {
        return input == null || input.isBlank() ? lang.get("labels.none") : input;
    }

    private String sanitize(String input, String fallback) {
        if (input == null || input.isBlank()) {
            return fallback;
        }
        return input.trim();
    }

    private String toIsoTimestamp(Long epochMillis) {
        if (epochMillis == null) {
            return "";
        }
        return Instant.ofEpochMilli(epochMillis).toString();
    }

    private String uuidString(UUID value) {
        return value == null ? "" : value.toString();
    }

    private void notifyCreatedCase(CaseRecord record) {
        switch (record.getType()) {
            case BAN -> {
                if (record.getTargetPlayerUniqueId() != null) {
                    plugin.getNetworkSyncService().notifyPlayerBan(record.getTargetPlayerUniqueId(), record.getId());
                } else {
                    plugin.getNetworkSyncService().requestImmediateSync();
                }
            }
            case IP_BAN -> {
                if (record.getTargetIp() != null && !record.getTargetIp().isBlank()) {
                    plugin.getNetworkSyncService().notifyIpBan(record.getTargetIp(), record.getId());
                } else {
                    plugin.getNetworkSyncService().requestImmediateSync();
                }
            }
            case IP_BLACKLIST -> {
                if (record.getTargetIp() != null && !record.getTargetIp().isBlank()) {
                    plugin.getNetworkSyncService().notifyIpBlacklist(record.getTargetIp(), record.getId());
                } else {
                    plugin.getNetworkSyncService().requestImmediateSync();
                }
            }
            default -> {
            }
        }
    }

    private void notifyResolvedCase(CaseRecord record) {
        switch (record.getType()) {
            case BAN -> {
                if (record.getTargetPlayerUniqueId() != null) {
                    plugin.getNetworkSyncService().notifyPlayerUnban(record.getTargetPlayerUniqueId(), record.getId());
                } else {
                    plugin.getNetworkSyncService().requestImmediateSync();
                }
            }
            case IP_BAN -> {
                if (record.getTargetIp() != null && !record.getTargetIp().isBlank()) {
                    plugin.getNetworkSyncService().notifyIpUnban(record.getTargetIp(), record.getId());
                } else {
                    plugin.getNetworkSyncService().requestImmediateSync();
                }
            }
            case IP_BLACKLIST -> {
                if (record.getTargetIp() != null && !record.getTargetIp().isBlank()) {
                    plugin.getNetworkSyncService().notifyIpUnblacklist(record.getTargetIp(), record.getId());
                } else {
                    plugin.getNetworkSyncService().requestImmediateSync();
                }
            }
            default -> {
            }
        }
    }
}
