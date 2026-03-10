package dev.eministar.starbans.service;

import com.google.gson.GsonBuilder;
import com.sun.management.OperatingSystemMXBean;
import dev.eministar.starbans.StarBans;
import dev.eministar.starbans.model.PluginStats;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

public final class SupportDumpService {

    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.systemDefault());
    private static final DecimalFormat NUMBER = new DecimalFormat("0.00");

    private final StarBans plugin;

    public SupportDumpService(StarBans plugin) {
        this.plugin = plugin;
    }

    public DumpResult generateDump(CommandSender sender) throws Exception {
        File dumpsDirectory = new File(plugin.getDataFolder(), "dumps");
        Files.createDirectories(dumpsDirectory.toPath());

        PluginStats stats = plugin.getModerationService().getStats();
        int knownProfiles = plugin.getModerationService().countKnownProfiles();
        List<DiagnosticCheck> checks = collectChecks(dumpsDirectory);
        Instant now = Instant.now();

        File timestampedFile = new File(dumpsDirectory, FILE_STAMP.format(now) + ".html");
        File latestFile = new File(dumpsDirectory, "latest.html");
        String html = buildHtml(now, sender, stats, knownProfiles, checks);

        Files.writeString(timestampedFile.toPath(), html, StandardCharsets.UTF_8);
        Files.copy(timestampedFile.toPath(), latestFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return new DumpResult(timestampedFile, latestFile, checks);
    }

    public File getLatestDumpFile() {
        return new File(new File(plugin.getDataFolder(), "dumps"), "latest.html");
    }

    private List<DiagnosticCheck> collectChecks(File dumpsDirectory) {
        List<DiagnosticCheck> checks = new ArrayList<>();
        checks.add(checkYaml(new File(plugin.getDataFolder(), "config.yml"), "config.yml"));
        checks.add(checkYaml(new File(plugin.getDataFolder(), "discord-webhooks.yml"), "discord-webhooks.yml"));
        checks.add(checkYaml(new File(plugin.getDataFolder(), plugin.getConfig().getString("settings.language-file", "lang-en.yml")), "language file"));
        checks.add(checkWritable(plugin.getDataFolder(), "plugin data folder"));
        checks.add(checkWritable(dumpsDirectory, "dumps folder"));
        checks.add(checkDatabase());
        checks.add(checkServerProfile());
        checks.add(checkWebhookUrls());
        checks.add(checkFeedbackEndpoint());
        checks.add(checkPlaceholderApi());
        return checks;
    }

    private DiagnosticCheck checkYaml(File file, String label) {
        if (!file.exists()) {
            return new DiagnosticCheck(label, Status.FAIL, "missing: " + file.getAbsolutePath());
        }
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(file);
            return new DiagnosticCheck(label, Status.PASS, "syntax ok | " + readableBytes(file.length()));
        } catch (IOException | InvalidConfigurationException exception) {
            return new DiagnosticCheck(label, Status.FAIL, exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    private DiagnosticCheck checkWritable(File directory, String label) {
        try {
            Files.createDirectories(directory.toPath());
            File probe = new File(directory, ".starbans-probe");
            Files.writeString(probe.toPath(), "ok", StandardCharsets.UTF_8);
            Files.deleteIfExists(probe.toPath());
            return new DiagnosticCheck(label, Status.PASS, "writable");
        } catch (Exception exception) {
            return new DiagnosticCheck(label, Status.FAIL, exception.getClass().getSimpleName());
        }
    }

    private DiagnosticCheck checkDatabase() {
        String type = plugin.getConfig().getString("database.type", "SQLITE").toUpperCase(Locale.ROOT);
        return switch (type) {
            case "JSON" -> new DiagnosticCheck("database", Status.PASS, "JSON -> " + plugin.getConfig().getString("database.json.file", "missing"));
            case "SQLITE" -> new DiagnosticCheck("database", Status.PASS, "SQLite -> " + plugin.getConfig().getString("database.sqlite.file", "missing"));
            case "MARIADB" -> {
                String host = plugin.getConfig().getString("database.mariadb.host", "");
                String database = plugin.getConfig().getString("database.mariadb.database", "");
                yield host.isBlank() || database.isBlank()
                        ? new DiagnosticCheck("database", Status.FAIL, "MariaDB selected but host/database incomplete")
                        : new DiagnosticCheck("database", Status.PASS, "MariaDB -> " + host + "/" + database);
            }
            default -> new DiagnosticCheck("database", Status.FAIL, "unsupported type: " + type);
        };
    }

    private DiagnosticCheck checkServerProfile() {
        return plugin.getServerRuleService().getActiveProfileSection() == null
                ? new DiagnosticCheck("server-rules", Status.WARN, "profile '" + plugin.getServerRuleService().getActiveProfileId() + "' not explicitly configured")
                : new DiagnosticCheck("server-rules", Status.PASS, "active profile: " + plugin.getServerRuleService().getActiveProfileId());
    }

    private DiagnosticCheck checkWebhookUrls() {
        int valid = 0;
        int invalid = 0;
        var section = plugin.getDiscordWebhookConfig().getConfiguration().getConfigurationSection("actions");
        if (section != null) {
            for (String action : section.getKeys(false)) {
                List<String> urls = new ArrayList<>(plugin.getDiscordWebhookConfig().getConfiguration().getStringList("actions." + action + ".urls"));
                String single = plugin.getDiscordWebhookConfig().getConfiguration().getString("actions." + action + ".url", "");
                if (single != null && !single.isBlank()) {
                    urls.add(single);
                }
                for (String url : urls) {
                    if (isHttpUrl(url)) {
                        valid++;
                    } else {
                        invalid++;
                    }
                }
            }
        }
        return new DiagnosticCheck("webhook-urls", invalid > 0 ? Status.WARN : Status.PASS, "valid=" + valid + " invalid=" + invalid);
    }

    private DiagnosticCheck checkFeedbackEndpoint() {
        String endpoint = plugin.getConfig().getString("feedback.endpoint-url", "");
        if (endpoint == null || endpoint.isBlank()) {
            return new DiagnosticCheck("feedback", Status.FAIL, "feedback.endpoint-url is empty");
        }
        return new DiagnosticCheck("feedback", isHttpUrl(endpoint) ? Status.PASS : Status.WARN, endpoint);
    }

    private DiagnosticCheck checkPlaceholderApi() {
        return Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null
                ? new DiagnosticCheck("placeholderapi", Status.WARN, "not installed")
                : new DiagnosticCheck("placeholderapi", Status.PASS, "detected");
    }

    private String buildHtml(Instant now,
                             CommandSender sender,
                             PluginStats stats,
                             int knownProfiles,
                             List<DiagnosticCheck> checks) {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);

        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("Plugin", plugin.getDescription().getName() + " " + plugin.getDescription().getVersion());
        environment.put("Server", Bukkit.getName() + " " + Bukkit.getVersion());
        environment.put("Bukkit", Bukkit.getBukkitVersion());
        environment.put("Java", System.getProperty("java.version") + " | " + System.getProperty("java.vendor"));
        environment.put("OS", System.getProperty("os.name") + " " + System.getProperty("os.version") + " [" + System.getProperty("os.arch") + "]");
        environment.put("Processors", String.valueOf(runtime.availableProcessors()));
        environment.put("Load Average", osBean == null || osBean.getSystemLoadAverage() < 0 ? "n/a" : NUMBER.format(osBean.getSystemLoadAverage()));
        environment.put("Physical Memory", osBean == null ? "n/a" : readableBytes(osBean.getTotalMemorySize()));
        environment.put("Free Physical Memory", osBean == null ? "n/a" : readableBytes(osBean.getFreeMemorySize()));
        environment.put("Data Folder", plugin.getDataFolder().getAbsolutePath());
        environment.put("Triggered By", sender.getName());

        Map<String, String> moderation = new LinkedHashMap<>();
        moderation.put("Used Heap", readableBytes(used));
        moderation.put("Committed Heap", readableBytes(runtime.totalMemory()));
        moderation.put("Max Heap", readableBytes(runtime.maxMemory()));
        moderation.put("Online Players", Bukkit.getOnlinePlayers().size() + "/" + Bukkit.getMaxPlayers());
        moderation.put("Known Profiles", String.valueOf(knownProfiles));
        moderation.put("Total Cases", String.valueOf(stats.totalCases()));
        moderation.put("Active Bans", String.valueOf(stats.activeBans()));
        moderation.put("Active IP Bans", String.valueOf(stats.activeIpBans()));
        moderation.put("Active Mutes", String.valueOf(stats.activeMutes()));
        moderation.put("Active Warns", String.valueOf(stats.activeWarns()));
        moderation.put("Active Watchlists", String.valueOf(stats.activeWatchlists()));

        StringBuilder html = new StringBuilder(32_768);
        html.append("""
                <!DOCTYPE html><html lang="en"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>StarBans Support Dump</title>
                <style>
                :root{--bg:#0d1117;--panel:#161b22;--line:#30363d;--text:#e6edf3;--muted:#8b949e;--accent:#ffb84d;--accentSoft:rgba(255,184,77,.14);--green:#3fb950;--yellow:#d29922;--red:#f85149}
                *{box-sizing:border-box}body{margin:0;font-family:"Segoe UI","SF Pro Display",sans-serif;background:radial-gradient(circle at top left,rgba(255,184,77,.12),transparent 26%),radial-gradient(circle at top right,rgba(59,130,246,.10),transparent 22%),var(--bg);color:var(--text)}
                .wrap{max-width:1440px;margin:0 auto;padding:40px 24px 64px}.hero,.panel,.card{background:linear-gradient(180deg,rgba(255,255,255,.03),rgba(255,255,255,.01));border:1px solid var(--line);border-radius:22px;box-shadow:0 24px 60px rgba(0,0,0,.24)}
                .hero,.panel{padding:24px;margin-bottom:24px}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(320px,1fr));gap:18px}.card{padding:18px}.chip{display:inline-flex;padding:8px 14px;border-radius:999px;background:var(--accentSoft);color:var(--accent);font-size:12px;font-weight:700;letter-spacing:.06em;text-transform:uppercase}
                h1,h2{line-height:1.14}h1{font-size:clamp(30px,4vw,52px);margin:18px 0 10px}h2{font-size:24px;margin:0 0 18px}p{color:var(--muted);line-height:1.7}.kv{display:grid;grid-template-columns:180px 1fr;gap:8px 14px}.kv div:nth-child(odd){color:var(--muted)}
                table{width:100%;border-collapse:collapse}th,td{padding:14px 16px;border-bottom:1px solid var(--line);text-align:left;vertical-align:top}th{color:var(--muted);font-size:12px;text-transform:uppercase;letter-spacing:.08em}tr:last-child td{border-bottom:none}
                .status{display:inline-flex;padding:6px 10px;border-radius:999px;font-size:12px;font-weight:700;letter-spacing:.06em;text-transform:uppercase}.pass{background:rgba(63,185,80,.15);color:var(--green)}.warn{background:rgba(210,153,34,.15);color:var(--yellow)}.fail{background:rgba(248,81,73,.14);color:var(--red)}
                details{border:1px solid var(--line);border-radius:18px;overflow:hidden;background:var(--panel);margin-bottom:16px}summary{padding:16px 18px;cursor:pointer;font-weight:700}pre{margin:0;padding:18px;overflow:auto;background:#0b0f14;border-top:1px solid var(--line)}code{font-family:"Cascadia Code","JetBrains Mono",Consolas,monospace;font-size:13px;line-height:1.6}
                .meta{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:12px;margin-top:22px}.mini{padding:16px;border:1px solid var(--line);border-radius:18px;background:var(--panel)}.mini .label{color:var(--muted);font-size:12px;text-transform:uppercase;letter-spacing:.08em}.mini .value{font-size:24px;font-weight:700;margin-top:8px}.foot{margin-top:28px;text-align:center;color:var(--muted);font-size:13px}
                </style></head><body><div class="wrap">
                """);
        html.append("<section class=\"hero\"><div class=\"chip\">StarBans Support Dump</div><h1>Support Snapshot</h1><p>Generated at <strong>")
                .append(escape(now.toString()))
                .append("</strong>. This report bundles environment info, syntax checks, memory usage and embedded config snapshots in one support-friendly HTML file.</p><div class=\"meta\">")
                .append(metaCard("Server Profile", plugin.getServerRuleService().getDisplayName()))
                .append(metaCard("Storage", plugin.getConfig().getString("database.type", "unknown")))
                .append(metaCard("Language", plugin.getConfig().getString("settings.language-file", "unknown")))
                .append(metaCard("Feedback Endpoint", plugin.getConfig().getString("feedback.endpoint-url", "not configured")))
                .append("</div></section>");

        html.append("<section class=\"grid\">")
                .append(renderKeyValues("Environment", environment))
                .append(renderKeyValues("Runtime & Moderation", moderation))
                .append(renderFilesCard())
                .append("</section>");

        html.append("<section class=\"panel\"><h2>Diagnostic Checks</h2><table><thead><tr><th>Check</th><th>Status</th><th>Detail</th></tr></thead><tbody>");
        for (DiagnosticCheck check : checks) {
            html.append("<tr><td>").append(escape(check.name())).append("</td><td><span class=\"status ").append(check.status().cssClass()).append("\">")
                    .append(check.status().name()).append("</span></td><td>").append(escape(check.detail())).append("</td></tr>");
        }
        html.append("</tbody></table></section>");

        html.append("<section class=\"panel\"><h2>Configuration Snapshots</h2>")
                .append(configBlock("config.yml", new File(plugin.getDataFolder(), "config.yml")))
                .append(configBlock("discord-webhooks.yml", new File(plugin.getDataFolder(), "discord-webhooks.yml")))
                .append(configBlock(plugin.getConfig().getString("settings.language-file", "lang-en.yml"), new File(plugin.getDataFolder(), plugin.getConfig().getString("settings.language-file", "lang-en.yml"))))
                .append("</section>");

        html.append("<section class=\"panel\"><h2>JSON Snapshot</h2><pre><code>")
                .append(escape(new GsonBuilder().setPrettyPrinting().create().toJson(buildJsonSnapshot(now, sender, stats, knownProfiles, checks))))
                .append("</code></pre></section>");
        html.append("<div class=\"foot\">Generated by ").append(escape(plugin.getDescription().getName())).append(" ").append(escape(plugin.getDescription().getVersion())).append("</div></div></body></html>");
        return html.toString();
    }

    private Map<String, Object> buildJsonSnapshot(Instant now, CommandSender sender, PluginStats stats, int knownProfiles, List<DiagnosticCheck> checks) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("generatedAt", now.toString());
        snapshot.put("generatedBy", sender.getName());
        snapshot.put("pluginVersion", plugin.getDescription().getVersion());
        snapshot.put("serverVersion", Bukkit.getVersion());
        snapshot.put("serverProfile", plugin.getServerRuleService().getActiveProfileId());
        snapshot.put("knownProfiles", knownProfiles);
        snapshot.put("stats", stats);
        snapshot.put("checks", checks);
        return snapshot;
    }

    private String renderKeyValues(String title, Map<String, String> values) {
        StringBuilder builder = new StringBuilder("<div class=\"card\"><h2>").append(escape(title)).append("</h2><div class=\"kv\">");
        for (Map.Entry<String, String> entry : values.entrySet()) {
            builder.append("<div>").append(escape(entry.getKey())).append("</div><div>").append(escape(entry.getValue())).append("</div>");
        }
        return builder.append("</div></div>").toString();
    }

    private String renderFilesCard() {
        StringBuilder builder = new StringBuilder("<div class=\"card\"><h2>Files</h2><pre><code>");
        builder.append(escape(fileLine(new File(plugin.getDataFolder(), "config.yml")))).append('\n');
        builder.append(escape(fileLine(new File(plugin.getDataFolder(), "discord-webhooks.yml")))).append('\n');
        builder.append(escape(fileLine(new File(plugin.getDataFolder(), plugin.getConfig().getString("settings.language-file", "lang-en.yml"))))).append('\n');
        builder.append(escape(fileLine(getLatestDumpFile())));
        return builder.append("</code></pre></div>").toString();
    }

    private String configBlock(String title, File file) {
        return "<details open><summary>" + escape(title) + "</summary><pre><code>" + escape(readFileSafe(file)) + "</code></pre></details>";
    }

    private String metaCard(String title, String value) {
        return "<div class=\"mini\"><div class=\"label\">" + escape(title) + "</div><div class=\"value\">" + escape(value) + "</div></div>";
    }

    private String fileLine(File file) {
        return file.exists() ? file.getAbsolutePath() + " [" + readableBytes(file.length()) + "]" : file.getAbsolutePath() + " [missing]";
    }

    private String readFileSafe(File file) {
        if (!file.exists()) {
            return "# missing: " + file.getAbsolutePath();
        }
        try {
            return Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return "# failed to read: " + exception.getMessage();
        }
    }

    private boolean isHttpUrl(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    private String readableBytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double value = bytes;
        String[] units = { "KB", "MB", "GB", "TB" };
        int index = -1;
        while (value >= 1024D && index + 1 < units.length) {
            value /= 1024D;
            index++;
        }
        return NUMBER.format(value) + " " + units[Math.max(0, index)];
    }

    private String escape(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    public record DumpResult(File timestampedFile, File latestFile, List<DiagnosticCheck> checks) {
    }

    public record DiagnosticCheck(String name, Status status, String detail) {
    }

    public enum Status {
        PASS("pass"),
        WARN("warn"),
        FAIL("fail");

        private final String cssClass;

        Status(String cssClass) {
            this.cssClass = cssClass;
        }

        public String cssClass() {
            return cssClass;
        }
    }
}
