package dev.eministar.starbans.service;

import dev.eministar.starbans.StarBans;
import dev.eministar.starbans.utils.Lang;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimeUtil {

    private static final Pattern DURATION_PATTERN = Pattern.compile("(?i)(\\d+)(mo|[smhdwy])");
    private static final Map<String, Long> UNIT_MILLIS = new LinkedHashMap<>();

    static {
        UNIT_MILLIS.put("y", 31_536_000_000L);
        UNIT_MILLIS.put("mo", 2_592_000_000L);
        UNIT_MILLIS.put("w", 604_800_000L);
        UNIT_MILLIS.put("d", 86_400_000L);
        UNIT_MILLIS.put("h", 3_600_000L);
        UNIT_MILLIS.put("m", 60_000L);
        UNIT_MILLIS.put("s", 1_000L);
    }

    private TimeUtil() {
    }

    public static Long parseDuration(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Duration input is empty.");
        }

        String normalized = input.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("perm") || normalized.equals("permanent") || normalized.equals("forever")) {
            return null;
        }

        long total = 0L;
        int cursor = 0;
        Matcher matcher = DURATION_PATTERN.matcher(normalized);
        while (matcher.find()) {
            if (matcher.start() != cursor) {
                throw new IllegalArgumentException("Duration contains an unsupported segment.");
            }

            long amount = Long.parseLong(matcher.group(1));
            long unitMillis = UNIT_MILLIS.get(matcher.group(2).toLowerCase(Locale.ROOT));
            total += amount * unitMillis;
            cursor = matcher.end();
        }

        if (cursor != normalized.length() || total <= 0L) {
            throw new IllegalArgumentException("Duration format is invalid.");
        }

        return total;
    }

    public static String formatRemaining(Long expiresAt, Lang lang) {
        if (expiresAt == null) {
            return lang.get("time.permanent");
        }

        long remaining = expiresAt - System.currentTimeMillis();
        if (remaining <= 0L) {
            return lang.get("time.expired");
        }
        return formatDuration(remaining, lang);
    }

    public static String formatDuration(long millis, Lang lang) {
        if (millis <= 0L) {
            return lang.get("time.now");
        }

        long remaining = millis;
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Long> entry : UNIT_MILLIS.entrySet()) {
            long amount = remaining / entry.getValue();
            if (amount <= 0L) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(amount).append(lang.get("time.units." + entry.getKey()));
            remaining -= amount * entry.getValue();
        }
        return builder.isEmpty() ? lang.get("time.now") : builder.toString();
    }

    public static String formatDate(StarBans plugin, long epochMillis) {
        String pattern = plugin.getConfig().getString("settings.date-format", "dd.MM.yyyy HH:mm:ss");
        String zoneValue = plugin.getConfig().getString("settings.timezone", "system");

        ZoneId zoneId;
        try {
            zoneId = zoneValue != null && !zoneValue.equalsIgnoreCase("system") ? ZoneId.of(zoneValue) : ZoneId.systemDefault();
        } catch (Exception exception) {
            zoneId = ZoneId.systemDefault();
        }

        return DateTimeFormatter.ofPattern(pattern, Locale.GERMANY)
                .withZone(zoneId)
                .format(Instant.ofEpochMilli(epochMillis));
    }
}
