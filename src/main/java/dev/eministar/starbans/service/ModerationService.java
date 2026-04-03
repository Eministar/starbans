package dev.eministar.starbans.service;

import dev.eministar.starbans.StarBans;
import dev.eministar.starbans.database.ModerationStorage;
import dev.eministar.starbans.discord.DiscordWorkflowOrigin;
import dev.eministar.starbans.discord.DiscordWorkflowRequest;
import dev.eministar.starbans.discord.DiscordWorkflowRequestKind;
import dev.eministar.starbans.discord.DiscordWebhookService;
import dev.eministar.starbans.model.AppealStatus;
import dev.eministar.starbans.model.CaseComment;
import dev.eministar.starbans.model.CaseEvidence;
import dev.eministar.starbans.model.CasePriority;
import dev.eministar.starbans.model.CaseRecord;
import dev.eministar.starbans.model.CaseSearchFilter;
import dev.eministar.starbans.model.CaseStatus;
import dev.eministar.starbans.model.CaseType;
import dev.eministar.starbans.model.CaseVisibility;
import dev.eministar.starbans.model.CommandActor;
import dev.eministar.starbans.model.EvidenceType;
import dev.eministar.starbans.model.ModerationActionResult;
import dev.eministar.starbans.model.ModerationActionType;
import dev.eministar.starbans.model.PlayerIdentity;
import dev.eministar.starbans.model.PlayerProfile;
import dev.eministar.starbans.model.PlayerSummary;
import dev.eministar.starbans.model.PluginStats;
import dev.eministar.starbans.model.RiskAssessment;
import dev.eministar.starbans.utils.Lang;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
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

    public Optional<CaseRecord> getActiveWatchlist(UUID playerUniqueId) throws Exception {
        return ensureNotExpired(storage.findActiveCaseForPlayer(playerUniqueId, CaseType.WATCHLIST));
    }

    public Optional<CaseRecord> getActiveQuarantine(UUID playerUniqueId) throws Exception {
        return ensureNotExpired(storage.findActiveCaseForPlayer(playerUniqueId, CaseType.QUARANTINE));
    }

    public Optional<CaseRecord> getActiveIpBan(String ipAddress) throws Exception {
        return ensureNotExpired(storage.findActiveCaseForIp(IpUtil.normalize(ipAddress), CaseType.IP_BAN));
    }

    public Optional<CaseRecord> getActiveIpBlacklist(String ipAddress) throws Exception {
        return ensureNotExpired(storage.findActiveCaseForIp(IpUtil.normalize(ipAddress), CaseType.IP_BLACKLIST));
    }

    public PlayerSummary getPlayerSummary(PlayerIdentity player) throws Exception {
        Optional<PlayerProfile> profile = storage.findPlayerProfile(player.uniqueId());
        List<CaseRecord> activeWarnings = getActivePlayerCases(player.uniqueId(), CaseType.WARN);
        int warningPoints = activeWarnings.stream()
                .mapToInt(CaseRecord::getPoints)
                .sum();
        RiskAssessment risk = calculateRiskAssessment(player.uniqueId());
        return new PlayerSummary(
                player,
                profile.map(PlayerProfile::getLastIp).orElse(null),
                getActivePlayerBan(player.uniqueId()).orElse(null),
                getActivePlayerMute(player.uniqueId()).orElse(null),
                getActiveWatchlist(player.uniqueId()).orElse(null),
                getActiveQuarantine(player.uniqueId()).orElse(null),
                storage.findLatestCaseForPlayer(player.uniqueId()).orElse(null),
                storage.countVisibleCasesForPlayer(player.uniqueId()),
                storage.countCasesByTypeForPlayer(player.uniqueId(), CaseType.NOTE),
                storage.getActiveAltFlags(player.uniqueId()).size(),
                activeWarnings.size(),
                warningPoints,
                risk.score(),
                risk.level()
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

    public List<CaseRecord> searchCases(CaseSearchFilter filter, int pageSize, int page) throws Exception {
        int offset = Math.max(0, page) * Math.max(1, pageSize);
        return storage.searchCases(filter, Math.max(1, pageSize), offset);
    }

    public int countCases(CaseSearchFilter filter) throws Exception {
        return storage.countCases(filter);
    }

    public int countOpenAppeals() throws Exception {
        return storage.countCases(new CaseSearchFilter(null, null, null, null, null, null, null, null, null, null, AppealStatus.OPEN, null, null));
    }

    public List<CaseRecord> getOpenAppeals(int pageSize, int page) throws Exception {
        return searchCases(
                new CaseSearchFilter(null, null, null, null, null, null, null, null, null, null, AppealStatus.OPEN, null, null),
                pageSize,
                page
        );
    }

    public List<CaseRecord> getCasesByIds(Collection<Long> caseIds) throws Exception {
        if (caseIds == null || caseIds.isEmpty()) {
            return List.of();
        }

        List<CaseRecord> result = new ArrayList<>();
        for (Long caseId : caseIds) {
            if (caseId == null || caseId <= 0L) {
                continue;
            }
            storage.findCaseById(caseId).ifPresent(result::add);
        }
        result.sort(Comparator.comparingLong(CaseRecord::getCreatedAt).reversed());
        return List.copyOf(result);
    }

    public List<CaseRecord> getCasesByActor(String actorName, int pageSize, int page) throws Exception {
        int offset = Math.max(0, page) * Math.max(1, pageSize);
        return storage.getCasesByActor(actorName, Math.max(1, pageSize), offset);
    }

    public List<CaseRecord> getCasesByStatusActor(String actorName, int pageSize, int page) throws Exception {
        int offset = Math.max(0, page) * Math.max(1, pageSize);
        return storage.getCasesByStatusActor(actorName, Math.max(1, pageSize), offset);
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

    public List<CaseRecord> getActiveWarnings(UUID playerUniqueId) throws Exception {
        return getActivePlayerCases(playerUniqueId, CaseType.WARN);
    }

    public List<CaseRecord> getActiveReviews(UUID playerUniqueId) throws Exception {
        return getActivePlayerCases(playerUniqueId, CaseType.REVIEW);
    }

    public int getWarningPoints(UUID playerUniqueId) throws Exception {
        return getActiveWarnings(playerUniqueId).stream()
                .mapToInt(CaseRecord::getPoints)
                .sum();
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

    public RiskAssessment calculateRiskAssessment(UUID playerUniqueId) throws Exception {
        List<String> reasons = new ArrayList<>();
        int score = 0;

        int visibleCases = storage.countVisibleCasesForPlayer(playerUniqueId);
        if (visibleCases >= 10) {
            score += 30;
            reasons.add("extensive-history");
        } else if (visibleCases >= 5) {
            score += 15;
            reasons.add("repeat-history");
        }

        int warningPoints = getWarningPoints(playerUniqueId);
        if (warningPoints >= 6) {
            score += 25;
            reasons.add("warning-points-high");
        } else if (warningPoints >= 3) {
            score += 12;
            reasons.add("warning-points-medium");
        }

        if (getActiveWatchlist(playerUniqueId).isPresent()) {
            score += 20;
            reasons.add("watchlist");
        }

        if (getActiveQuarantine(playerUniqueId).isPresent()) {
            score += 30;
            reasons.add("quarantine");
        }

        int altCount = storage.getActiveAltFlags(playerUniqueId).size();
        if (altCount > 0) {
            score += Math.min(20, altCount * 5);
            reasons.add("alt-links");
        }

        String level = score >= 60 ? "CRITICAL" : score >= 35 ? "HIGH" : score >= 15 ? "MEDIUM" : "LOW";
        return new RiskAssessment(score, level, List.copyOf(reasons));
    }

    public void trackPlayer(PlayerIdentity player, String ipAddress, long seenAt) throws Exception {
        String normalizedIp = IpUtil.normalize(ipAddress);
        PlayerProfile existing = storage.findPlayerProfile(player.uniqueId()).orElse(new PlayerProfile(player.uniqueId(), player.name(), normalizedIp, seenAt, seenAt));
        storage.upsertPlayerProfile(existing.withSeen(player.name(), normalizedIp, seenAt));
    }

    public ModerationActionResult reportPlayer(PlayerIdentity reporter,
                                               PlayerIdentity target,
                                               String reason,
                                               CasePriority priority,
                                               String source) throws Exception {
        String effectiveReason = sanitize(reason, plugin.getConfig().getString("reports.default-reason", "Reported by player."));
        CaseRecord stored = createAndStore(
                CaseType.REPORT,
                plugin.getConfig().getString("reports.default-label", "player-report"),
                target,
                null,
                reporter,
                CommandActor.system(),
                effectiveReason,
                source,
                null,
                plugin.getConfig().getString("reports.default-category", "player-report"),
                null,
                List.of("report", "player-submitted"),
                0,
                CaseVisibility.INTERNAL,
                null,
                null,
                priority == null ? CasePriority.NORMAL : priority,
                null,
                null
        );
        sendWebhook("report-created", stored);
        return new ModerationActionResult(ModerationActionType.CREATED, stored);
    }

    public ModerationActionResult claimCase(long caseId, CommandActor actor) throws Exception {
        Optional<CaseRecord> existing = storage.findCaseById(caseId);
        if (existing.isEmpty()) {
            return new ModerationActionResult(ModerationActionType.NOT_FOUND, null);
        }

        CaseRecord updated = existing.get().withWorkflow(
                existing.get().getServerProfileId(),
                existing.get().getIncidentId(),
                existing.get().getPriority(),
                actor.uniqueId(),
                actor.name(),
                System.currentTimeMillis(),
                existing.get().getAppealStatus(),
                existing.get().getAppealDeadlineAt(),
                existing.get().getAppealChangedAt(),
                existing.get().getAppealActorUniqueId(),
                existing.get().getAppealActorName(),
                existing.get().getAppealNotes(),
                existing.get().getNextReviewAt(),
                existing.get().getLastReviewedAt(),
                existing.get().getReviewReason(),
                existing.get().getEvidence()
        );
        storage.updateCase(updated);
        sendWebhook("case-claimed", updated);
        syncWorkflowCase(updated);
        return new ModerationActionResult(ModerationActionType.UPDATED, updated);
    }

    public ModerationActionResult setCasePriority(long caseId, CommandActor actor, CasePriority priority) throws Exception {
        Optional<CaseRecord> existing = storage.findCaseById(caseId);
        if (existing.isEmpty()) {
            return new ModerationActionResult(ModerationActionType.NOT_FOUND, null);
        }

        CaseRecord updated = existing.get().withWorkflow(
                existing.get().getServerProfileId(),
                existing.get().getIncidentId(),
                priority == null ? CasePriority.NORMAL : priority,
                actor.uniqueId(),
                actor.name(),
                System.currentTimeMillis(),
                existing.get().getAppealStatus(),
                existing.get().getAppealDeadlineAt(),
                existing.get().getAppealChangedAt(),
                existing.get().getAppealActorUniqueId(),
                existing.get().getAppealActorName(),
                existing.get().getAppealNotes(),
                existing.get().getNextReviewAt(),
                existing.get().getLastReviewedAt(),
                existing.get().getReviewReason(),
                existing.get().getEvidence()
        );
        storage.updateCase(updated);
        sendWebhook("case-priority-updated", updated);
        syncWorkflowCase(updated);
        return new ModerationActionResult(ModerationActionType.UPDATED, updated);
    }

    public ModerationActionResult createIncident(String incidentId,
                                                 CommandActor actor,
                                                 String title,
                                                 String description,
                                                 CasePriority priority,
                                                 String source) throws Exception {
        String effectiveIncidentId = sanitize(incidentId, "incident-" + System.currentTimeMillis()).toLowerCase();
        String effectiveTitle = sanitize(title, "Incident");
        String effectiveDescription = sanitize(description, "No description provided.");
        CaseRecord stored = createAndStore(
                CaseType.INCIDENT,
                effectiveTitle,
                null,
                null,
                null,
                actor,
                effectiveDescription,
                source,
                null,
                plugin.getConfig().getString("incidents.default-category", "incident"),
                null,
                List.of("incident"),
                0,
                CaseVisibility.INTERNAL,
                null,
                effectiveIncidentId,
                priority == null ? CasePriority.HIGH : priority,
                null,
                null
        );
        sendWebhook("incident-created", stored);
        return new ModerationActionResult(ModerationActionType.CREATED, stored);
    }

    public ModerationActionResult linkCaseToIncident(long caseId, String incidentId, CommandActor actor) throws Exception {
        Optional<CaseRecord> existing = storage.findCaseById(caseId);
        if (existing.isEmpty()) {
            return new ModerationActionResult(ModerationActionType.NOT_FOUND, null);
        }

        CaseRecord updated = existing.get().withWorkflow(
                existing.get().getServerProfileId(),
                sanitize(incidentId, null),
                existing.get().getPriority(),
                actor.uniqueId(),
                actor.name(),
                System.currentTimeMillis(),
                existing.get().getAppealStatus(),
                existing.get().getAppealDeadlineAt(),
                existing.get().getAppealChangedAt(),
                existing.get().getAppealActorUniqueId(),
                existing.get().getAppealActorName(),
                existing.get().getAppealNotes(),
                existing.get().getNextReviewAt(),
                existing.get().getLastReviewedAt(),
                existing.get().getReviewReason(),
                existing.get().getEvidence()
        );
        storage.updateCase(updated);
        sendWebhook("incident-linked", updated);
        syncWorkflowCase(updated);
        return new ModerationActionResult(ModerationActionType.UPDATED, updated);
    }

    public ModerationActionResult addEvidence(long caseId,
                                              CommandActor actor,
                                              EvidenceType type,
                                              String value,
                                              String note) throws Exception {
        Optional<CaseRecord> existing = storage.findCaseById(caseId);
        if (existing.isEmpty()) {
            return new ModerationActionResult(ModerationActionType.NOT_FOUND, null);
        }

        List<CaseEvidence> updatedEvidence = new ArrayList<>(existing.get().getEvidence());
        updatedEvidence.add(CaseEvidence.create(
                "ev-" + caseId + '-' + (updatedEvidence.size() + 1),
                type == null ? EvidenceType.LINK : type,
                sanitize(value, ""),
                sanitize(note, ""),
                actor
        ));
        CaseRecord updated = existing.get().withWorkflow(
                existing.get().getServerProfileId(),
                existing.get().getIncidentId(),
                existing.get().getPriority(),
                actor.uniqueId(),
                actor.name(),
                System.currentTimeMillis(),
                existing.get().getAppealStatus(),
                existing.get().getAppealDeadlineAt(),
                existing.get().getAppealChangedAt(),
                existing.get().getAppealActorUniqueId(),
                existing.get().getAppealActorName(),
                existing.get().getAppealNotes(),
                existing.get().getNextReviewAt(),
                existing.get().getLastReviewedAt(),
                existing.get().getReviewReason(),
                updatedEvidence
        );
        storage.updateCase(updated);
        sendWebhook("evidence-added", updated);
        syncWorkflowCase(updated);
        return new ModerationActionResult(ModerationActionType.UPDATED, updated);
    }

    public ModerationActionResult openAppeal(long caseId, CommandActor actor, Long deadlineAt, String note) throws Exception {
        ModerationActionResult result = updateAppeal(caseId, actor, AppealStatus.OPEN, deadlineAt, note);
        if (result.caseRecord() != null) {
            ensureWorkflowRequest(result.caseRecord(), DiscordWorkflowRequestKind.APPEAL, DiscordWorkflowOrigin.INGAME, null, null, null);
            syncWorkflowCase(result.caseRecord());
        }
        return result;
    }

    public ModerationActionResult openWorkflowRequest(long caseId,
                                                      CommandActor actor,
                                                      Long deadlineAt,
                                                      String note,
                                                      DiscordWorkflowRequestKind kind,
                                                      DiscordWorkflowOrigin origin,
                                                      String requesterDiscordUserId,
                                                      String requesterDisplayName,
                                                      String guildId) throws Exception {
        ModerationActionResult result = updateAppeal(caseId, actor, AppealStatus.OPEN, deadlineAt, note);
        if (result.caseRecord() == null) {
            return result;
        }

        ensureWorkflowRequest(
                result.caseRecord(),
                kind == null ? DiscordWorkflowRequestKind.APPEAL : kind,
                origin == null ? DiscordWorkflowOrigin.DISCORD : origin,
                requesterDiscordUserId,
                requesterDisplayName,
                guildId
        );
        syncWorkflowCase(result.caseRecord());
        return result;
    }

    public ModerationActionResult acceptAppeal(long caseId, CommandActor actor, String note) throws Exception {
        ModerationActionResult updatedAppeal = updateAppeal(caseId, actor, AppealStatus.ACCEPTED, null, note);
        if (updatedAppeal.caseRecord() == null) {
            return updatedAppeal;
        }

        if (!shouldAutoResolveAcceptedAppeal(updatedAppeal.caseRecord())) {
            return updatedAppeal;
        }

        ModerationActionResult resolved = resolveCase(caseId, actor, note);
        return resolved.caseRecord() == null ? updatedAppeal : resolved;
    }

    public ModerationActionResult denyAppeal(long caseId, CommandActor actor, String note) throws Exception {
        return updateAppeal(caseId, actor, AppealStatus.DENIED, null, note);
    }

    public ModerationActionResult updateAppeal(long caseId,
                                               CommandActor actor,
                                               AppealStatus appealStatus,
                                               Long deadlineAt,
                                               String note) throws Exception {
        Optional<CaseRecord> existing = storage.findCaseById(caseId);
        if (existing.isEmpty()) {
            return new ModerationActionResult(ModerationActionType.NOT_FOUND, null);
        }

        List<CaseComment> appealNotes = new ArrayList<>(existing.get().getAppealNotes());
        if (note != null && !note.isBlank()) {
            appealNotes.add(CaseComment.create(actor, note.trim()));
        }

        CaseRecord updated = existing.get().withWorkflow(
                existing.get().getServerProfileId(),
                existing.get().getIncidentId(),
                existing.get().getPriority(),
                existing.get().getClaimActorUniqueId(),
                existing.get().getClaimActorName(),
                existing.get().getClaimChangedAt(),
                appealStatus == null ? existing.get().getAppealStatus() : appealStatus,
                deadlineAt == null ? existing.get().getAppealDeadlineAt() : deadlineAt,
                System.currentTimeMillis(),
                actor.uniqueId(),
                actor.name(),
                appealNotes,
                existing.get().getNextReviewAt(),
                existing.get().getLastReviewedAt(),
                existing.get().getReviewReason(),
                existing.get().getEvidence()
        );
        storage.updateCase(updated);
        sendWebhook("appeal-updated", updated);
        syncWorkflowCase(updated);
        return new ModerationActionResult(ModerationActionType.UPDATED, updated);
    }

    public ModerationActionResult createReview(PlayerIdentity target,
                                               CommandActor actor,
                                               String reason,
                                               Long reviewAt,
                                               CasePriority priority,
                                               String source) throws Exception {
        String effectiveReason = sanitize(reason, "Manual review reminder");
        CaseRecord stored = createAndStore(
                CaseType.REVIEW,
                plugin.getConfig().getString("reviews.default-label", "review"),
                target,
                null,
                null,
                actor,
                effectiveReason,
                source,
                null,
                plugin.getConfig().getString("reviews.default-category", "review"),
                null,
                List.of("review"),
                0,
                CaseVisibility.INTERNAL,
                null,
                null,
                priority == null ? CasePriority.NORMAL : priority,
                reviewAt,
                effectiveReason
        );
        sendWebhook("review-created", stored);
        return new ModerationActionResult(ModerationActionType.CREATED, stored);
    }

    public ModerationActionResult markReview(long caseId, CommandActor actor, String note, Long nextReviewAt) throws Exception {
        Optional<CaseRecord> existing = storage.findCaseById(caseId);
        if (existing.isEmpty()) {
            return new ModerationActionResult(ModerationActionType.NOT_FOUND, null);
        }

        CaseRecord base = existing.get().withWorkflow(
                existing.get().getServerProfileId(),
                existing.get().getIncidentId(),
                existing.get().getPriority(),
                actor.uniqueId(),
                actor.name(),
                System.currentTimeMillis(),
                existing.get().getAppealStatus(),
                existing.get().getAppealDeadlineAt(),
                existing.get().getAppealChangedAt(),
                existing.get().getAppealActorUniqueId(),
                existing.get().getAppealActorName(),
                existing.get().getAppealNotes(),
                nextReviewAt,
                System.currentTimeMillis(),
                sanitize(note, existing.get().getReviewReason()),
                existing.get().getEvidence()
        );
        CaseRecord updated = storage.updateCase(base);
        sendWebhook("review-updated", updated);
        syncWorkflowCase(updated);
        return new ModerationActionResult(ModerationActionType.UPDATED, updated);
    }

    public List<CaseRecord> getDueReviews(int pageSize, int page) throws Exception {
        List<CaseRecord> reviews = searchCases(
                new CaseSearchFilter(CaseType.REVIEW, CaseStatus.ACTIVE, null, null, null, null, null, null, null, null, null, null, null),
                pageSize,
                page
        );
        long now = System.currentTimeMillis();
        return reviews.stream()
                .filter(record -> record.getNextReviewAt() != null && record.getNextReviewAt() <= now)
                .toList();
    }

    public ModerationActionResult quarantinePlayer(PlayerIdentity target,
                                                   CommandActor actor,
                                                   String reason,
                                                   Long expiresAt,
                                                   CasePriority priority,
                                                   String source) throws Exception {
        Optional<CaseRecord> active = getActiveQuarantine(target.uniqueId());
        if (active.isPresent()) {
            return new ModerationActionResult(ModerationActionType.ALREADY_ACTIVE, active.get());
        }

        String effectiveReason = sanitize(reason, "Player was placed in quarantine.");
        CaseRecord stored = createAndStore(
                CaseType.QUARANTINE,
                plugin.getConfig().getString("quarantine.default-label", "quarantine"),
                target,
                null,
                null,
                actor,
                effectiveReason,
                source,
                expiresAt,
                plugin.getConfig().getString("quarantine.default-category", "security"),
                null,
                List.of("quarantine"),
                0,
                CaseVisibility.INTERNAL,
                null,
                null,
                priority == null ? CasePriority.HIGH : priority,
                null,
                effectiveReason
        );
        sendWebhook("quarantine-add", stored);
        return new ModerationActionResult(ModerationActionType.CREATED, stored);
    }

    public ModerationActionResult unquarantinePlayer(PlayerIdentity target, CommandActor actor, String note) throws Exception {
        return resolveActivePlayerCase(target, actor, note, CaseType.QUARANTINE, "quarantine-remove");
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

        String effectiveReason = sanitize(reason, defaultText("ban-reason", "punishments.defaults.ban-reason", "No reason specified."));
        CaseRecord stored = createAndStore(CaseType.BAN, null, target, null, null, actor, effectiveReason, source, expiresAt);
        notifyCreatedCase(stored);

        if (plugin.getConfig().getBoolean("punishments.player-ban.kick-online-player", true)) {
            Player online = Bukkit.getPlayer(target.uniqueId());
            if (online != null) {
                online.kickPlayer(buildBanScreen(stored));
            }
        }

        if (plugin.getServerRuleService().resolveBoolean("broadcasts.player-ban", plugin.getConfig().getBoolean("broadcasts.player-ban.enabled", true))) {
            broadcast("messages.broadcasts.player-ban", recordReplacements(stored));
        }

        sendWebhook(stored.isTemporary() ? "tempban" : "ban", stored);
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

        String effectiveReason = sanitize(reason, defaultText("mute-reason", "punishments.defaults.mute-reason", "No reason specified."));
        CaseRecord stored = createAndStore(CaseType.MUTE, null, target, null, null, actor, effectiveReason, source, expiresAt);

        Player online = Bukkit.getPlayer(target.uniqueId());
        if (online != null && plugin.getConfig().getBoolean("punishments.player-mute.notify-player", true)) {
            online.sendMessage(buildMuteMessage(stored));
        }

        if (plugin.getServerRuleService().resolveBoolean("broadcasts.player-mute", plugin.getConfig().getBoolean("broadcasts.player-mute.enabled", false))) {
            broadcast("messages.broadcasts.player-mute", recordReplacements(stored));
        }

        sendWebhook(stored.isTemporary() ? "tempmute" : "mute", stored);
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

        String effectiveReason = sanitize(reason, defaultText("ip-ban-reason", "punishments.defaults.ip-ban-reason", "No reason specified."));
        CaseRecord stored = createAndStore(CaseType.IP_BAN, null, targetPlayer, normalizedIp, null, actor, effectiveReason, source, expiresAt);
        notifyCreatedCase(stored);

        if (targetPlayer != null && plugin.getConfig().getBoolean("punishments.ip-ban.kick-online-player", true)) {
            Player online = Bukkit.getPlayer(targetPlayer.uniqueId());
            if (online != null) {
                online.kickPlayer(buildIpBanScreen(stored));
            }
        }

        if (plugin.getServerRuleService().resolveBoolean("broadcasts.ip-ban", plugin.getConfig().getBoolean("broadcasts.ip-ban.enabled", true))) {
            broadcast("messages.broadcasts.ip-ban", recordReplacements(stored));
        }

        sendWebhook(stored.isTemporary() ? "tempipban" : "ipban", stored);
        return new ModerationActionResult(ModerationActionType.CREATED, stored);
    }

    public ModerationActionResult unbanIp(String ipAddress, CommandActor actor, String note) throws Exception {
        String normalizedIp = IpUtil.normalize(ipAddress);
        Optional<CaseRecord> active = getActiveIpBan(normalizedIp);
        if (active.isEmpty()) {
            return new ModerationActionResult(ModerationActionType.NOT_ACTIVE, null);
        }

        String effectiveNote = sanitize(note, defaultText("unban-note", "punishments.defaults.unban-note", "No reason specified."));
        CaseRecord updated = storage.updateCaseStatus(active.get().getId(), CaseStatus.RESOLVED, System.currentTimeMillis(), actor.uniqueId(), actor.name(), effectiveNote);
        sendWebhook("unipban", updated);
        notifyResolvedCase(updated);
        syncWorkflowCase(updated);
        return new ModerationActionResult(ModerationActionType.REMOVED, updated);
    }

    public ModerationActionResult blacklistIp(String ipAddress, CommandActor actor, String reason, String source) throws Exception {
        String normalizedIp = IpUtil.normalize(ipAddress);
        Optional<CaseRecord> active = getActiveIpBlacklist(normalizedIp);
        if (active.isPresent()) {
            return new ModerationActionResult(ModerationActionType.ALREADY_ACTIVE, active.get());
        }

        String effectiveReason = sanitize(reason, defaultText("ip-blacklist-reason", "punishments.defaults.ip-blacklist-reason", "No reason specified."));
        CaseRecord stored = createAndStore(CaseType.IP_BLACKLIST, null, null, normalizedIp, null, actor, effectiveReason, source, null);
        sendWebhook("ipblacklist", stored);
        notifyCreatedCase(stored);
        return new ModerationActionResult(ModerationActionType.CREATED, stored);
    }

    public ModerationActionResult unblacklistIp(String ipAddress, CommandActor actor, String note) throws Exception {
        String normalizedIp = IpUtil.normalize(ipAddress);
        Optional<CaseRecord> active = getActiveIpBlacklist(normalizedIp);
        if (active.isEmpty()) {
            return new ModerationActionResult(ModerationActionType.NOT_ACTIVE, null);
        }

        String effectiveNote = sanitize(note, defaultText("unban-note", "punishments.defaults.unban-note", "No reason specified."));
        CaseRecord updated = storage.updateCaseStatus(active.get().getId(), CaseStatus.RESOLVED, System.currentTimeMillis(), actor.uniqueId(), actor.name(), effectiveNote);
        sendWebhook("ipunblacklist", updated);
        notifyResolvedCase(updated);
        syncWorkflowCase(updated);
        return new ModerationActionResult(ModerationActionType.REMOVED, updated);
    }

    public ModerationActionResult addNote(PlayerIdentity target,
                                          CommandActor actor,
                                          String label,
                                          String note,
                                          String source) throws Exception {
        return addNote(target, actor, label, note, source, CaseVisibility.fromConfig(plugin.getConfig().getString("notes.default-visibility", "INTERNAL")), List.of(), null, null);
    }

    public ModerationActionResult addNote(PlayerIdentity target,
                                          CommandActor actor,
                                          String label,
                                          String note,
                                          String source,
                                          CaseVisibility visibility,
                                          List<String> tags,
                                          String category,
                                          String templateKey) throws Exception {
        String effectiveLabel = sanitize(label, plugin.getConfig().getString("notes.default-label", "note"));
        String effectiveNote = sanitize(note, plugin.getConfig().getString("notes.default-text", "No additional note provided."));
        CaseRecord stored = createAndStore(
                CaseType.NOTE,
                effectiveLabel,
                target,
                null,
                null,
                actor,
                effectiveNote,
                source,
                null,
                category,
                templateKey,
                tags,
                0,
                visibility,
                null
        );
        sendWebhook("note", stored);
        return new ModerationActionResult(ModerationActionType.CREATED, stored);
    }

    public ModerationActionResult warnPlayer(PlayerIdentity target,
                                             CommandActor actor,
                                             String reason,
                                             int points,
                                             Long expiresAt,
                                             String source,
                                             String category,
                                             String templateKey,
                                             List<String> tags) throws Exception {
        int effectivePoints = Math.max(1, points);
        String effectiveReason = sanitize(reason, defaultText("warn-reason", "warnings.default-reason", "No reason specified."));
        CaseRecord stored = createAndStore(
                CaseType.WARN,
                null,
                target,
                null,
                null,
                actor,
                effectiveReason,
                source,
                expiresAt,
                sanitize(category, plugin.getConfig().getString("warnings.default-category", "warnings")),
                templateKey,
                tags,
                effectivePoints,
                CaseVisibility.INTERNAL,
                null
        );
        sendWebhook("warn", stored);
        applyWarnEscalation(target, actor, stored, source);
        return new ModerationActionResult(ModerationActionType.CREATED, stored);
    }

    public ModerationActionResult watchlistPlayer(PlayerIdentity target,
                                                  CommandActor actor,
                                                  String reason,
                                                  Long expiresAt,
                                                  String source,
                                                  List<String> tags) throws Exception {
        Optional<CaseRecord> active = getActiveWatchlist(target.uniqueId());
        if (active.isPresent()) {
            return new ModerationActionResult(ModerationActionType.ALREADY_ACTIVE, active.get());
        }

        String effectiveReason = sanitize(reason, defaultText("watchlist-reason", "watchlist.default-reason", "Added to watchlist."));
        CaseRecord stored = createAndStore(
                CaseType.WATCHLIST,
                plugin.getConfig().getString("watchlist.default-label", "watchlist"),
                target,
                null,
                null,
                actor,
                effectiveReason,
                source,
                expiresAt,
                plugin.getConfig().getString("watchlist.default-category", "security"),
                null,
                tags,
                0,
                CaseVisibility.INTERNAL,
                null
        );
        sendWebhook("watchlist-add", stored);
        return new ModerationActionResult(ModerationActionType.CREATED, stored);
    }

    public ModerationActionResult unwatchlistPlayer(PlayerIdentity target, CommandActor actor, String note) throws Exception {
        return resolveActivePlayerCase(target, actor, note, CaseType.WATCHLIST, "watchlist-remove");
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
        sendWebhook("altflag", stored);
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

        String effectiveNote = sanitize(note, defaultText("resolve-note", "punishments.defaults.resolve-note", "Resolved manually."));
        CaseRecord updated = storage.updateCaseStatus(caseId, CaseStatus.RESOLVED, System.currentTimeMillis(), actor.uniqueId(), actor.name(), effectiveNote);
        sendWebhook("resolve", updated);
        notifyResolvedCase(updated);
        syncWorkflowCase(updated);
        return new ModerationActionResult(ModerationActionType.REMOVED, updated);
    }

    public ModerationActionResult reopenCase(long caseId, CommandActor actor, String note) throws Exception {
        Optional<CaseRecord> existing = storage.findCaseById(caseId);
        if (existing.isEmpty()) {
            return new ModerationActionResult(ModerationActionType.NOT_FOUND, null);
        }
        if (existing.get().getStatus() == CaseStatus.ACTIVE) {
            return new ModerationActionResult(ModerationActionType.ALREADY_ACTIVE, existing.get());
        }

        String effectiveNote = sanitize(note, plugin.getConfig().getString("cases.reopen-note", "Case reopened manually."));
        CaseRecord reopened = storage.updateCaseStatus(caseId, CaseStatus.ACTIVE, System.currentTimeMillis(), actor.uniqueId(), actor.name(), effectiveNote);
        sendWebhook("reopen", reopened);
        notifyCreatedCase(reopened);
        syncWorkflowCase(reopened);
        return new ModerationActionResult(ModerationActionType.REOPENED, reopened);
    }

    public ModerationActionResult undoCase(long caseId, CommandActor actor, String note) throws Exception {
        Optional<CaseRecord> existing = storage.findCaseById(caseId);
        if (existing.isEmpty()) {
            return new ModerationActionResult(ModerationActionType.NOT_FOUND, null);
        }
        if (existing.get().getStatus() != CaseStatus.ACTIVE) {
            return new ModerationActionResult(ModerationActionType.NOT_ACTIVE, existing.get());
        }

        String effectiveNote = sanitize(note, plugin.getConfig().getString("cases.undo-note", "Action reverted manually."));
        CaseRecord updated = storage.updateCaseStatus(caseId, CaseStatus.RESOLVED, System.currentTimeMillis(), actor.uniqueId(), actor.name(), effectiveNote);
        sendWebhook("undo", updated);
        notifyResolvedCase(updated);
        syncWorkflowCase(updated);
        return new ModerationActionResult(ModerationActionType.REMOVED, updated);
    }

    public ModerationActionResult updateCaseTags(long caseId, CommandActor actor, String mode, List<String> tags) throws Exception {
        Optional<CaseRecord> existing = storage.findCaseById(caseId);
        if (existing.isEmpty()) {
            return new ModerationActionResult(ModerationActionType.NOT_FOUND, null);
        }

        List<String> updatedTags = switch (mode == null ? "set" : mode.toLowerCase()) {
            case "add" -> mergeTags(existing.get().getTags(), tags);
            case "remove" -> existing.get().getTags().stream()
                    .filter(tag -> tags == null || tags.stream().noneMatch(input -> input.equalsIgnoreCase(tag)))
                    .toList();
            case "clear" -> List.of();
            default -> mergeTags(List.of(), tags);
        };

        CaseRecord updated = existing.get().withMetadata(
                existing.get().getCategory(),
                existing.get().getTemplateKey(),
                updatedTags,
                existing.get().getPoints(),
                existing.get().getVisibility(),
                existing.get().getReferenceCaseId()
        );
        storage.updateCase(updated);
        sendWebhook("case-updated", updated);
        syncWorkflowCase(updated);
        return new ModerationActionResult(ModerationActionType.UPDATED, updated);
    }

    public ModerationActionResult kickPlayer(PlayerIdentity target,
                                             CommandActor actor,
                                             String reason,
                                             String source) throws Exception {
        String effectiveReason = sanitize(reason, defaultText("kick-reason", "punishments.defaults.kick-reason", "No reason specified."));
        CaseRecord stored = createAndStore(CaseType.KICK, null, target, null, null, actor, effectiveReason, source, null);
        notifyCreatedCase(stored);
        Player online = Bukkit.getPlayer(target.uniqueId());
        if (online != null) {
            online.kickPlayer(buildKickScreen(stored));
        }

        if (plugin.getServerRuleService().resolveBoolean("broadcasts.kick", plugin.getConfig().getBoolean("broadcasts.kick.enabled", false))) {
            broadcast("messages.broadcasts.kick", recordReplacements(stored));
        }

        sendWebhook("kick", stored);
        return new ModerationActionResult(ModerationActionType.EXECUTED, stored);
    }

    public PluginStats getStats() throws Exception {
        return new PluginStats(
                storage.countActiveCases(CaseType.BAN),
                storage.countActiveCases(CaseType.IP_BAN),
                storage.countActiveCases(CaseType.MUTE),
                storage.countActiveCases(CaseType.WARN),
                storage.countActiveCases(CaseType.WATCHLIST),
                storage.countActiveCases(CaseType.REPORT),
                storage.countActiveCases(CaseType.QUARANTINE),
                storage.countActiveCases(CaseType.REVIEW),
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

    private void ensureWorkflowRequest(CaseRecord record,
                                       DiscordWorkflowRequestKind kind,
                                       DiscordWorkflowOrigin origin,
                                       String requesterDiscordUserId,
                                       String requesterDisplayName,
                                       String guildId) {
        DiscordWorkflowRequest existing = plugin.getDiscordWorkflowStateStore().getRequest(record.getId()).orElse(null);
        DiscordWorkflowRequestKind effectiveKind = existing != null
                && existing.kind() == DiscordWorkflowRequestKind.UNBAN_REQUEST
                && kind == DiscordWorkflowRequestKind.APPEAL
                ? existing.kind()
                : kind;
        DiscordWorkflowOrigin effectiveOrigin = origin == null
                ? existing == null ? DiscordWorkflowOrigin.INGAME : existing.origin()
                : origin;
        String effectiveRequesterId = normalizeWorkflowText(requesterDiscordUserId, existing == null ? null : existing.requesterDiscordUserId());
        String effectiveRequesterName = normalizeWorkflowText(requesterDisplayName, existing == null ? null : existing.requesterDisplayName());
        String effectiveGuildId = normalizeWorkflowText(guildId, existing == null ? null : existing.guildId());
        String staffChannelId = existing == null ? null : existing.staffChannelId();
        String staffMessageId = existing == null ? null : existing.staffMessageId();
        long createdAt = existing == null ? System.currentTimeMillis() : existing.createdAt();

        plugin.getDiscordWorkflowStateStore().upsertRequest(new DiscordWorkflowRequest(
                record.getId(),
                effectiveKind == null ? DiscordWorkflowRequestKind.APPEAL : effectiveKind,
                effectiveOrigin,
                effectiveRequesterId,
                effectiveRequesterName,
                effectiveGuildId,
                staffChannelId,
                staffMessageId,
                createdAt,
                System.currentTimeMillis(),
                null
        ));
    }

    private String normalizeWorkflowText(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred.trim();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return null;
    }

    private boolean shouldAutoResolveAcceptedAppeal(CaseRecord record) {
        if (record == null || record.getStatus() != CaseStatus.ACTIVE) {
            return false;
        }
        if (!plugin.getConfig().getBoolean("discord-bot.appeals.auto-unban-on-accept", true)) {
            return false;
        }
        return switch (record.getType()) {
            case BAN, IP_BAN, MUTE, IP_BLACKLIST, WATCHLIST, QUARANTINE -> true;
            default -> false;
        };
    }

    private void syncWorkflowCase(CaseRecord record) {
        if (record == null || plugin.getDiscordBotManager() == null) {
            return;
        }
        plugin.getDiscordBotManager().syncWorkflowCase(record.getId());
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

        String effectiveNote = sanitize(note, defaultText("unban-note", "punishments.defaults.unban-note", "No reason specified."));
        CaseRecord updated = storage.updateCaseStatus(active.get().getId(), CaseStatus.RESOLVED, System.currentTimeMillis(), actor.uniqueId(), actor.name(), effectiveNote);
        sendWebhook(webhookKey, updated);
        notifyResolvedCase(updated);
        syncWorkflowCase(updated);
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
                defaultText("expired-note", "punishments.defaults.expired-note", "Case duration elapsed.")
        );
        sendWebhook("expired", expired);
        syncWorkflowCase(expired);
        return Optional.empty();
    }

    private List<CaseRecord> getActivePlayerCases(UUID playerUniqueId, CaseType type) throws Exception {
        List<CaseRecord> activeCases = new ArrayList<>();
        for (CaseRecord record : storage.getActiveCasesForPlayer(playerUniqueId, type)) {
            Optional<CaseRecord> active = ensureNotExpired(Optional.of(record));
            active.ifPresent(activeCases::add);
        }
        return activeCases;
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
        return createAndStore(type, label, target, ipAddress, related, actor, reason, source, expiresAt, null, null, List.of(), 0, CaseVisibility.INTERNAL, null, null, CasePriority.NORMAL, null, null);
    }

    private CaseRecord createAndStore(CaseType type,
                                      String label,
                                      PlayerIdentity target,
                                      String ipAddress,
                                      PlayerIdentity related,
                                      CommandActor actor,
                                      String reason,
                                      String source,
                                      Long expiresAt,
                                      String category,
                                      String templateKey,
                                      List<String> tags,
                                      int points,
                                      CaseVisibility visibility,
                                      Long referenceCaseId) throws Exception {
        return createAndStore(type, label, target, ipAddress, related, actor, reason, source, expiresAt, category, templateKey, tags, points, visibility, referenceCaseId, null, CasePriority.NORMAL, null, null);
    }

    private CaseRecord createAndStore(CaseType type,
                                      String label,
                                      PlayerIdentity target,
                                      String ipAddress,
                                      PlayerIdentity related,
                                      CommandActor actor,
                                      String reason,
                                      String source,
                                      Long expiresAt,
                                      String category,
                                      String templateKey,
                                      List<String> tags,
                                      int points,
                                      CaseVisibility visibility,
                                      Long referenceCaseId,
                                      String incidentId,
                                      CasePriority priority,
                                      Long nextReviewAt,
                                      String reviewReason) throws Exception {
        ensureProfileExists(target);
        ensureProfileExists(related);
        List<String> effectiveTags = mergeTags(plugin.getServerRuleService().getDefaultTags(type.name()), tags);
        CaseRecord record = CaseRecord.create(
                type,
                label,
                target,
                ipAddress,
                related,
                actor,
                reason,
                normalizeSource(source),
                expiresAt,
                category,
                templateKey,
                effectiveTags,
                points,
                visibility,
                referenceCaseId,
                plugin.getServerRuleService().getActiveProfileId(),
                incidentId,
                priority,
                nextReviewAt,
                reviewReason
        );
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
        DiscordWorkflowRequest workflowRequest = resolveWorkflowRequest(record);
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
                "category", defaultString(record.getCategory()),
                "template_key", defaultString(record.getTemplateKey()),
                "tags", record.getTagsDisplay().isBlank() ? lang.get("labels.none") : record.getTagsDisplay(),
                "tag_count", record.getTags().size(),
                "points", record.getPoints(),
                "visibility", record.getVisibility().name(),
                "reference_case_id", record.getReferenceCaseId() == null ? "" : String.valueOf(record.getReferenceCaseId()),
                "server_profile", plugin.getServerRuleService().getActiveProfileId(),
                "server_profile_name", plugin.getServerRuleService().getDisplayName(),
                "case_server_profile", defaultString(record.getServerProfileId()),
                "incident_id", defaultString(record.getIncidentId()),
                "priority", record.getPriority().name(),
                "claim_actor", defaultString(record.getClaimActorName()),
                "claim_actor_uuid", uuidString(record.getClaimActorUniqueId()),
                "claim_changed_at", record.getClaimChangedAt() == null ? lang.get("labels.none") : formatDate(record.getClaimChangedAt()),
                "appeal_status", record.getAppealStatus().name(),
                "appeal_deadline", record.getAppealDeadlineAt() == null ? lang.get("labels.none") : formatDate(record.getAppealDeadlineAt()),
                "appeal_actor", defaultString(record.getAppealActorName()),
                "appeal_note_count", record.getAppealNotes().size(),
                "workflow_kind", formatWorkflowKind(workflowRequest),
                "workflow_kind_key", workflowRequest == null ? "" : workflowRequest.kind().name(),
                "workflow_origin", workflowRequest == null ? lang.get("labels.none") : workflowRequest.origin().name(),
                "workflow_origin_key", workflowRequest == null ? "" : workflowRequest.origin().name(),
                "workflow_requester", workflowRequest == null ? lang.get("labels.none") : defaultString(workflowRequest.requesterDisplayName()),
                "workflow_discord_user_id", workflowRequest == null ? "" : sanitize(workflowRequest.requesterDiscordUserId(), ""),
                "workflow_guild_id", workflowRequest == null ? "" : sanitize(workflowRequest.guildId(), ""),
                "workflow_staff_channel_id", workflowRequest == null ? "" : sanitize(workflowRequest.staffChannelId(), ""),
                "workflow_staff_message_id", workflowRequest == null ? "" : sanitize(workflowRequest.staffMessageId(), ""),
                "workflow_created_at", workflowRequest == null ? lang.get("labels.none") : formatDate(workflowRequest.createdAt()),
                "workflow_updated_at", workflowRequest == null ? lang.get("labels.none") : formatDate(workflowRequest.updatedAt()),
                "appeal_latest_note", latestAppealNote(record),
                "review_at", record.getNextReviewAt() == null ? lang.get("labels.none") : formatDate(record.getNextReviewAt()),
                "review_reason", defaultString(record.getReviewReason()),
                "evidence_count", record.getEvidence().size(),
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

    private String formatWorkflowKind(DiscordWorkflowRequest workflowRequest) {
        if (workflowRequest == null) {
            return lang.get("labels.none");
        }
        return switch (workflowRequest.kind()) {
            case APPEAL -> "Appeal";
            case UNBAN_REQUEST -> "Unban Request";
        };
    }

    private String latestAppealNote(CaseRecord record) {
        if (record.getAppealNotes().isEmpty()) {
            return lang.get("labels.none");
        }
        return defaultString(record.getAppealNotes().getLast().getMessage());
    }

    private DiscordWorkflowRequest resolveWorkflowRequest(CaseRecord record) {
        DiscordWorkflowRequest stored = plugin.getDiscordWorkflowStateStore().getRequest(record.getId()).orElse(null);
        if (stored != null || record.getAppealStatus() != AppealStatus.OPEN) {
            return stored;
        }

        String requester = record.getAppealActorName() != null && !record.getAppealActorName().isBlank()
                ? record.getAppealActorName()
                : record.getActorName();
        long updatedAt = record.getAppealChangedAt() == null ? record.getCreatedAt() : record.getAppealChangedAt();
        return new DiscordWorkflowRequest(
                record.getId(),
                DiscordWorkflowRequestKind.APPEAL,
                DiscordWorkflowOrigin.INGAME,
                null,
                requester,
                null,
                null,
                null,
                record.getCreatedAt(),
                updatedAt,
                null
        );
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

    private String defaultText(String key, String fallbackPath, String fallback) {
        return plugin.getServerRuleService().resolveDefaultText(key, fallbackPath, fallback);
    }

    private String normalizeSource(String source) {
        return plugin.getServerRuleService().decorateSource(source);
    }

    private void sendWebhook(String actionKey, CaseRecord record) {
        webhookService.send(plugin.getServerRuleService().resolveWebhookAction(actionKey), recordReplacements(record));
    }

    private List<String> mergeTags(Collection<String> base, Collection<String> extra) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (base != null) {
            for (String value : base) {
                if (value != null && !value.isBlank()) {
                    merged.add(value.trim().toLowerCase());
                }
            }
        }
        if (extra != null) {
            for (String value : extra) {
                if (value != null && !value.isBlank()) {
                    merged.add(value.trim().toLowerCase());
                }
            }
        }
        return List.copyOf(merged);
    }

    private void applyWarnEscalation(PlayerIdentity target, CommandActor actor, CaseRecord warning, String source) throws Exception {
        if (!plugin.getConfig().getBoolean("warnings.escalation.enabled", true)) {
            return;
        }

        int warningPoints = getWarningPoints(target.uniqueId());
        List<?> steps = plugin.getConfig().getList("warnings.escalation.steps");
        if (steps == null) {
            return;
        }

        for (Object entry : steps) {
            if (!(entry instanceof java.util.Map<?, ?> map)) {
                continue;
            }

            int threshold = readInt(map.get("threshold"), Integer.MAX_VALUE);
            if (warningPoints < threshold) {
                continue;
            }

            String action = String.valueOf(map.containsKey("action") ? map.get("action") : "").trim().toUpperCase();
            String reason = replaceSimplePlaceholders(
                    String.valueOf(map.containsKey("reason") ? map.get("reason") : "Automatic escalation after warning points"),
                    "player", target.name(),
                    "warning_points", String.valueOf(warningPoints),
                    "case_id", String.valueOf(warning.getId())
            );
            List<String> tags = toStringList(map.get("tags"));
            Long duration = null;
            Object durationRaw = map.get("duration");
            if (durationRaw != null && !String.valueOf(durationRaw).isBlank()) {
                try {
                    duration = TimeUtil.parseDuration(String.valueOf(durationRaw));
                } catch (IllegalArgumentException ignored) {
                    duration = null;
                }
            }
            Long expiresAt = duration == null ? null : System.currentTimeMillis() + duration;

            if (action.equals("MUTE") && getActivePlayerMute(target.uniqueId()).isEmpty()) {
                CaseRecord escalated = createAndStore(CaseType.MUTE, null, target, null, null, actor, reason, source, expiresAt, "auto-escalation", null, tags, 0, CaseVisibility.INTERNAL, warning.getId());
                Player online = Bukkit.getPlayer(target.uniqueId());
                if (online != null && plugin.getConfig().getBoolean("punishments.player-mute.notify-player", true)) {
                    online.sendMessage(buildMuteMessage(escalated));
                }
                if (plugin.getServerRuleService().resolveBoolean("broadcasts.player-mute", plugin.getConfig().getBoolean("broadcasts.player-mute.enabled", false))) {
                    broadcast("messages.broadcasts.player-mute", recordReplacements(escalated));
                }
                sendWebhook(escalated.isTemporary() ? "tempmute" : "mute", escalated);
                sendWebhook("warn-escalated", escalated);
                return;
            }
            if (action.equals("BAN") && getActivePlayerBan(target.uniqueId()).isEmpty()) {
                CaseRecord escalated = createAndStore(CaseType.BAN, null, target, null, null, actor, reason, source, expiresAt, "auto-escalation", null, tags, 0, CaseVisibility.INTERNAL, warning.getId());
                notifyCreatedCase(escalated);
                Player online = Bukkit.getPlayer(target.uniqueId());
                if (online != null && plugin.getConfig().getBoolean("punishments.player-ban.kick-online-player", true)) {
                    online.kickPlayer(buildBanScreen(escalated));
                }
                if (plugin.getServerRuleService().resolveBoolean("broadcasts.player-ban", plugin.getConfig().getBoolean("broadcasts.player-ban.enabled", true))) {
                    broadcast("messages.broadcasts.player-ban", recordReplacements(escalated));
                }
                sendWebhook(escalated.isTemporary() ? "tempban" : "ban", escalated);
                sendWebhook("warn-escalated", escalated);
                return;
            }
        }
    }

    private int readInt(Object input, int fallback) {
        if (input instanceof Number number) {
            return number.intValue();
        }
        if (input == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(input));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private List<String> toStringList(Object input) {
        if (!(input instanceof List<?> list)) {
            return List.of();
        }
        List<String> output = new ArrayList<>();
        for (Object entry : list) {
            if (entry != null && !String.valueOf(entry).isBlank()) {
                output.add(String.valueOf(entry));
            }
        }
        return output;
    }

    private String replaceSimplePlaceholders(String input, String... replacements) {
        String output = input == null ? "" : input;
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            output = output.replace("{" + replacements[index] + "}", replacements[index + 1]);
        }
        return output;
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
            case KICK -> {
                if (record.getTargetPlayerUniqueId() != null) {
                    plugin.getNetworkSyncService().notifyPlayerKick(record.getTargetPlayerUniqueId(), record.getId());
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
