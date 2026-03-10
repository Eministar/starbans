package dev.eministar.starbans.service;

import com.google.gson.Gson;
import dev.eministar.starbans.StarBans;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FeedbackService {

    private static final Pattern WEBHOOK_PATTERN = Pattern.compile("https://(?:canary\\.|ptb\\.)?(?:discord\\.com|discordapp\\.com)/api/webhooks/\\S+");

    private final StarBans plugin;
    private final Gson gson = new Gson();

    public FeedbackService(StarBans plugin) {
        this.plugin = plugin;
    }

    public FeedbackResult sendFeedback(CommandSender sender, String message, String latestDumpName) throws Exception {
        String endpoint = plugin.getConfig().getString("feedback.endpoint-url", "");
        if (endpoint == null || endpoint.isBlank()) {
            return new FeedbackResult(false, "feedback.endpoint-url is not configured");
        }

        String webhookUrl = resolveWebhookUrl(endpoint, plugin.getConfig().getInt("feedback.timeout-ms", 5000));
        postFeedback(webhookUrl, sender, message, latestDumpName, plugin.getConfig().getInt("feedback.timeout-ms", 5000));
        return new FeedbackResult(true, webhookUrl);
    }

    private String resolveWebhookUrl(String endpoint, int timeoutMs) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(timeoutMs);
        connection.setReadTimeout(timeoutMs);
        connection.setRequestProperty("User-Agent", "StarBans-Feedback/" + plugin.getDescription().getVersion());

        try (InputStream inputStream = connection.getInputStream()) {
            String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
            if (WEBHOOK_PATTERN.matcher(content).matches()) {
                return content;
            }

            Matcher matcher = WEBHOOK_PATTERN.matcher(content);
            if (matcher.find()) {
                return matcher.group();
            }
            throw new IllegalStateException("Feedback endpoint did not return a Discord webhook URL");
        } finally {
            connection.disconnect();
        }
    }

    private void postFeedback(String webhookUrl,
                              CommandSender sender,
                              String message,
                              String latestDumpName,
                              int timeoutMs) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("username", "StarBans Feedback");
        payload.put("content", "🛰️ **New StarBans feedback** from `" + sender.getName() + "`");

        Map<String, Object> embed = new LinkedHashMap<>();
        embed.put("title", "💬 StarBans Feedback");
        embed.put("description", ">>> " + message + "\n\n**Server:** " + Bukkit.getName() + " " + Bukkit.getVersion());
        embed.put("color", 0xFFB84D);
        embed.put("timestamp", Instant.now().toString());
        embed.put("footer", Map.of("text", plugin.getDescription().getName() + " " + plugin.getDescription().getVersion()));
        embed.put("fields", List.of(
                field("Reporter", sender.getName(), true),
                field("Reporter UUID", sender instanceof Player player ? String.valueOf(player.getUniqueId()) : "console", true),
                field("Server Profile", plugin.getServerRuleService().getDisplayName(), true),
                field("Online Players", Bukkit.getOnlinePlayers().size() + "/" + Bukkit.getMaxPlayers(), true),
                field("Java", System.getProperty("java.version"), true),
                field("Latest Dump", latestDumpName == null || latestDumpName.isBlank() ? "none" : latestDumpName, true)
        ));
        payload.put("embeds", List.of(embed));

        byte[] body = gson.toJson(payload).getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) new URL(webhookUrl).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(timeoutMs);
        connection.setReadTimeout(timeoutMs);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("User-Agent", "StarBans-Feedback/" + plugin.getDescription().getVersion());
        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(body);
        }

        int statusCode = connection.getResponseCode();
        connection.disconnect();
        if (statusCode < 200 || statusCode >= 300) {
            throw new IllegalStateException("Discord webhook returned HTTP " + statusCode);
        }
    }

    private Map<String, Object> field(String name, String value, boolean inline) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("name", name);
        field.put("value", value == null || value.isBlank() ? "n/a" : value);
        field.put("inline", inline);
        return field;
    }

    public record FeedbackResult(boolean success, String detail) {
    }
}
