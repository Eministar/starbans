package dev.eministar.starbans.service;

import dev.eministar.starbans.StarBans;
import dev.eministar.starbans.model.CasePriority;
import dev.eministar.starbans.model.CaseRecord;
import dev.eministar.starbans.model.CaseSearchFilter;
import dev.eministar.starbans.model.CaseStatus;
import dev.eministar.starbans.model.CaseType;
import dev.eministar.starbans.model.CommandActor;
import dev.eministar.starbans.model.PlayerIdentity;
import dev.eministar.starbans.model.PlayerProfile;
import dev.eministar.starbans.model.RiskAssessment;
import dev.eministar.starbans.utils.LoggerUtil;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ReviewReminderService {

    private final StarBans plugin;
    private final Set<Long> alertedReviewIds = new HashSet<>();
    private BukkitTask task;

    public ReviewReminderService(StarBans plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        if (task != null) {
            task.cancel();
            task = null;
        }

        if (!plugin.getConfig().getBoolean("reviews.reminders.enabled", true)) {
            return;
        }

        long intervalSeconds = Math.max(30L, plugin.getConfig().getLong("reviews.reminders.interval-seconds", 120L));
        task = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin,
                this::runScan,
                20L * Math.min(intervalSeconds, 5L),
                20L * intervalSeconds
        );
    }

    public void close() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        alertedReviewIds.clear();
    }

    private void runScan() {
        try {
            scanExistingReviews();
            scanWatchlists();
            scanQuarantines();
            scanHighRiskProfiles();
        } catch (Exception exception) {
            LoggerUtil.error("The review reminder scan failed.", exception);
        }
    }

    private void scanExistingReviews() throws Exception {
        long now = System.currentTimeMillis();
        List<CaseRecord> reviews = plugin.getModerationService().searchCases(
                new CaseSearchFilter(CaseType.REVIEW, CaseStatus.ACTIVE, null, null, null, null, null, null, null, null, null, null, null),
                200,
                0
        );

        for (CaseRecord review : reviews) {
            if (review.getNextReviewAt() == null || review.getNextReviewAt() > now) {
                alertedReviewIds.remove(review.getId());
                continue;
            }
            if (!alertedReviewIds.add(review.getId())) {
                continue;
            }

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.getStaffAlertService().sendCustomAlert(
                        "messages.staff-alert-review-due",
                        "player", review.getTargetPlayerName(),
                        "case_id", review.getId(),
                        "reason", review.getReason(),
                        "review_reason", review.getReviewReason(),
                        "priority", review.getPriority().name()
                );
                plugin.getDiscordWebhookService().send("review-due", plugin.getModerationService().recordReplacements(review));
            });
        }
    }

    private void scanWatchlists() throws Exception {
        Long threshold = parseOptionalDuration(plugin.getConfig().getString("reviews.reminders.watchlist-after", "3d"));
        if (threshold == null) {
            return;
        }

        List<CaseRecord> watchlists = plugin.getModerationService().searchCases(
                new CaseSearchFilter(CaseType.WATCHLIST, CaseStatus.ACTIVE, null, null, null, null, null, null, null, null, null, null, null),
                200,
                0
        );
        long now = System.currentTimeMillis();
        for (CaseRecord record : watchlists) {
            if (now - record.getCreatedAt() < threshold) {
                continue;
            }
            createReviewIfMissing(record, "watchlist review");
        }
    }

    private void scanQuarantines() throws Exception {
        Long threshold = parseOptionalDuration(plugin.getConfig().getString("reviews.reminders.quarantine-after", "12h"));
        if (threshold == null) {
            return;
        }

        List<CaseRecord> quarantines = plugin.getModerationService().searchCases(
                new CaseSearchFilter(CaseType.QUARANTINE, CaseStatus.ACTIVE, null, null, null, null, null, null, null, null, null, null, null),
                200,
                0
        );
        long now = System.currentTimeMillis();
        for (CaseRecord record : quarantines) {
            if (now - record.getCreatedAt() < threshold) {
                continue;
            }
            createReviewIfMissing(record, "quarantine follow-up");
        }
    }

    private void scanHighRiskProfiles() throws Exception {
        int threshold = Math.max(1, plugin.getConfig().getInt("reviews.reminders.high-risk-threshold", 45));
        int scanLimit = Math.max(10, plugin.getConfig().getInt("reviews.reminders.high-risk-profile-limit", 50));

        List<PlayerProfile> profiles = plugin.getModerationService().getKnownProfiles(scanLimit, 0);
        for (PlayerProfile profile : profiles) {
            RiskAssessment risk = plugin.getModerationService().calculateRiskAssessment(profile.getUniqueId());
            if (risk.score() < threshold) {
                continue;
            }
            if (!plugin.getModerationService().getActiveReviews(profile.getUniqueId()).isEmpty()) {
                continue;
            }
            plugin.getModerationService().createReview(
                    new PlayerIdentity(profile.getUniqueId(), profile.getLastName()),
                    CommandActor.system(),
                    "Automatic risk review: " + String.join(", ", risk.reasons()),
                    System.currentTimeMillis(),
                    CasePriority.HIGH,
                    "SYSTEM:REVIEW-RISK"
            );
        }
    }

    private void createReviewIfMissing(CaseRecord source, String reason) throws Exception {
        if (source.getTargetPlayerUniqueId() == null || source.getTargetPlayerName() == null) {
            return;
        }
        if (!plugin.getModerationService().getActiveReviews(source.getTargetPlayerUniqueId()).isEmpty()) {
            return;
        }

        plugin.getModerationService().createReview(
                new PlayerIdentity(source.getTargetPlayerUniqueId(), source.getTargetPlayerName()),
                CommandActor.system(),
                reason + " | source case #" + source.getId(),
                System.currentTimeMillis(),
                source.getPriority(),
                "SYSTEM:REVIEW-REMINDER"
        );
    }

    private Long parseOptionalDuration(String value) {
        try {
            return TimeUtil.parseDuration(value);
        } catch (Exception ignored) {
            return null;
        }
    }
}
