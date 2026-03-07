package dev.eministar.starbans.velocity.util;

import dev.eministar.starbans.velocity.StarBansVelocityAddon;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class TimeUtil {

    private TimeUtil() {
    }

    public static String formatDate(StarBansVelocityAddon plugin, long epochMillis) {
        plugin.getSharedNetworkSnapshotService().refreshIfNeeded();
        String zoneId = plugin.getSharedNetworkSnapshotService().getString("settings.timezone", "system");
        ZoneId zone = zoneId.equalsIgnoreCase("system") ? ZoneId.systemDefault() : ZoneId.of(zoneId);
        String dateFormat = plugin.getSharedNetworkSnapshotService().getString("settings.date-format", "dd.MM.yyyy HH:mm:ss");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(dateFormat);
        return formatter.format(Instant.ofEpochMilli(epochMillis).atZone(zone));
    }

    public static String formatRemaining(Long expiresAt, StarBansVelocityAddon plugin) {
        plugin.getSharedNetworkSnapshotService().refreshIfNeeded();
        if (expiresAt == null) {
            return plugin.getSharedNetworkSnapshotService().getString("time.permanent", plugin.getLang().get("time.permanent", "&cPermanent"));
        }

        long remaining = expiresAt - System.currentTimeMillis();
        if (remaining <= 0L) {
            return plugin.getSharedNetworkSnapshotService().getString("time.expired", plugin.getLang().get("time.expired", "&7Expired"));
        }

        long seconds = remaining / 1000L;
        long minutes = seconds / 60L;
        long hours = minutes / 60L;
        long days = hours / 24L;

        if (days > 0L) {
            return days + plugin.getSharedNetworkSnapshotService().getString("time.units.d", plugin.getLang().get("time.units.d", "d"));
        }
        if (hours > 0L) {
            return hours + plugin.getSharedNetworkSnapshotService().getString("time.units.h", plugin.getLang().get("time.units.h", "h"));
        }
        if (minutes > 0L) {
            return minutes + plugin.getSharedNetworkSnapshotService().getString("time.units.m", plugin.getLang().get("time.units.m", "m"));
        }
        return Math.max(1L, seconds) + plugin.getSharedNetworkSnapshotService().getString("time.units.s", plugin.getLang().get("time.units.s", "s"));
    }
}
