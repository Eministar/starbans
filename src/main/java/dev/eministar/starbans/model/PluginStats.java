package dev.eministar.starbans.model;

public record PluginStats(int activeBans,
                          int activeIpBans,
                          int activeMutes,
                          int activeWarns,
                          int activeWatchlists,
                          int totalCases) {
}
