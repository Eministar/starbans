package dev.eministar.starbans.service;

import dev.eministar.starbans.StarBans;
import dev.eministar.starbans.model.PlayerIdentity;
import dev.eministar.starbans.model.PlayerProfile;
import dev.eministar.starbans.model.PlayerSummary;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AltDetectionService {

    private final StarBans plugin;
    private final ModerationService moderationService;

    public AltDetectionService(StarBans plugin, ModerationService moderationService) {
        this.plugin = plugin;
        this.moderationService = moderationService;
    }

    public AltAnalysisResult analyze(PlayerIdentity player, String ipAddress) throws Exception {
        List<PlayerProfile> sameIpProfiles = moderationService.getProfilesByIp(ipAddress).stream()
                .filter(profile -> !player.uniqueId().equals(profile.getUniqueId()))
                .toList();
        if (sameIpProfiles.isEmpty()) {
            return new AltAnalysisResult(false, 0, List.of(), List.of(), List.of());
        }

        int minimumScore = Math.max(1, plugin.getConfig().getInt("alt-detection.minimum-score", 4));
        int similarityPrefixLength = Math.max(3, plugin.getConfig().getInt("alt-detection.name-prefix-threshold", 4));
        int score = 0;
        List<String> reasons = new ArrayList<>();
        List<PlayerProfile> suspiciousProfiles = new ArrayList<>();

        for (PlayerProfile profile : sameIpProfiles) {
            PlayerIdentity identity = new PlayerIdentity(profile.getUniqueId(), profile.getLastName());
            PlayerSummary summary = moderationService.getPlayerSummary(identity);
            boolean suspicious = false;

            if (summary.visibleCaseCount() > 0) {
                score += 2;
                suspicious = true;
                reasons.add("Shared IP with moderated account " + safeName(profile.getLastName()));
            }
            if (summary.activeBan() != null || summary.activeMute() != null || summary.activeWatchlist() != null) {
                score += 3;
                suspicious = true;
                reasons.add("Shared IP with active punishment/watchlist on " + safeName(profile.getLastName()));
            }
            if (hasSimilarName(player.name(), profile.getLastName(), similarityPrefixLength)) {
                score += 2;
                suspicious = true;
                reasons.add("Name pattern close to " + safeName(profile.getLastName()));
            }
            if (summary.warnCount() >= Math.max(1, plugin.getConfig().getInt("alt-detection.warn-threshold", 2))) {
                score += 1;
                suspicious = true;
                reasons.add("Related account " + safeName(profile.getLastName()) + " already has warning history");
            }

            if (suspicious && !suspiciousProfiles.contains(profile)) {
                suspiciousProfiles.add(profile);
            }
        }

        return new AltAnalysisResult(
                score >= minimumScore,
                score,
                reasons.stream().distinct().limit(Math.max(1, plugin.getConfig().getInt("alt-detection.max-reasons", 4))).toList(),
                sameIpProfiles,
                suspiciousProfiles
        );
    }

    private boolean hasSimilarName(String first, String second, int prefixLength) {
        String normalizedFirst = normalizeName(first);
        String normalizedSecond = normalizeName(second);
        if (normalizedFirst.isBlank() || normalizedSecond.isBlank()) {
            return false;
        }
        if (normalizedFirst.equals(normalizedSecond)) {
            return true;
        }
        if (normalizedFirst.length() < prefixLength || normalizedSecond.length() < prefixLength) {
            return false;
        }
        return normalizedFirst.substring(0, prefixLength).equals(normalizedSecond.substring(0, prefixLength));
    }

    private String normalizeName(String input) {
        if (input == null) {
            return "";
        }
        return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private String safeName(String input) {
        return input == null || input.isBlank() ? "unknown" : input;
    }

    public record AltAnalysisResult(boolean flagged,
                                    int score,
                                    List<String> reasons,
                                    List<PlayerProfile> relatedProfiles,
                                    List<PlayerProfile> suspiciousProfiles) {
    }
}
