package dev.eministar.starbans.discord;

import com.google.gson.Gson;
import dev.eministar.starbans.StarBans;
import dev.eministar.starbans.config.DiscordWebhookConfig;
import dev.eministar.starbans.service.TimeUtil;
import dev.eministar.starbans.utils.LoggerUtil;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class DiscordWebhookService {

    private static final Pattern LEGACY_COLOR_PATTERN = Pattern.compile("(?i)[&§][0-9A-FK-OR]");
    private static final Pattern AMPERSAND_HEX_PATTERN = Pattern.compile("(?i)&#[0-9A-F]{6}");
    private static final Pattern SECTION_HEX_PATTERN = Pattern.compile("(?i)§x(?:§[0-9A-F]){6}");

    private final StarBans plugin;
    private final DiscordWebhookConfig webhookConfig;
    private final Gson gson = new Gson();

    public DiscordWebhookService(StarBans plugin, DiscordWebhookConfig webhookConfig) {
        this.plugin = plugin;
        this.webhookConfig = webhookConfig;
    }

    public void send(String actionKey, Object... replacements) {
        FileConfiguration configuration = webhookConfig.getConfiguration();
        if (!configuration.getBoolean("enabled", false)) {
            return;
        }

        String actionPath = "actions." + actionKey;
        if (!configuration.getBoolean(actionPath + ".enabled", false)) {
            return;
        }

        List<String> urls = getConfiguredUrls(configuration, actionPath);
        if (urls.isEmpty()) {
            return;
        }

        Map<String, String> placeholders = buildPlaceholders(actionKey, replacements);
        String content = readTextBlock(configuration, actionPath + ".content", actionPath + ".content-single", placeholders);
        List<Map<String, Object>> embeds = buildEmbeds(configuration, actionPath, placeholders);
        if ((content == null || content.isBlank()) && embeds.isEmpty()) {
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("username", firstNonBlank(
                configuration.getString(actionPath + ".username"),
                configuration.getString("username"),
                "StarBans"
        ));
        if (content != null && !content.isBlank()) {
            payload.put("content", content);
        }

        String avatarUrl = firstNonBlank(
                configuration.getString(actionPath + ".avatar-url"),
                configuration.getString("avatar-url"),
                ""
        );
        if (!avatarUrl.isBlank()) {
            payload.put("avatar_url", avatarUrl);
        }

        if (configuration.getBoolean(actionPath + ".tts", false)) {
            payload.put("tts", true);
        }

        Map<String, Object> allowedMentions = buildAllowedMentions(configuration, actionPath);
        if (!allowedMentions.isEmpty()) {
            payload.put("allowed_mentions", allowedMentions);
        }
        if (!embeds.isEmpty()) {
            payload.put("embeds", embeds);
        }

        int timeout = configuration.getInt("timeout-ms", 5000);
        boolean logFailures = configuration.getBoolean("log-failures", true);
        boolean logDeliveries = configuration.getBoolean("log-deliveries", false);
        plugin.getServer().getScheduler().runTaskAsynchronously(
                plugin,
                () -> postAll(actionKey, urls, payload, timeout, logFailures, logDeliveries)
        );
    }

    private Map<String, String> buildPlaceholders(String actionKey, Object... replacements) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        long now = System.currentTimeMillis();

        placeholders.put("action_key", actionKey);
        placeholders.put("plugin_name", plugin.getDescription().getName());
        placeholders.put("plugin_version", plugin.getDescription().getVersion());
        placeholders.put("server_name", safeServerName());
        placeholders.put("server_version", safe(plugin.getServer().getVersion()));
        placeholders.put("online_players", String.valueOf(plugin.getServer().getOnlinePlayers().size()));
        placeholders.put("max_players", String.valueOf(plugin.getServer().getMaxPlayers()));
        placeholders.put("event_time", TimeUtil.formatDate(plugin, now));
        placeholders.put("event_time_iso", Instant.ofEpochMilli(now).toString());

        if (replacements != null) {
            for (int index = 0; index + 1 < replacements.length; index += 2) {
                placeholders.put(String.valueOf(replacements[index]), normalizeReplacementValue(replacements[index + 1]));
            }
        }

        return placeholders;
    }

    private List<String> getConfiguredUrls(FileConfiguration configuration, String actionPath) {
        List<String> urls = readStringList(configuration, actionPath + ".urls");
        if (urls.isEmpty()) {
            String single = configuration.getString(actionPath + ".url", "");
            if (single != null && !single.isBlank()) {
                urls = List.of(single);
            }
        }

        if (urls.isEmpty()) {
            urls = readStringList(configuration, "default-urls");
        }
        if (urls.isEmpty()) {
            String single = configuration.getString("default-url", "");
            if (single != null && !single.isBlank()) {
                urls = List.of(single);
            }
        }

        return urls.stream()
                .filter(url -> url != null && !url.isBlank())
                .distinct()
                .toList();
    }

    private String readTextBlock(FileConfiguration configuration, String path, String fallbackPath, Map<String, String> placeholders) {
        List<String> lines = readStringList(configuration, path);
        if (lines.isEmpty() && fallbackPath != null && !fallbackPath.isBlank()) {
            String single = configuration.getString(fallbackPath, "");
            if (single != null && !single.isBlank()) {
                lines = List.of(single);
            }
        }
        if (lines.isEmpty()) {
            return "";
        }

        List<String> output = new ArrayList<>(lines.size());
        for (String line : lines) {
            output.add(applyPlaceholders(line, placeholders));
        }
        return String.join("\n", output);
    }

    private List<Map<String, Object>> buildEmbeds(FileConfiguration configuration, String actionPath, Map<String, String> placeholders) {
        List<Map<String, Object>> embeds = new ArrayList<>();
        for (Map<?, ?> embedData : configuration.getMapList(actionPath + ".embeds")) {
            Map<String, Object> embed = buildEmbed(embedData, placeholders);
            if (!embed.isEmpty()) {
                embeds.add(embed);
            }
        }
        return embeds;
    }

    private Map<String, Object> buildEmbed(Map<?, ?> embedData, Map<String, String> placeholders) {
        Map<String, Object> embed = new LinkedHashMap<>();

        putResolved(embed, "title", embedData.get("title"), placeholders);
        putResolved(embed, "description", embedData.get("description"), placeholders);
        putResolved(embed, "url", embedData.get("url"), placeholders);

        Integer color = parseColor(resolveObject(embedData.get("color"), placeholders));
        if (color != null) {
            embed.put("color", color);
        }

        Map<String, Object> author = buildAuthorOrFooter(asMap(embedData.get("author")), placeholders, true);
        if (!author.isEmpty()) {
            embed.put("author", author);
        }

        Map<String, Object> footer = buildAuthorOrFooter(asMap(embedData.get("footer")), placeholders, false);
        if (!footer.isEmpty()) {
            embed.put("footer", footer);
        }

        String thumbnailUrl = resolveObject(embedData.get("thumbnail-url"), placeholders);
        if (!thumbnailUrl.isBlank()) {
            embed.put("thumbnail", Map.of("url", thumbnailUrl));
        }

        String imageUrl = resolveObject(embedData.get("image-url"), placeholders);
        if (!imageUrl.isBlank()) {
            embed.put("image", Map.of("url", imageUrl));
        }

        String timestamp = resolveTimestamp(embedData.get("timestamp"), placeholders);
        if (!timestamp.isBlank()) {
            embed.put("timestamp", timestamp);
        }

        List<Map<String, Object>> fields = buildFields(embedData.get("fields"), placeholders);
        if (!fields.isEmpty()) {
            embed.put("fields", fields);
        }

        return embed;
    }

    private Map<String, Object> buildAuthorOrFooter(Map<String, Object> section, Map<String, String> placeholders, boolean author) {
        if (section.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> output = new LinkedHashMap<>();
        String textKey = author ? "name" : "text";
        String iconKey = "icon-url";

        String text = resolveObject(section.get(textKey), placeholders);
        if (!text.isBlank()) {
            output.put(textKey, text);
        }

        if (author) {
            String url = resolveObject(section.get("url"), placeholders);
            if (!url.isBlank()) {
                output.put("url", url);
            }
        }

        String iconUrl = resolveObject(section.get(iconKey), placeholders);
        if (!iconUrl.isBlank()) {
            output.put("icon_url", iconUrl);
        }

        return output;
    }

    private List<Map<String, Object>> buildFields(Object rawFields, Map<String, String> placeholders) {
        if (!(rawFields instanceof List<?> list)) {
            return List.of();
        }

        List<Map<String, Object>> fields = new ArrayList<>();
        for (Object rawField : list) {
            Map<String, Object> fieldData = asMap(rawField);
            if (fieldData.isEmpty()) {
                continue;
            }

            String name = resolveObject(fieldData.get("name"), placeholders);
            String value = resolveObject(fieldData.get("value"), placeholders);
            if (name.isBlank() || value.isBlank()) {
                continue;
            }

            Map<String, Object> field = new LinkedHashMap<>();
            field.put("name", name);
            field.put("value", value);
            field.put("inline", parseBoolean(fieldData.get("inline")));
            fields.add(field);
        }
        return fields;
    }

    private String resolveTimestamp(Object rawValue, Map<String, String> placeholders) {
        String configured = resolveObject(rawValue, placeholders);
        if (configured.isBlank() || configured.equalsIgnoreCase("NONE")) {
            return "";
        }

        return switch (configured.toUpperCase(Locale.ROOT)) {
            case "NOW", "EVENT_TIME" -> placeholders.getOrDefault("event_time_iso", "");
            case "CREATED_AT" -> placeholders.getOrDefault("created_at_iso", "");
            case "STATUS_CHANGED_AT" -> placeholders.getOrDefault("status_changed_at_iso", "");
            case "EXPIRES_AT" -> placeholders.getOrDefault("expires_at_iso", "");
            default -> configured;
        };
    }

    private Map<String, Object> buildAllowedMentions(FileConfiguration configuration, String actionPath) {
        ConfigurationSection section = configuration.getConfigurationSection(actionPath + ".allowed-mentions");
        if (section == null) {
            section = configuration.getConfigurationSection("allowed-mentions");
        }
        if (section == null) {
            return Map.of();
        }

        Map<String, Object> mentions = new LinkedHashMap<>();
        List<String> parse = readStringList(section, "parse");
        if (!parse.isEmpty()) {
            mentions.put("parse", parse);
        }

        List<String> users = readStringList(section, "users");
        if (!users.isEmpty()) {
            mentions.put("users", users);
        }

        List<String> roles = readStringList(section, "roles");
        if (!roles.isEmpty()) {
            mentions.put("roles", roles);
        }

        if (section.contains("replied-user")) {
            mentions.put("replied_user", section.getBoolean("replied-user"));
        }

        return mentions;
    }

    private void postAll(String actionKey,
                         List<String> targetUrls,
                         Map<String, Object> payload,
                         int timeout,
                         boolean logFailures,
                         boolean logDeliveries) {
        for (String targetUrl : targetUrls) {
            post(actionKey, targetUrl, payload, timeout, logFailures, logDeliveries);
        }
    }

    private void post(String actionKey,
                      String targetUrl,
                      Map<String, Object> payload,
                      int timeout,
                      boolean logFailures,
                      boolean logDeliveries) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(targetUrl).openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);

            byte[] body = gson.toJson(payload).getBytes(StandardCharsets.UTF_8);
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(body);
            }

            int statusCode = connection.getResponseCode();
            try (InputStream ignored = statusCode >= 200 && statusCode < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream()) {
                if (statusCode >= 200 && statusCode < 300) {
                    if (logDeliveries) {
                        LoggerUtil.info("Discord webhook '" + actionKey + "' delivered to " + summarizeUrl(targetUrl) + ".");
                    }
                    return;
                }
            }

            if (logFailures) {
                LoggerUtil.warn("Discord webhook '" + actionKey + "' returned HTTP " + statusCode + " for " + summarizeUrl(targetUrl) + ".");
            }
        } catch (Exception exception) {
            if (logFailures) {
                LoggerUtil.warn("Discord webhook '" + actionKey + "' failed for " + summarizeUrl(targetUrl) + ": " + exception.getClass().getSimpleName());
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void putResolved(Map<String, Object> target, String key, Object rawValue, Map<String, String> placeholders) {
        String resolved = resolveObject(rawValue, placeholders);
        if (!resolved.isBlank()) {
            target.put(key, resolved);
        }
    }

    private String resolveObject(Object rawValue, Map<String, String> placeholders) {
        if (rawValue == null) {
            return "";
        }
        if (rawValue instanceof List<?> list) {
            List<String> lines = new ArrayList<>(list.size());
            for (Object entry : list) {
                lines.add(resolveObject(entry, placeholders));
            }
            return String.join("\n", lines);
        }
        return applyPlaceholders(String.valueOf(rawValue), placeholders);
    }

    private String applyPlaceholders(String input, Map<String, String> placeholders) {
        String output = input == null ? "" : input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            output = output.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return stripMinecraftFormatting(output);
    }

    private List<String> readStringList(FileConfiguration configuration, String path) {
        List<String> list = configuration.getStringList(path);
        if (!list.isEmpty()) {
            return list;
        }

        String single = configuration.getString(path);
        if (single == null || single.isBlank()) {
            return List.of();
        }
        return List.of(single);
    }

    private List<String> readStringList(ConfigurationSection configuration, String path) {
        List<String> list = configuration.getStringList(path);
        if (!list.isEmpty()) {
            return list;
        }

        String single = configuration.getString(path);
        if (single == null || single.isBlank()) {
            return List.of();
        }
        return List.of(single);
    }

    private Map<String, Object> asMap(Object rawValue) {
        if (rawValue instanceof ConfigurationSection section) {
            return new LinkedHashMap<>(section.getValues(false));
        }
        if (rawValue instanceof Map<?, ?> map) {
            Map<String, Object> output = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                output.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return output;
        }
        return Map.of();
    }

    private Integer parseColor(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        String normalized = input.trim().replace("#", "");
        try {
            if (normalized.matches("[0-9a-fA-F]{6}")) {
                return Integer.parseInt(normalized, 16);
            }
            return Integer.parseInt(normalized);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean parseBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private String normalizeReplacementValue(Object value) {
        if (value == null) {
            return "";
        }
        return stripMinecraftFormatting(String.valueOf(value));
    }

    private String stripMinecraftFormatting(String input) {
        String value = input == null ? "" : input;
        value = SECTION_HEX_PATTERN.matcher(value).replaceAll("");
        value = AMPERSAND_HEX_PATTERN.matcher(value).replaceAll("");
        value = LEGACY_COLOR_PATTERN.matcher(value).replaceAll("");
        String stripped = ChatColor.stripColor(value);
        return stripped == null ? "" : stripped;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String summarizeUrl(String targetUrl) {
        try {
            URL url = new URL(targetUrl);
            return url.getProtocol() + "://" + url.getHost();
        } catch (Exception exception) {
            return "the configured endpoint";
        }
    }

    private String safeServerName() {
        return safe(plugin.getServer().getName());
    }

    private String safe(String input) {
        return input == null ? "" : input;
    }
}
