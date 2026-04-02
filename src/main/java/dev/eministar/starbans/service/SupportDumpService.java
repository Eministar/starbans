package dev.eministar.starbans.service;

import com.google.gson.GsonBuilder;
import com.sun.management.OperatingSystemMXBean;
import dev.eministar.starbans.StarBans;
import dev.eministar.starbans.config.BundledYamlConfigSynchronizer;
import dev.eministar.starbans.model.CaseRecord;
import dev.eministar.starbans.model.PlayerProfile;
import dev.eministar.starbans.model.PluginStats;
import dev.eministar.starbans.utils.Version;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.stream.Stream;

public final class SupportDumpService {

    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DISPLAY_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
            .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter UTC_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
            .withZone(ZoneOffset.UTC);
    private static final DecimalFormat NUMBER = new DecimalFormat("0.00");
    private static final int RECENT_CASE_LIMIT = 14;
    private static final int PROFILE_SAMPLE_LIMIT = 12;
    private static final String MASK = "********";

    private final StarBans plugin;

    public SupportDumpService(StarBans plugin) {
        this.plugin = plugin;
    }

    public DumpResult generateDump(CommandSender sender) throws Exception {
        File dumpsDirectory = new File(plugin.getDataFolder(), "dumps");
        Files.createDirectories(dumpsDirectory.toPath());

        Instant now = Instant.now();
        File timestampedFile = new File(dumpsDirectory, FILE_STAMP.format(now) + ".html");
        File latestFile = new File(dumpsDirectory, "latest.html");
        SupportSnapshot snapshot = collectSnapshot(now, sender.getName(), dumpsDirectory, timestampedFile, latestFile);
        String html = buildHtml(snapshot);

        Files.writeString(timestampedFile.toPath(), html, StandardCharsets.UTF_8);
        Files.copy(timestampedFile.toPath(), latestFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return new DumpResult(timestampedFile, latestFile, snapshot.checks());
    }

    public File getLatestDumpFile() {
        return new File(new File(plugin.getDataFolder(), "dumps"), "latest.html");
    }

    private SupportSnapshot collectSnapshot(Instant now,
                                           String triggeredBy,
                                           File dumpsDirectory,
                                           File timestampedFile,
                                           File latestFile) {
        List<DiagnosticCheck> checks = new ArrayList<>();
        PluginStats stats = collectPluginStats(checks);
        int knownProfiles = collectKnownProfileCount(checks);
        checks.addAll(collectChecks(dumpsDirectory));

        List<RecentCaseEntry> recentCases = collectRecentCases(checks);
        List<ProfileEntry> profileSample = collectProfileSample(checks);
        List<OnlinePlayerEntry> onlinePlayers = collectOnlinePlayers();
        List<PluginEntry> installedPlugins = collectInstalledPlugins();
        List<WebhookActionSummary> webhookActions = collectWebhookActions();
        List<CommandOverrideSummary> commandOverrides = collectCommandOverrides();
        List<ServerProfileSummary> serverProfiles = collectServerProfiles();
        List<FileSnapshot> files = collectFileSnapshots(dumpsDirectory, timestampedFile, latestFile);

        return new SupportSnapshot(
                now,
                DISPLAY_STAMP.format(now),
                UTC_STAMP.format(now),
                triggeredBy,
                stats,
                knownProfiles,
                summarizeChecks(checks),
                buildFocusAreas(checks),
                buildEnvironmentMap(now, triggeredBy, dumpsDirectory),
                buildRuntimeMap(),
                buildConfigOverview(webhookActions, commandOverrides, serverProfiles),
                buildModerationOverview(stats, knownProfiles, onlinePlayers, installedPlugins, recentCases, profileSample),
                List.copyOf(checks),
                files,
                webhookActions,
                commandOverrides,
                serverProfiles,
                installedPlugins,
                onlinePlayers,
                recentCases,
                profileSample
        );
    }

    private PluginStats collectPluginStats(List<DiagnosticCheck> checks) {
        try {
            return plugin.getModerationService().getStats();
        } catch (Exception exception) {
            checks.add(new DiagnosticCheck("moderation-stats", Status.FAIL, safeMessage(exception)));
            return new PluginStats(0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    private int collectKnownProfileCount(List<DiagnosticCheck> checks) {
        try {
            return plugin.getModerationService().countKnownProfiles();
        } catch (Exception exception) {
            checks.add(new DiagnosticCheck("known-profiles", Status.FAIL, safeMessage(exception)));
            return 0;
        }
    }

    private List<DiagnosticCheck> collectChecks(File dumpsDirectory) {
        List<DiagnosticCheck> checks = new ArrayList<>();
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        File discordFile = new File(plugin.getDataFolder(), "discord-webhooks.yml");
        File languageFile = new File(plugin.getDataFolder(), plugin.getConfig().getString("settings.language-file", "lang-en.yml"));

        checks.add(checkYaml(configFile, "config.yml"));
        checks.add(checkConfigVersion("config.yml", plugin.getConfig().getString(BundledYamlConfigSynchronizer.VERSION_PATH)));
        checks.add(checkYaml(discordFile, "discord-webhooks.yml"));
        checks.add(checkConfigVersion("discord-webhooks.yml", plugin.getDiscordWebhookConfig().getConfiguration().getString(BundledYamlConfigSynchronizer.VERSION_PATH)));
        checks.add(checkYaml(languageFile, "language file"));
        checks.add(checkWritable(plugin.getDataFolder(), "plugin data folder"));
        checks.add(checkWritable(dumpsDirectory, "dumps folder"));
        checks.add(checkDatabase());
        checks.add(checkServerProfile());
        checks.add(checkWebhookUrls());
        checks.add(checkFeedbackEndpoint());
        checks.add(checkUpdateChecker());
        checks.add(checkNetworkBridge());
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
            String detail = "syntax ok | " + readableBytes(file.length());
            String version = yaml.getString(BundledYamlConfigSynchronizer.VERSION_PATH, "");
            if (version != null && !version.isBlank()) {
                detail += " | version " + version.trim();
            }
            return new DiagnosticCheck(label, Status.PASS, detail);
        } catch (IOException | InvalidConfigurationException exception) {
            return new DiagnosticCheck(label, Status.FAIL, safeMessage(exception));
        }
    }

    private DiagnosticCheck checkConfigVersion(String label, String version) {
        String expected = Version.get();
        if (version == null || version.isBlank()) {
            return new DiagnosticCheck(label + " version", Status.WARN, "missing config-version, expected " + expected);
        }
        if (!expected.equalsIgnoreCase(version.trim())) {
            return new DiagnosticCheck(label + " version", Status.WARN, "config-version=" + version.trim() + ", expected " + expected);
        }
        return new DiagnosticCheck(label + " version", Status.PASS, version.trim());
    }

    private DiagnosticCheck checkWritable(File directory, String label) {
        try {
            Files.createDirectories(directory.toPath());
            File probe = new File(directory, ".starbans-probe");
            Files.writeString(probe.toPath(), "ok", StandardCharsets.UTF_8);
            Files.deleteIfExists(probe.toPath());
            return new DiagnosticCheck(label, Status.PASS, "writable");
        } catch (Exception exception) {
            return new DiagnosticCheck(label, Status.FAIL, safeMessage(exception));
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
        ConfigurationSection section = plugin.getDiscordWebhookConfig().getConfiguration().getConfigurationSection("actions");
        if (section != null) {
            for (String action : section.getKeys(false)) {
                for (String url : collectActionUrls(action)) {
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
        return new DiagnosticCheck("feedback", isHttpUrl(endpoint) ? Status.PASS : Status.WARN, describeUrl(endpoint));
    }

    private DiagnosticCheck checkUpdateChecker() {
        boolean enabled = plugin.getConfig().getBoolean("update-checker.enabled", true);
        if (!enabled) {
            return new DiagnosticCheck("update-checker", Status.PASS, "disabled");
        }

        String versionUrl = plugin.getConfig().getString("update-checker.version-url", "");
        return isHttpUrl(versionUrl)
                ? new DiagnosticCheck("update-checker", Status.PASS, describeUrl(versionUrl))
                : new DiagnosticCheck("update-checker", Status.WARN, "enabled but version-url is invalid");
    }

    private DiagnosticCheck checkNetworkBridge() {
        boolean enabled = plugin.getConfig().getBoolean("network.velocity-bridge.enabled", true);
        String channel = plugin.getConfig().getString("network.velocity-bridge.channel", "");
        if (!enabled) {
            return new DiagnosticCheck("velocity-bridge", Status.PASS, "disabled");
        }
        return channel == null || channel.isBlank()
                ? new DiagnosticCheck("velocity-bridge", Status.FAIL, "enabled but channel is empty")
                : new DiagnosticCheck("velocity-bridge", Status.PASS, channel.trim());
    }

    private DiagnosticCheck checkPlaceholderApi() {
        return Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null
                ? new DiagnosticCheck("placeholderapi", Status.WARN, "not installed")
                : new DiagnosticCheck("placeholderapi", Status.PASS, "detected");
    }

    private List<RecentCaseEntry> collectRecentCases(List<DiagnosticCheck> checks) {
        try {
            return plugin.getModerationService().getRecentCases(RECENT_CASE_LIMIT, 0).stream()
                    .map(this::toRecentCaseEntry)
                    .toList();
        } catch (Exception exception) {
            checks.add(new DiagnosticCheck("recent-cases", Status.WARN, safeMessage(exception)));
            return List.of();
        }
    }

    private RecentCaseEntry toRecentCaseEntry(CaseRecord record) {
        return new RecentCaseEntry(
                "#" + record.getId(),
                enumDisplay(record.getType()),
                defaultValue(record.getTargetPlayerName(), maskIp(record.getTargetIp()), "-"),
                defaultValue(record.getActorName(), "SYSTEM"),
                enumDisplay(record.getStatus()),
                formatTimestamp(record.getCreatedAt()),
                record.getExpiresAt() == null ? "-" : formatTimestamp(record.getExpiresAt()),
                defaultValue(record.getSource(), "-"),
                truncate(defaultValue(record.getReason(), "-"), 120)
        );
    }

    private List<ProfileEntry> collectProfileSample(List<DiagnosticCheck> checks) {
        try {
            return plugin.getModerationService().getKnownProfiles(PROFILE_SAMPLE_LIMIT, 0).stream()
                    .map(profile -> new ProfileEntry(
                            defaultValue(profile.getLastName(), "-"),
                            profile.getUniqueId() == null ? "-" : profile.getUniqueId().toString(),
                            maskIp(profile.getLastIp()),
                            formatTimestamp(profile.getFirstSeen()),
                            formatTimestamp(profile.getLastSeen())
                    ))
                    .toList();
        } catch (Exception exception) {
            checks.add(new DiagnosticCheck("profile-sample", Status.WARN, safeMessage(exception)));
            return List.of();
        }
    }

    private List<OnlinePlayerEntry> collectOnlinePlayers() {
        List<OnlinePlayerEntry> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            players.add(new OnlinePlayerEntry(player.getName(), player.getUniqueId().toString(), player.getWorld().getName()));
        }
        players.sort(Comparator.comparing(OnlinePlayerEntry::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(players);
    }

    private List<PluginEntry> collectInstalledPlugins() {
        List<PluginEntry> plugins = new ArrayList<>();
        for (Plugin installed : Bukkit.getPluginManager().getPlugins()) {
            plugins.add(new PluginEntry(
                    installed.getDescription().getName(),
                    installed.getDescription().getVersion(),
                    installed.isEnabled(),
                    installed.getDescription().getMain()
            ));
        }
        plugins.sort(Comparator.comparing(PluginEntry::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(plugins);
    }

    private List<WebhookActionSummary> collectWebhookActions() {
        List<WebhookActionSummary> actions = new ArrayList<>();
        ConfigurationSection section = plugin.getDiscordWebhookConfig().getConfiguration().getConfigurationSection("actions");
        if (section == null) {
            return List.of();
        }

        List<String> keys = new ArrayList<>(section.getKeys(false));
        keys.sort(String.CASE_INSENSITIVE_ORDER);
        for (String action : keys) {
            List<String> urls = collectActionUrls(action);
            int invalidUrls = 0;
            for (String url : urls) {
                if (!isHttpUrl(url)) {
                    invalidUrls++;
                }
            }

            actions.add(new WebhookActionSummary(
                    action,
                    plugin.getDiscordWebhookConfig().getConfiguration().getBoolean("actions." + action + ".enabled", true),
                    urls.size(),
                    invalidUrls,
                    plugin.getDiscordWebhookConfig().getConfiguration().getMapList("actions." + action + ".embeds").size()
            ));
        }
        return List.copyOf(actions);
    }

    private List<String> collectActionUrls(String action) {
        List<String> urls = new ArrayList<>(plugin.getDiscordWebhookConfig().getConfiguration().getStringList("actions." + action + ".urls"));
        String single = plugin.getDiscordWebhookConfig().getConfiguration().getString("actions." + action + ".url", "");
        if (single != null && !single.isBlank()) {
            urls.add(single.trim());
        }
        return urls;
    }

    private List<CommandOverrideSummary> collectCommandOverrides() {
        List<CommandOverrideSummary> overrides = new ArrayList<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("command-overrides.commands");
        if (section == null) {
            return List.of();
        }

        List<String> keys = new ArrayList<>(section.getKeys(false));
        keys.sort(String.CASE_INSENSITIVE_ORDER);
        for (String key : keys) {
            overrides.add(new CommandOverrideSummary(
                    key,
                    plugin.getConfig().getBoolean("command-overrides.commands." + key + ".enabled", false),
                    plugin.getConfig().getString("command-overrides.commands." + key + ".name", key),
                    plugin.getConfig().getStringList("command-overrides.commands." + key + ".aliases").size()
            ));
        }
        return List.copyOf(overrides);
    }

    private List<ServerProfileSummary> collectServerProfiles() {
        List<ServerProfileSummary> profiles = new ArrayList<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("server-rules.profiles");
        if (section == null) {
            return List.of();
        }

        String activeProfile = plugin.getServerRuleService().getActiveProfileId();
        List<String> keys = new ArrayList<>(section.getKeys(false));
        keys.sort(String.CASE_INSENSITIVE_ORDER);
        for (String key : keys) {
            ConfigurationSection profile = section.getConfigurationSection(key);
            if (profile == null) {
                continue;
            }

            profiles.add(new ServerProfileSummary(
                    key,
                    profile.getString("display-name", key),
                    activeProfile.equalsIgnoreCase(key),
                    sizeOfSection(profile.getConfigurationSection("defaults")),
                    sizeOfSection(profile.getConfigurationSection("default-tags")),
                    sizeOfSection(profile.getConfigurationSection("webhook-actions")),
                    sizeOfSection(profile.getConfigurationSection("flags"))
            ));
        }
        return List.copyOf(profiles);
    }

    private List<FileSnapshot> collectFileSnapshots(File dumpsDirectory, File timestampedFile, File latestFile) {
        List<FileSnapshot> files = new ArrayList<>();
        files.add(snapshotFile("plugin data folder", plugin.getDataFolder(), "plugin workspace"));
        files.add(snapshotFile("dumps folder", dumpsDirectory, "generated dump output"));
        files.add(snapshotFile("config.yml", new File(plugin.getDataFolder(), "config.yml"), "main runtime configuration"));
        files.add(snapshotFile("discord-webhooks.yml", new File(plugin.getDataFolder(), "discord-webhooks.yml"), "webhook layout configuration"));
        files.add(snapshotFile("language file", new File(plugin.getDataFolder(), plugin.getConfig().getString("settings.language-file", "lang-en.yml")), "active language file"));

        File storageTarget = resolveStorageTarget();
        if (storageTarget != null) {
            files.add(snapshotFile("storage target", storageTarget, plugin.getConfig().getString("database.type", "unknown").toUpperCase(Locale.ROOT)));
        }

        files.add(new FileSnapshot("new dump file", timestampedFile.getAbsolutePath(), false, -1L, DISPLAY_STAMP.format(Instant.now()), "will be written by this dump run"));
        files.add(snapshotFile("latest dump", latestFile, "latest generated HTML dump"));
        return List.copyOf(files);
    }

    private FileSnapshot snapshotFile(String label, File file, String note) {
        boolean exists = file.exists();
        long size = file.isDirectory() ? directorySize(file) : (exists ? file.length() : -1L);
        return new FileSnapshot(label, file.getAbsolutePath(), exists, size, exists ? formatFileTimestamp(file) : "-", note);
    }

    private Map<String, String> buildEnvironmentMap(Instant now, String triggeredBy, File dumpsDirectory) {
        Runtime runtime = Runtime.getRuntime();
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

        Map<String, String> values = new LinkedHashMap<>();
        values.put("Plugin", plugin.getDescription().getName());
        values.put("Running Jar Version", Version.get());
        values.put("Descriptor Version", plugin.getDescription().getVersion());
        values.put("Triggered By", triggeredBy);
        values.put("Generated At (Local)", DISPLAY_STAMP.format(now));
        values.put("Generated At (UTC)", UTC_STAMP.format(now));
        values.put("Server", Bukkit.getName() + " " + Bukkit.getVersion());
        values.put("Bukkit API", Bukkit.getBukkitVersion());
        values.put("Java", System.getProperty("java.version") + " | " + System.getProperty("java.vendor"));
        values.put("OS", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        values.put("Architecture", System.getProperty("os.arch"));
        values.put("Processors", String.valueOf(runtime.availableProcessors()));
        values.put("JVM Uptime", formatDuration(runtimeBean.getUptime()));
        values.put("Thread Count", threadBean.getThreadCount() + " (peak " + threadBean.getPeakThreadCount() + ")");
        values.put("Data Folder", plugin.getDataFolder().getAbsolutePath());
        values.put("Dumps Folder", dumpsDirectory.getAbsolutePath());
        return values;
    }

    private Map<String, String> buildRuntimeMap() {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);

        Map<String, String> values = new LinkedHashMap<>();
        values.put("Used Heap", readableBytes(used));
        values.put("Committed Heap", readableBytes(runtime.totalMemory()));
        values.put("Max Heap", readableBytes(runtime.maxMemory()));
        values.put("Process CPU", formatPercent(osBean == null ? -1D : osBean.getProcessCpuLoad()));
        values.put("System CPU", formatPercent(osBean == null ? -1D : osBean.getCpuLoad()));
        values.put("Load Average", osBean == null || osBean.getSystemLoadAverage() < 0 ? "n/a" : NUMBER.format(osBean.getSystemLoadAverage()));
        values.put("Physical Memory", osBean == null ? "n/a" : readableBytes(osBean.getTotalMemorySize()));
        values.put("Free Physical Memory", osBean == null ? "n/a" : readableBytes(osBean.getFreeMemorySize()));
        values.put("Disk Total", readableBytes(plugin.getDataFolder().getTotalSpace()));
        values.put("Disk Free", readableBytes(plugin.getDataFolder().getFreeSpace()));
        values.put("Data Folder Size", readableBytes(directorySize(plugin.getDataFolder())));
        values.put("Latest Dump Size", readableBytes(getLatestDumpFile().exists() ? getLatestDumpFile().length() : -1L));
        return values;
    }

    private Map<String, String> buildConfigOverview(List<WebhookActionSummary> webhookActions,
                                                    List<CommandOverrideSummary> commandOverrides,
                                                    List<ServerProfileSummary> serverProfiles) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("Main Config Version", plugin.getConfig().getString(BundledYamlConfigSynchronizer.VERSION_PATH, "missing"));
        values.put("Discord Config Version", plugin.getDiscordWebhookConfig().getConfiguration().getString(BundledYamlConfigSynchronizer.VERSION_PATH, "missing"));
        values.put("Language File", plugin.getConfig().getString("settings.language-file", "lang-en.yml"));
        values.put("Date Format", plugin.getConfig().getString("settings.date-format", "-"));
        values.put("Timezone", plugin.getConfig().getString("settings.timezone", "system"));
        values.put("Server ID", plugin.getConfig().getString("settings.server-id", "default"));
        values.put("Rule Profile", plugin.getServerRuleService().getActiveProfileId() + " (" + plugin.getServerRuleService().getDisplayName() + ")");
        values.put("Storage Type", plugin.getConfig().getString("database.type", "SQLITE"));
        values.put("Templates Enabled", enabledTemplates() + "/" + totalTemplates());
        values.put("Webhook Actions Enabled", enabledWebhookActions(webhookActions) + "/" + webhookActions.size());
        values.put("Command Overrides Enabled", enabledOverrides(commandOverrides) + "/" + commandOverrides.size());
        values.put("Server Profiles", String.valueOf(serverProfiles.size()));
        values.put("Proxy Support", plugin.getConfig().getBoolean("network.proxy-support.enabled", false)
                ? "enabled (" + plugin.getConfig().getString("network.proxy-support.mode", "NONE") + ")"
                : "disabled");
        values.put("Velocity Bridge", plugin.getConfig().getBoolean("network.velocity-bridge.enabled", true)
                ? "enabled (" + plugin.getConfig().getString("network.velocity-bridge.channel", "-") + ")"
                : "disabled");
        values.put("VPN Detection", plugin.getConfig().getBoolean("security.vpn-detection.enabled", false)
                ? "enabled (" + plugin.getConfig().getString("security.vpn-detection.provider", "unknown") + ")"
                : "disabled");
        values.put("Staff Alerts", plugin.getConfig().getBoolean("staff-alerts.enabled", true) ? "enabled" : "disabled");
        values.put("Alt Detection", plugin.getConfig().getBoolean("alt-detection.enabled", true)
                ? "enabled (score " + plugin.getConfig().getInt("alt-detection.minimum-score", 0) + ")"
                : "disabled");
        values.put("Update Checker", plugin.getConfig().getBoolean("update-checker.enabled", true) ? "enabled" : "disabled");
        values.put("Feedback Endpoint", describeUrl(plugin.getConfig().getString("feedback.endpoint-url", "")));
        return values;
    }

    private Map<String, String> buildModerationOverview(PluginStats stats,
                                                        int knownProfiles,
                                                        List<OnlinePlayerEntry> onlinePlayers,
                                                        List<PluginEntry> installedPlugins,
                                                        List<RecentCaseEntry> recentCases,
                                                        List<ProfileEntry> profileSample) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("Online Players", onlinePlayers.size() + "/" + Bukkit.getMaxPlayers());
        values.put("Known Profiles", String.valueOf(knownProfiles));
        values.put("Total Cases", String.valueOf(stats.totalCases()));
        values.put("Active Bans", String.valueOf(stats.activeBans()));
        values.put("Active IP Bans", String.valueOf(stats.activeIpBans()));
        values.put("Active Mutes", String.valueOf(stats.activeMutes()));
        values.put("Active Warns", String.valueOf(stats.activeWarns()));
        values.put("Active Watchlists", String.valueOf(stats.activeWatchlists()));
        values.put("Recent Cases Sampled", String.valueOf(recentCases.size()));
        values.put("Profiles Sampled", String.valueOf(profileSample.size()));
        values.put("Installed Plugins", String.valueOf(installedPlugins.size()));
        return values;
    }

    private HealthSummary summarizeChecks(List<DiagnosticCheck> checks) {
        int pass = 0;
        int warn = 0;
        int fail = 0;
        for (DiagnosticCheck check : checks) {
            switch (check.status()) {
                case PASS -> pass++;
                case WARN -> warn++;
                case FAIL -> fail++;
            }
        }
        return new HealthSummary(pass, warn, fail, fail > 0 ? Status.FAIL : (warn > 0 ? Status.WARN : Status.PASS));
    }

    private List<String> buildFocusAreas(List<DiagnosticCheck> checks) {
        List<String> focus = new ArrayList<>();
        for (DiagnosticCheck check : checks) {
            if (check.status() != Status.PASS) {
                focus.add(check.name() + ": " + check.detail());
            }
        }
        return List.copyOf(focus);
    }

    private String buildHtml(SupportSnapshot snapshot) {
        StringBuilder html = new StringBuilder(96_000);
        String jsonSnapshot = new GsonBuilder().setPrettyPrinting().create().toJson(buildJsonSnapshot(snapshot));

        html.append(htmlShellStart())
                .append(renderHero(snapshot))
                .append(renderNav())
                .append(renderOverviewSection(snapshot))
                .append(renderDiagnosticsSection(snapshot))
                .append(renderConfigSection(snapshot))
                .append(renderActivitySection(snapshot))
                .append(renderSystemSection(snapshot))
                .append(renderSnapshotSection())
                .append(renderJsonSection(jsonSnapshot))
                .append("<div class=\"foot\">Generated by ")
                .append(escape(plugin.getDescription().getName()))
                .append(" ")
                .append(escape(Version.get()))
                .append(" at ")
                .append(escape(snapshot.generatedAtLocal()))
                .append("</div></div></body></html>");
        return html.toString();
    }

    private Map<String, Object> buildJsonSnapshot(SupportSnapshot snapshot) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("generatedAt", snapshot.generatedAt().toString());
        json.put("generatedAtLocal", snapshot.generatedAtLocal());
        json.put("generatedAtUtc", snapshot.generatedAtUtc());
        json.put("generatedBy", snapshot.triggeredBy());
        json.put("jarVersion", Version.get());
        json.put("stats", snapshot.stats());
        json.put("knownProfiles", snapshot.knownProfiles());
        json.put("healthSummary", snapshot.healthSummary());
        json.put("focusAreas", snapshot.focusAreas());
        json.put("environment", snapshot.environment());
        json.put("runtime", snapshot.runtime());
        json.put("config", snapshot.config());
        json.put("moderation", snapshot.moderation());
        json.put("checks", snapshot.checks());
        json.put("files", snapshot.files());
        json.put("webhookActions", snapshot.webhookActions());
        json.put("commandOverrides", snapshot.commandOverrides());
        json.put("serverProfiles", snapshot.serverProfiles());
        json.put("installedPlugins", snapshot.installedPlugins());
        json.put("onlinePlayers", snapshot.onlinePlayers());
        json.put("recentCases", snapshot.recentCases());
        json.put("profileSample", snapshot.profileSample());
        return json;
    }

    private String htmlShellStart() {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>StarBans Support Dump</title>
                  <style>
                    :root{--bg:#f4efe7;--panel:#fffdf8;--panel-soft:#fff7ed;--line:#e5d8c5;--line-strong:#d8c3a6;--ink:#1f2937;--muted:#64748b;--accent:#c26b1d;--accent-soft:rgba(194,107,29,.12);--green:#1f7a45;--green-soft:rgba(31,122,69,.12);--yellow:#a16207;--yellow-soft:rgba(161,98,7,.12);--red:#b42318;--red-soft:rgba(180,35,24,.10);--shadow:0 20px 50px rgba(71,52,23,.10);--radius:28px}
                    *{box-sizing:border-box}html{scroll-behavior:smooth}body{margin:0;color:var(--ink);font-family:"Segoe UI","SF Pro Text",system-ui,sans-serif;background:radial-gradient(circle at top left,rgba(194,107,29,.15),transparent 28%),radial-gradient(circle at top right,rgba(15,118,110,.10),transparent 24%),linear-gradient(180deg,#fff8ec 0%,var(--bg) 42%,#eef2ef 100%)}h1,h2,h3{margin:0;font-family:Georgia,"Times New Roman",serif;letter-spacing:-.02em}h1{font-size:clamp(34px,5vw,64px);line-height:1.02;margin-top:18px}h2{font-size:28px;line-height:1.1}h3{font-size:18px;line-height:1.15}p{margin:12px 0 0;color:var(--muted);line-height:1.75;font-size:15px}
                    .wrap{max-width:1640px;margin:0 auto;padding:36px 22px 64px}.panel,.hero,.table-panel,.code-panel{background:linear-gradient(180deg,rgba(255,255,255,.88),rgba(255,255,255,.76));border:1px solid var(--line);border-radius:var(--radius);box-shadow:var(--shadow)}.section{padding:26px;margin-bottom:18px}.section-head{display:flex;justify-content:space-between;align-items:flex-end;gap:14px;margin-bottom:18px;flex-wrap:wrap}.section-copy{max-width:780px}
                    .hero{position:relative;overflow:hidden;padding:32px;margin-bottom:18px;background:linear-gradient(135deg,rgba(255,248,236,.98),rgba(255,255,255,.88))}.hero::after{content:"";position:absolute;inset:auto -80px -80px auto;width:260px;height:260px;background:radial-gradient(circle,rgba(194,107,29,.20),transparent 68%);pointer-events:none}.hero-top{display:flex;justify-content:space-between;gap:16px;align-items:flex-start;flex-wrap:wrap}.hero-copy{max-width:920px}
                    .eyebrow{display:inline-flex;align-items:center;gap:8px;padding:8px 14px;border-radius:999px;background:var(--accent-soft);color:var(--accent);font-size:12px;font-weight:800;text-transform:uppercase;letter-spacing:.10em}
                    .metric-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(190px,1fr));gap:14px;margin-top:26px}.metric{padding:18px;border-radius:22px;border:1px solid var(--line);background:linear-gradient(180deg,rgba(255,255,255,.88),rgba(255,250,244,.72))}.metric.pass{background:linear-gradient(180deg,rgba(31,122,69,.10),rgba(255,255,255,.88))}.metric.warn{background:linear-gradient(180deg,rgba(161,98,7,.11),rgba(255,255,255,.88))}.metric.fail{background:linear-gradient(180deg,rgba(180,35,24,.10),rgba(255,255,255,.88))}.metric .label{color:var(--muted);font-size:12px;text-transform:uppercase;letter-spacing:.08em;font-weight:800}.metric .value{margin-top:10px;font-size:30px;font-weight:800;line-height:1.05}.metric .sub{margin-top:8px;color:var(--muted);font-size:13px;line-height:1.45}
                    .nav{position:sticky;top:12px;z-index:10;display:flex;flex-wrap:wrap;gap:10px;padding:14px;margin-bottom:18px;background:rgba(255,253,248,.84);border:1px solid rgba(228,218,203,.88);border-radius:999px;box-shadow:0 10px 30px rgba(71,52,23,.08);backdrop-filter:blur(12px)}.nav a{color:var(--ink);text-decoration:none;padding:9px 14px;border-radius:999px;font-weight:700;font-size:13px;transition:.18s ease}.nav a:hover{background:var(--accent-soft);color:var(--accent)}
                    .split-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(340px,1fr));gap:16px}.kv-card{padding:20px;border-radius:24px;border:1px solid var(--line);background:linear-gradient(180deg,rgba(255,255,255,.92),rgba(255,252,247,.82))}.kv{display:grid;grid-template-columns:minmax(130px,180px) 1fr;gap:10px 14px;margin-top:16px}.kv .k{color:var(--muted);font-size:13px;font-weight:700}.kv .v{font-size:14px;line-height:1.55;word-break:break-word}
                    .badge{display:inline-flex;align-items:center;gap:8px;padding:8px 12px;border-radius:999px;font-weight:800;font-size:12px;letter-spacing:.08em;text-transform:uppercase}.pass-badge{background:var(--green-soft);color:var(--green)}.warn-badge{background:var(--yellow-soft);color:var(--yellow)}.fail-badge{background:var(--red-soft);color:var(--red)}
                    .focus{padding:18px 20px;border-radius:22px;border:1px solid var(--line);background:linear-gradient(180deg,rgba(255,255,255,.95),rgba(255,247,235,.85))}.focus ul{margin:14px 0 0;padding-left:20px}.focus li{margin:8px 0;line-height:1.55}
                    .health{margin-top:18px;padding:18px;border-radius:22px;border:1px solid var(--line);background:linear-gradient(180deg,rgba(255,255,255,.95),rgba(248,245,239,.90))}.bar{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px;margin-top:14px}.bar div{min-height:14px;border-radius:999px}.bar-pass{background:linear-gradient(90deg,#a8d7b4,#1f7a45)}.bar-warn{background:linear-gradient(90deg,#f1d19b,#a16207)}.bar-fail{background:linear-gradient(90deg,#f0b6ae,#b42318)}
                    .table-panel{padding:0;overflow:hidden;margin-top:16px}.table-head{padding:20px 22px 0}.table-wrap{overflow:auto;padding:0 8px 8px}table{width:100%;border-collapse:separate;border-spacing:0;min-width:720px}th,td{padding:14px;text-align:left;vertical-align:top;border-bottom:1px solid var(--line);font-size:14px}th{position:sticky;top:0;background:rgba(255,251,245,.96);color:var(--muted);font-size:12px;letter-spacing:.08em;text-transform:uppercase;font-weight:800}tbody tr:nth-child(odd){background:rgba(255,250,243,.56)}tbody tr:hover{background:rgba(194,107,29,.07)}.empty{padding:18px 20px 22px;color:var(--muted);font-style:italic}
                    details.snapshot{border:1px solid var(--line);border-radius:22px;overflow:hidden;background:var(--panel);margin-bottom:14px}details.snapshot summary{cursor:pointer;list-style:none;padding:18px 20px;display:flex;justify-content:space-between;align-items:center;gap:14px;font-weight:800}details.snapshot summary::-webkit-details-marker{display:none}.snapshot-meta{color:var(--muted);font-weight:700;font-size:12px;text-transform:uppercase;letter-spacing:.08em}
                    pre{margin:0;padding:18px 20px;overflow:auto;background:#fffaf3;border-top:1px solid var(--line)}code{font-family:"Cascadia Code","JetBrains Mono",Consolas,monospace;font-size:13px;line-height:1.65;color:#243247}.foot{text-align:center;color:var(--muted);font-size:13px;padding:12px 0 4px}
                    @media (max-width:900px){.wrap{padding:18px 12px 48px}.hero,.section{padding:20px}.nav{border-radius:24px}.kv{grid-template-columns:1fr}table{min-width:640px}}
                  </style>
                </head>
                <body>
                  <div class="wrap">
                """;
    }

    private String renderHero(SupportSnapshot snapshot) {
        return "<section class=\"hero\" id=\"overview\"><div class=\"hero-top\"><span class=\"eyebrow\">StarBans Support Dump</span>"
                + renderStatusBadge(snapshot.healthSummary().overallStatus(), "Overall " + snapshot.healthSummary().overallStatus().name())
                + "</div><div class=\"hero-copy\"><h1>Support Snapshot</h1><p>Generated by <strong>"
                + escape(snapshot.triggeredBy())
                + "</strong> on <strong>"
                + escape(snapshot.generatedAtLocal())
                + "</strong>. This dump expands the old output with health summaries, runtime telemetry, config matrices, activity samples, plugin inventory and sanitized YAML snapshots.</p></div><div class=\"metric-grid\">"
                + renderMetric("Overall Health", snapshot.healthSummary().overallStatus().name(), snapshot.healthSummary().pass() + " pass | " + snapshot.healthSummary().warn() + " warn | " + snapshot.healthSummary().fail() + " fail", snapshot.healthSummary().overallStatus())
                + renderMetric("Jar Version", Version.get(), "Config versions are synchronized against the running jar version.", Status.PASS)
                + renderMetric("Known Profiles", Integer.toString(snapshot.knownProfiles()), "Stored player profiles currently available to StarBans.", Status.PASS)
                + renderMetric("Total Cases", Integer.toString(snapshot.stats().totalCases()), "All moderation cases currently persisted.", Status.PASS)
                + renderMetric("Online Players", snapshot.onlinePlayers().size() + "/" + Bukkit.getMaxPlayers(), "Live player list captured at dump time.", Status.PASS)
                + renderMetric("Webhook Actions", enabledWebhookActions(snapshot.webhookActions()) + "/" + snapshot.webhookActions().size(), "Enabled Discord actions with configured layouts.", snapshot.webhookActions().stream().anyMatch(action -> action.invalidUrls() > 0) ? Status.WARN : Status.PASS)
                + renderMetric("Command Overrides", enabledOverrides(snapshot.commandOverrides()) + "/" + snapshot.commandOverrides().size(), "Vanilla command takeover definitions currently enabled.", Status.PASS)
                + renderMetric("Data Folder Size", readableBytes(directorySize(plugin.getDataFolder())), "Total footprint of the plugin workspace on disk.", Status.PASS)
                + "</div></section>";
    }

    private String renderNav() {
        return """
                <nav class="nav">
                  <a href="#overview">Overview</a>
                  <a href="#diagnostics">Diagnostics</a>
                  <a href="#config">Config</a>
                  <a href="#activity">Activity</a>
                  <a href="#system">System</a>
                  <a href="#snapshots">Snapshots</a>
                  <a href="#json">JSON</a>
                </nav>
                """;
    }

    private String renderOverviewSection(SupportSnapshot snapshot) {
        return sectionStart("overview-grid", "Overview Grids", "The quick summary below is grouped for fast triage: environment, runtime, config state and moderation numbers.")
                + "<div class=\"split-grid\">"
                + renderKeyValueCard("Environment", "Runtime host and build context.", snapshot.environment())
                + renderKeyValueCard("Runtime", "Heap, CPU, memory and disk telemetry captured during dump creation.", snapshot.runtime())
                + renderKeyValueCard("Configuration", "Effective high-level feature state and config version alignment.", snapshot.config())
                + renderKeyValueCard("Moderation", "Stored moderation totals and sample sizes used in this report.", snapshot.moderation())
                + "</div>" + sectionEnd();
    }

    private String renderDiagnosticsSection(SupportSnapshot snapshot) {
        StringBuilder builder = new StringBuilder(sectionStart("diagnostics", "Diagnostics", "Checks are ordered as a support-first health view. Focus areas are derived from warnings and failures only."));
        if (snapshot.focusAreas().isEmpty()) {
            builder.append("<div class=\"focus\"><strong>No immediate warning signals detected.</strong><p>All diagnostic checks passed for this snapshot.</p></div>");
        } else {
            builder.append("<div class=\"focus\"><strong>Focus Areas</strong><ul>");
            for (String issue : snapshot.focusAreas()) {
                builder.append("<li>").append(escape(issue)).append("</li>");
            }
            builder.append("</ul></div>");
        }

        builder.append("<div class=\"health\"><div class=\"section-head\" style=\"margin-bottom:0\"><div class=\"section-copy\"><h3>Health Breakdown</h3><p>Pass, warning and failure totals across all automated checks.</p></div>")
                .append(renderStatusBadge(snapshot.healthSummary().overallStatus(), snapshot.healthSummary().overallStatus().name()))
                .append("</div><div class=\"bar\"><div class=\"bar-pass\"></div><div class=\"bar-warn\"></div><div class=\"bar-fail\"></div></div></div>");

        List<List<TableCell>> rows = new ArrayList<>();
        for (DiagnosticCheck check : snapshot.checks()) {
            rows.add(List.of(TableCell.text(check.name()), TableCell.html(renderStatusBadge(check.status(), check.status().name())), TableCell.text(check.detail())));
        }
        builder.append(renderTablePanel("Diagnostic Checks", "Every automated validation included in this dump.", List.of("Check", "Status", "Detail"), rows));
        return builder.append(sectionEnd()).toString();
    }

    private String renderConfigSection(SupportSnapshot snapshot) {
        List<List<TableCell>> webhookRows = new ArrayList<>();
        for (WebhookActionSummary action : snapshot.webhookActions()) {
            webhookRows.add(List.of(
                    TableCell.text(action.action()),
                    TableCell.html(renderStatusBadge(action.enabled() ? (action.invalidUrls() > 0 ? Status.WARN : Status.PASS) : Status.WARN, action.enabled() ? "Enabled" : "Disabled")),
                    TableCell.text(Integer.toString(action.urlCount())),
                    TableCell.text(Integer.toString(action.invalidUrls())),
                    TableCell.text(Integer.toString(action.embedCount()))
            ));
        }

        List<List<TableCell>> overrideRows = new ArrayList<>();
        for (CommandOverrideSummary override : snapshot.commandOverrides()) {
            overrideRows.add(List.of(
                    TableCell.text(override.key()),
                    TableCell.html(renderStatusBadge(override.enabled() ? Status.PASS : Status.WARN, override.enabled() ? "Enabled" : "Disabled")),
                    TableCell.text(override.name()),
                    TableCell.text(Integer.toString(override.aliasCount()))
            ));
        }

        List<List<TableCell>> profileRows = new ArrayList<>();
        for (ServerProfileSummary profile : snapshot.serverProfiles()) {
            profileRows.add(List.of(
                    TableCell.text(profile.id()),
                    TableCell.text(profile.displayName()),
                    TableCell.html(renderStatusBadge(profile.active() ? Status.PASS : Status.WARN, profile.active() ? "Active" : "Inactive")),
                    TableCell.text(Integer.toString(profile.defaults())),
                    TableCell.text(Integer.toString(profile.defaultTagGroups())),
                    TableCell.text(Integer.toString(profile.webhookOverrides())),
                    TableCell.text(Integer.toString(profile.flags()))
            ));
        }

        return sectionStart("config", "Config Matrix", "This section maps high-level configuration surfaces into support-friendly tables instead of raw YAML only.")
                + renderTablePanel("Webhook Actions", "Per-action Discord routing and embed coverage.", List.of("Action", "State", "URLs", "Invalid", "Embeds"), webhookRows)
                + renderTablePanel("Command Overrides", "Configured command takeover entries from config.yml.", List.of("Key", "State", "Command", "Aliases"), overrideRows)
                + renderTablePanel("Server Rule Profiles", "Configured moderation profile variants and override density.", List.of("ID", "Display Name", "State", "Defaults", "Tag Groups", "Webhook Overrides", "Flags"), profileRows)
                + sectionEnd();
    }

    private String renderActivitySection(SupportSnapshot snapshot) {
        List<List<TableCell>> recentCaseRows = new ArrayList<>();
        for (RecentCaseEntry entry : snapshot.recentCases()) {
            recentCaseRows.add(List.of(
                    TableCell.text(entry.id()),
                    TableCell.text(entry.type()),
                    TableCell.text(entry.target()),
                    TableCell.text(entry.actor()),
                    TableCell.text(entry.status()),
                    TableCell.text(entry.created()),
                    TableCell.text(entry.expires()),
                    TableCell.text(entry.source()),
                    TableCell.text(entry.reason())
            ));
        }

        List<List<TableCell>> profileRows = new ArrayList<>();
        for (ProfileEntry entry : snapshot.profileSample()) {
            profileRows.add(List.of(
                    TableCell.text(entry.name()),
                    TableCell.text(entry.uuid()),
                    TableCell.text(entry.lastIp()),
                    TableCell.text(entry.firstSeen()),
                    TableCell.text(entry.lastSeen())
            ));
        }

        List<List<TableCell>> onlineRows = new ArrayList<>();
        for (OnlinePlayerEntry player : snapshot.onlinePlayers()) {
            onlineRows.add(List.of(TableCell.text(player.name()), TableCell.text(player.uuid()), TableCell.text(player.world())));
        }

        return sectionStart("activity", "Activity Samples", "These tables provide a fast view of live players, stored profiles and the most recent moderation entries.")
                + renderTablePanel("Recent Cases", "Latest stored moderation events. IPs are masked in this dump view.", List.of("ID", "Type", "Target", "Actor", "Status", "Created", "Expires", "Source", "Reason"), recentCaseRows)
                + renderTablePanel("Known Profile Sample", "Recent sample of stored player profiles with masked IPs.", List.of("Name", "UUID", "Last IP", "First Seen", "Last Seen"), profileRows)
                + renderTablePanel("Online Players", "Players online at the moment the dump was generated.", List.of("Name", "UUID", "World"), onlineRows)
                + sectionEnd();
    }

    private String renderSystemSection(SupportSnapshot snapshot) {
        List<List<TableCell>> pluginRows = new ArrayList<>();
        for (PluginEntry pluginEntry : snapshot.installedPlugins()) {
            pluginRows.add(List.of(
                    TableCell.text(pluginEntry.name()),
                    TableCell.text(pluginEntry.version()),
                    TableCell.html(renderStatusBadge(pluginEntry.enabled() ? Status.PASS : Status.WARN, pluginEntry.enabled() ? "Enabled" : "Disabled")),
                    TableCell.text(pluginEntry.mainClass())
            ));
        }

        List<List<TableCell>> fileRows = new ArrayList<>();
        for (FileSnapshot file : snapshot.files()) {
            fileRows.add(List.of(
                    TableCell.text(file.label()),
                    TableCell.text(file.path()),
                    TableCell.html(renderStatusBadge(file.exists() ? Status.PASS : Status.WARN, file.exists() ? "Present" : "Missing")),
                    TableCell.text(readableBytes(file.size())),
                    TableCell.text(file.modified()),
                    TableCell.text(file.note())
            ));
        }

        return sectionStart("system", "System Inventory", "File and plugin inventories help narrow down environment-level issues quickly.")
                + renderTablePanel("Installed Plugins", "All plugins currently visible through the Bukkit plugin manager.", List.of("Plugin", "Version", "State", "Main Class"), pluginRows)
                + renderTablePanel("File Inventory", "Important files and directories used by StarBans during this run.", List.of("Label", "Path", "State", "Size", "Modified", "Note"), fileRows)
                + sectionEnd();
    }

    private String renderSnapshotSection() {
        return sectionStart("snapshots", "Sanitized Snapshots", "YAML snapshots are rendered from parsed config data and sensitive values such as passwords, API keys and webhook URLs are masked.")
                + renderSnapshotBlock("config.yml", new File(plugin.getDataFolder(), "config.yml"), true)
                + renderSnapshotBlock("discord-webhooks.yml", new File(plugin.getDataFolder(), "discord-webhooks.yml"), false)
                + renderSnapshotBlock(plugin.getConfig().getString("settings.language-file", "lang-en.yml"), new File(plugin.getDataFolder(), plugin.getConfig().getString("settings.language-file", "lang-en.yml")), false)
                + sectionEnd();
    }

    private String renderJsonSection(String jsonSnapshot) {
        return sectionStart("json", "JSON Snapshot", "Structured machine-readable dump content for quick copy/paste into issues or external tooling.")
                + "<div class=\"code-panel\"><pre><code>" + escape(jsonSnapshot) + "</code></pre></div>"
                + sectionEnd();
    }

    private String renderMetric(String label, String value, String sub, Status tone) {
        return "<div class=\"metric " + tone.cssClass() + "\"><div class=\"label\">" + escape(label) + "</div><div class=\"value\">"
                + escape(value) + "</div><div class=\"sub\">" + escape(sub) + "</div></div>";
    }

    private String renderKeyValueCard(String title, String subtitle, Map<String, String> values) {
        StringBuilder builder = new StringBuilder("<div class=\"kv-card\"><h3>").append(escape(title)).append("</h3><p>")
                .append(escape(subtitle)).append("</p><div class=\"kv\">");
        for (Map.Entry<String, String> entry : values.entrySet()) {
            builder.append("<div class=\"k\">").append(escape(entry.getKey())).append("</div><div class=\"v\">").append(escape(entry.getValue())).append("</div>");
        }
        return builder.append("</div></div>").toString();
    }

    private String renderTablePanel(String title, String subtitle, List<String> headers, List<List<TableCell>> rows) {
        StringBuilder builder = new StringBuilder("<div class=\"table-panel\"><div class=\"table-head\"><h3>").append(escape(title)).append("</h3><p>")
                .append(escape(subtitle)).append("</p></div>");
        if (rows.isEmpty()) {
            return builder.append("<div class=\"empty\">No rows available in this snapshot.</div></div>").toString();
        }

        builder.append("<div class=\"table-wrap\"><table><thead><tr>");
        for (String header : headers) {
            builder.append("<th>").append(escape(header)).append("</th>");
        }
        builder.append("</tr></thead><tbody>");
        for (List<TableCell> row : rows) {
            builder.append("<tr>");
            for (TableCell cell : row) {
                builder.append("<td>").append(cell.html() ? cell.value() : escape(cell.value())).append("</td>");
            }
            builder.append("</tr>");
        }
        return builder.append("</tbody></table></div></div>").toString();
    }

    private String renderStatusBadge(Status status, String text) {
        return "<span class=\"badge " + status.cssClass() + "-badge\">" + escape(text) + "</span>";
    }

    private String renderSnapshotBlock(String title, File file, boolean open) {
        String metadata = file.exists() ? readableBytes(file.length()) + " | " + formatFileTimestamp(file) : "missing";
        return "<details class=\"snapshot\"" + (open ? " open" : "") + "><summary><span>" + escape(title)
                + "</span><span class=\"snapshot-meta\">" + escape(metadata) + "</span></summary><pre><code>"
                + escape(readSnapshotSafe(file)) + "</code></pre></details>";
    }

    private String sectionStart(String id, String title, String subtitle) {
        return "<section class=\"panel section\" id=\"" + escape(id) + "\"><div class=\"section-head\"><div class=\"section-copy\"><h2>"
                + escape(title) + "</h2><p>" + escape(subtitle) + "</p></div></div>";
    }

    private String sectionEnd() {
        return "</section>";
    }

    private File resolveStorageTarget() {
        String type = plugin.getConfig().getString("database.type", "SQLITE").toUpperCase(Locale.ROOT);
        String configuredPath = switch (type) {
            case "JSON" -> plugin.getConfig().getString("database.json.file", "");
            case "SQLITE" -> plugin.getConfig().getString("database.sqlite.file", "");
            default -> "";
        };

        if (configuredPath == null || configuredPath.isBlank()) {
            return null;
        }

        File file = new File(configuredPath);
        return file.isAbsolute() ? file : new File(plugin.getDataFolder(), configuredPath);
    }

    private int enabledTemplates() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("templates.entries");
        if (section == null) {
            return 0;
        }

        int count = 0;
        for (String key : section.getKeys(false)) {
            if (plugin.getConfig().getBoolean("templates.entries." + key + ".enabled", true)) {
                count++;
            }
        }
        return count;
    }

    private int totalTemplates() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("templates.entries");
        return section == null ? 0 : section.getKeys(false).size();
    }

    private int enabledWebhookActions(List<WebhookActionSummary> webhookActions) {
        return (int) webhookActions.stream().filter(WebhookActionSummary::enabled).count();
    }

    private int enabledOverrides(List<CommandOverrideSummary> commandOverrides) {
        return (int) commandOverrides.stream().filter(CommandOverrideSummary::enabled).count();
    }

    private int sizeOfSection(ConfigurationSection section) {
        return section == null ? 0 : section.getKeys(false).size();
    }

    private String readSnapshotSafe(File file) {
        if (!file.exists()) {
            return "# missing: " + file.getAbsolutePath();
        }
        if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".yml")) {
            return readFileSafe(file);
        }

        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(file);
            sanitizeYaml(file.getName(), yaml);
            return yaml.saveToString().trim();
        } catch (Exception exception) {
            return "# failed to render sanitized snapshot: " + safeMessage(exception) + System.lineSeparator() + readFileSafe(file);
        }
    }

    private void sanitizeYaml(String fileName, YamlConfiguration yaml) {
        maskPath(yaml, "database.mariadb.password", MASK);
        maskPath(yaml, "security.vpn-detection.api-key", MASK);

        if ("discord-webhooks.yml".equalsIgnoreCase(fileName)) {
            maskUrlPath(yaml, "default-url");
            maskUrlListPath(yaml, "default-urls");
            ConfigurationSection actions = yaml.getConfigurationSection("actions");
            if (actions != null) {
                for (String action : actions.getKeys(false)) {
                    maskUrlPath(yaml, "actions." + action + ".url");
                    maskUrlListPath(yaml, "actions." + action + ".urls");
                }
            }
        }
    }

    private void maskPath(YamlConfiguration yaml, String path, String replacement) {
        if (yaml.contains(path)) {
            yaml.set(path, replacement);
        }
    }

    private void maskUrlPath(YamlConfiguration yaml, String path) {
        if (yaml.contains(path)) {
            yaml.set(path, maskUrlValue(yaml.getString(path, "")));
        }
    }

    private void maskUrlListPath(YamlConfiguration yaml, String path) {
        if (!yaml.contains(path)) {
            return;
        }

        List<String> masked = new ArrayList<>();
        for (String value : yaml.getStringList(path)) {
            masked.add(maskUrlValue(value));
        }
        yaml.set(path, masked);
    }

    private String maskUrlValue(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(url);
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                return MASK;
            }
            return "[" + uri.getHost() + "/...]";
        } catch (Exception exception) {
            return MASK;
        }
    }

    private String formatFileTimestamp(File file) {
        try {
            return DISPLAY_STAMP.format(Files.getLastModifiedTime(file.toPath()).toInstant());
        } catch (IOException exception) {
            return "-";
        }
    }

    private String formatTimestamp(long epochMillis) {
        return formatTimestamp(Long.valueOf(epochMillis));
    }

    private String formatTimestamp(Long epochMillis) {
        if (epochMillis == null || epochMillis <= 0L) {
            return "-";
        }
        return DISPLAY_STAMP.format(Instant.ofEpochMilli(epochMillis));
    }

    private String formatDuration(long millis) {
        Duration duration = Duration.ofMillis(Math.max(0L, millis));
        long days = duration.toDays();
        duration = duration.minusDays(days);
        long hours = duration.toHours();
        duration = duration.minusHours(hours);
        long minutes = duration.toMinutes();
        duration = duration.minusMinutes(minutes);
        long seconds = duration.getSeconds();

        List<String> parts = new ArrayList<>();
        if (days > 0) {
            parts.add(days + "d");
        }
        if (hours > 0) {
            parts.add(hours + "h");
        }
        if (minutes > 0) {
            parts.add(minutes + "m");
        }
        if (seconds > 0 || parts.isEmpty()) {
            parts.add(seconds + "s");
        }
        return String.join(" ", parts);
    }

    private long directorySize(File directory) {
        if (directory == null || !directory.exists()) {
            return -1L;
        }
        if (directory.isFile()) {
            return directory.length();
        }

        try (Stream<java.nio.file.Path> pathStream = Files.walk(directory.toPath())) {
            return pathStream
                    .filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException exception) {
                            return 0L;
                        }
                    })
                    .sum();
        } catch (IOException exception) {
            return -1L;
        }
    }

    private String formatPercent(double value) {
        if (value < 0D) {
            return "n/a";
        }
        return NUMBER.format(value * 100D) + "%";
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return throwable.getClass().getSimpleName() + ": " + message;
    }

    private String enumDisplay(Enum<?> value) {
        if (value == null) {
            return "-";
        }
        return value.name().replace('_', ' ');
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String defaultValue(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return fallback;
    }

    private String truncate(String input, int maxLength) {
        if (input == null || input.length() <= maxLength) {
            return input == null ? "" : input;
        }
        return input.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private String maskIp(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return "-";
        }
        if (ipAddress.contains(".")) {
            String[] parts = ipAddress.split("\\.");
            if (parts.length == 4) {
                return parts[0] + "." + parts[1] + "." + parts[2] + ".x";
            }
        }
        if (ipAddress.contains(":")) {
            String[] parts = ipAddress.split(":");
            if (parts.length >= 2) {
                return parts[0] + ":" + parts[1] + ":****";
            }
        }
        return MASK;
    }

    private String describeUrl(String url) {
        if (url == null || url.isBlank()) {
            return "not configured";
        }
        try {
            URI uri = URI.create(url);
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                return url;
            }
            return uri.getScheme() + "://" + uri.getHost();
        } catch (Exception exception) {
            return url;
        }
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
            return "# failed to read: " + safeMessage(exception);
        }
    }

    private boolean isHttpUrl(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    private String readableBytes(long bytes) {
        if (bytes < 0L) {
            return "n/a";
        }
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

    public record HealthSummary(int pass, int warn, int fail, Status overallStatus) {
    }

    public record FileSnapshot(String label, String path, boolean exists, long size, String modified, String note) {
    }

    public record WebhookActionSummary(String action, boolean enabled, int urlCount, int invalidUrls, int embedCount) {
    }

    public record CommandOverrideSummary(String key, boolean enabled, String name, int aliasCount) {
    }

    public record ServerProfileSummary(String id,
                                       String displayName,
                                       boolean active,
                                       int defaults,
                                       int defaultTagGroups,
                                       int webhookOverrides,
                                       int flags) {
    }

    public record PluginEntry(String name, String version, boolean enabled, String mainClass) {
    }

    public record OnlinePlayerEntry(String name, String uuid, String world) {
    }

    public record RecentCaseEntry(String id,
                                  String type,
                                  String target,
                                  String actor,
                                  String status,
                                  String created,
                                  String expires,
                                  String source,
                                  String reason) {
    }

    public record ProfileEntry(String name, String uuid, String lastIp, String firstSeen, String lastSeen) {
    }

    private record SupportSnapshot(Instant generatedAt,
                                   String generatedAtLocal,
                                   String generatedAtUtc,
                                   String triggeredBy,
                                   PluginStats stats,
                                   int knownProfiles,
                                   HealthSummary healthSummary,
                                   List<String> focusAreas,
                                   Map<String, String> environment,
                                   Map<String, String> runtime,
                                   Map<String, String> config,
                                   Map<String, String> moderation,
                                   List<DiagnosticCheck> checks,
                                   List<FileSnapshot> files,
                                   List<WebhookActionSummary> webhookActions,
                                   List<CommandOverrideSummary> commandOverrides,
                                   List<ServerProfileSummary> serverProfiles,
                                   List<PluginEntry> installedPlugins,
                                   List<OnlinePlayerEntry> onlinePlayers,
                                   List<RecentCaseEntry> recentCases,
                                   List<ProfileEntry> profileSample) {
    }

    private record TableCell(String value, boolean html) {
        private static TableCell text(String value) {
            return new TableCell(value == null ? "" : value, false);
        }

        private static TableCell html(String value) {
            return new TableCell(value == null ? "" : value, true);
        }
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
