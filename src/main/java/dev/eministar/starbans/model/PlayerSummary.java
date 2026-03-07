package dev.eministar.starbans.model;

public record PlayerSummary(PlayerIdentity player,
                            String lastKnownIp,
                            CaseRecord activeBan,
                            CaseRecord activeMute,
                            CaseRecord latestCase,
                            int visibleCaseCount,
                            int noteCount,
                            int altFlagCount) {
}
