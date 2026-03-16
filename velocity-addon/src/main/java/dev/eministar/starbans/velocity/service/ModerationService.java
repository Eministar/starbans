package dev.eministar.starbans.velocity.service;

import dev.eministar.starbans.velocity.StarBansVelocityAddon;
import dev.eministar.starbans.velocity.database.VelocityStorage;
import dev.eministar.starbans.velocity.model.CaseRecord;
import dev.eministar.starbans.velocity.model.CaseStatus;
import dev.eministar.starbans.velocity.model.CaseType;
import dev.eministar.starbans.velocity.model.PlayerProfile;
import dev.eministar.starbans.velocity.util.IpUtil;
import dev.eministar.starbans.velocity.util.TimeUtil;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ModerationService {

    private static final List<String> DEFAULT_PLAYER_BAN_SCREEN = List.of(
            "&8&m----------------------------------------",
            "&cYou are banned from this network",
            "",
            "&7Reason: &f{reason}",
            "&7Actor: &f{actor}",
            "&7Created: &f{created_at}",
            "&7Expires: &f{expires_at}",
            "&7Remaining: &f{remaining}",
            "&8&m----------------------------------------"
    );
    private static final List<String> DEFAULT_IP_BAN_SCREEN = List.of(
            "&8&m----------------------------------------",
            "&4Your IP is banned from this network",
            "",
            "&7Player: &f{player}",
            "&7IP: &f{ip}",
            "&7Reason: &f{reason}",
            "&7Actor: &f{actor}",
            "&7Expires: &f{expires_at}",
            "&8&m----------------------------------------"
    );
    private static final List<String> DEFAULT_IP_BLACKLIST_SCREEN = List.of(
            "&8&m----------------------------------------",
            "&4Your IP is blacklisted",
            "",
            "&7Reason: &f{reason}",
            "&7Actor: &f{actor}",
            "&8&m----------------------------------------"
    );
    private static final List<String> DEFAULT_KICK_SCREEN = List.of(
            "&8&m----------------------------------------",
            "&cYou were kicked from this network",
            "",
            "&7Reason: &f{reason}",
            "&7Actor: &f{actor}",
            "&8&m----------------------------------------"
    );

    private final StarBansVelocityAddon plugin;
    private final VelocityStorage storage;

    public ModerationService(StarBansVelocityAddon plugin, VelocityStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public Optional<CaseRecord> getActivePlayerBan(UUID uniqueId) throws Exception {
        return ensureNotExpired(storage.findActiveCaseForPlayer(uniqueId, CaseType.BAN));
    }

    public Optional<CaseRecord> getActivePlayerMute(UUID uniqueId) throws Exception {
        return ensureNotExpired(storage.findActiveCaseForPlayer(uniqueId, CaseType.MUTE));
    }

    public Optional<CaseRecord> getActiveIpBan(String ipAddress) throws Exception {
        return ensureNotExpired(storage.findActiveCaseForIp(IpUtil.normalize(ipAddress), CaseType.IP_BAN));
    }

    public Optional<CaseRecord> getActiveIpBlacklist(String ipAddress) throws Exception {
        return ensureNotExpired(storage.findActiveCaseForIp(IpUtil.normalize(ipAddress), CaseType.IP_BLACKLIST));
    }

    public Optional<CaseRecord> getLatestPlayerKick(UUID uniqueId) throws Exception {
        return storage.findLatestCaseForPlayer(uniqueId, CaseType.KICK);
    }

    public Optional<CaseRecord> getCase(long caseId) throws Exception {
        return storage.findCaseById(caseId);
    }

    public Optional<PlayerProfile> getProfile(UUID uniqueId) throws Exception {
        return storage.findPlayerProfile(uniqueId);
    }

    public Optional<PlayerProfile> resolveProfile(String input) throws Exception {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }

        try {
            return storage.findPlayerProfile(UUID.fromString(input));
        } catch (IllegalArgumentException ignored) {
        }

        Optional<PlayerProfile> exact = storage.findPlayerProfileByName(input);
        if (exact.isPresent()) {
            return exact;
        }

        List<PlayerProfile> matches = storage.searchProfilesByName(input, 5);
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(matches.getFirst());
    }

    public void trackPlayer(UUID uniqueId, String name, String ipAddress, long seenAt) throws Exception {
        String normalizedIp = IpUtil.normalize(ipAddress);
        PlayerProfile existing = storage.findPlayerProfile(uniqueId).orElse(new PlayerProfile(uniqueId, name, normalizedIp, seenAt, seenAt));
        storage.upsertPlayerProfile(existing.withSeen(name, normalizedIp, seenAt));
    }

    public String buildBanScreen(CaseRecord record) {
        return buildScreen("screens.player-ban", DEFAULT_PLAYER_BAN_SCREEN, record);
    }

    public String buildIpBanScreen(CaseRecord record) {
        return buildScreen("screens.ip-ban", DEFAULT_IP_BAN_SCREEN, record);
    }

    public String buildIpBlacklistScreen(CaseRecord record) {
        return buildScreen("screens.ip-blacklist", DEFAULT_IP_BLACKLIST_SCREEN, record);
    }

    public String buildKickScreen(CaseRecord record) {
        return buildScreen("screens.kick", DEFAULT_KICK_SCREEN, record);
    }

    public String formatDate(long epochMillis) {
        return TimeUtil.formatDate(plugin, epochMillis);
    }

    public String formatExpiry(CaseRecord record) {
        if (record.getExpiresAt() == null) {
            return plugin.getNetworkText("time.permanent", plugin.getLang().get("time.permanent", "&cPermanent"));
        }
        return formatDate(record.getExpiresAt());
    }

    public String formatRemaining(CaseRecord record) {
        return TimeUtil.formatRemaining(record.getExpiresAt(), plugin);
    }

    public Object[] replacements(CaseRecord record) {
        return new Object[]{
                "id", record.getId(),
                "label", defaultText(record.getLabel()),
                "player", defaultText(record.getTargetPlayerName()),
                "target_player", defaultText(record.getTargetPlayerName()),
                "related_player", defaultText(record.getRelatedPlayerName()),
                "ip", defaultText(record.getTargetIp()),
                "reason", defaultText(record.getReason()),
                "actor", defaultText(record.getActorName()),
                "source", defaultText(record.getSource()),
                "created_at", formatDate(record.getCreatedAt()),
                "expires_at", formatExpiry(record),
                "remaining", formatRemaining(record),
                "status", record.getStatus().name()
        };
    }

    private Optional<CaseRecord> ensureNotExpired(Optional<CaseRecord> input) throws Exception {
        if (input.isEmpty()) {
            return Optional.empty();
        }

        CaseRecord record = input.get();
        if (!record.isExpired(System.currentTimeMillis())) {
            return Optional.of(record);
        }

        storage.updateCaseStatus(
                record.getId(),
                CaseStatus.EXPIRED,
                System.currentTimeMillis(),
                null,
                "VELOCITY",
                "Case duration elapsed."
        );
        return Optional.empty();
    }

    private String defaultText(String input) {
        if (input != null && !input.isBlank()) {
            return input;
        }
        return plugin.getNetworkText("labels.none", plugin.getLang().get("labels.none", "&7none"));
    }

    private String buildScreen(String path, List<String> fallback, CaseRecord record) {
        return String.join("\n", plugin.getNetworkTextList(path, fallback, replacements(record)));
    }
}
