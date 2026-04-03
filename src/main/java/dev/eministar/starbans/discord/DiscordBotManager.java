package dev.eministar.starbans.discord;

import dev.eministar.starbans.StarBans;
import dev.eministar.starbans.utils.LoggerUtil;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

public final class DiscordBotManager implements AutoCloseable {

    private static final String RUNTIME_CLASS = "dev.eministar.starbans.discord.bot.RuntimeDiscordBot";
    private static final String RUNTIME_LOCK_FILE = "discord-runtime.lock";

    private final StarBans plugin;

    private DiscordBotBridge bridge;
    private DiscordBotClassLoader classLoader;

    public DiscordBotManager(StarBans plugin) {
        this.plugin = plugin;
    }

    public synchronized void reload() throws Exception {
        closeActive();

        if (!plugin.getConfig().getBoolean("discord-bot.enabled", false)) {
            return;
        }

        String token = plugin.getConfig().getString("discord-bot.token", "");
        if (token == null || token.isBlank()) {
            LoggerUtil.warn("Discord bot startup skipped because discord-bot.enabled=true but no token is configured.");
            return;
        }

        startBlocking();
    }

    private void startBlocking() throws Exception {
        DiscordBotBridge startedBridge = null;
        DiscordBotClassLoader startedClassLoader = null;
        try {
            List<Path> libraries = ensureLibrariesPresent();
            URL[] urls = buildRuntimeUrls(libraries);
            startedClassLoader = new DiscordBotClassLoader(urls, plugin.getClass().getClassLoader());

            Class<?> runtimeClass = Class.forName(RUNTIME_CLASS, true, startedClassLoader);
            Constructor<?> constructor = runtimeClass.getConstructor(StarBans.class);
            Object runtimeInstance = constructor.newInstance(plugin);
            if (!(runtimeInstance instanceof DiscordBotBridge runtimeBridge)) {
                throw new IllegalStateException("The Discord bot runtime did not implement the expected bridge interface.");
            }

            runtimeBridge.start();
            startedBridge = runtimeBridge;

            bridge = runtimeBridge;
            classLoader = startedClassLoader;
        } catch (Exception exception) {
            if (startedBridge != null) {
                try {
                    startedBridge.close();
                } catch (Exception ignored) {
                }
            }
            if (startedClassLoader != null) {
                try {
                    startedClassLoader.close();
                } catch (Exception ignored) {
                }
            }
            throw exception;
        }
    }

    private List<Path> ensureLibrariesPresent() throws Exception {
        Path librariesDirectory = resolveLibrariesDirectory();
        Files.createDirectories(librariesDirectory);

        String repositoryUrl = normalizeRepositoryUrl(plugin.getConfig().getString("discord-bot.download.repository-url", "https://repo1.maven.org/maven2/"));
        int connectTimeout = Math.max(1000, plugin.getConfig().getInt("discord-bot.download.connect-timeout-ms", 10000));
        int readTimeout = Math.max(1000, plugin.getConfig().getInt("discord-bot.download.read-timeout-ms", 15000));
        boolean autoUpdate = plugin.getConfig().getBoolean("discord-bot.download.auto-update", true);
        boolean cleanupStaleLibraries = plugin.getConfig().getBoolean("discord-bot.download.cleanup-stale-libraries", true);
        String configuredVersion = plugin.getConfig().getString("discord-bot.download.jda-version", "");
        String versionSelector = determineVersionSelector(autoUpdate, configuredVersion);

        DiscordRuntimeResolver resolver = new DiscordRuntimeResolver(plugin, repositoryUrl, connectTimeout, readTimeout);
        Path runtimeLockFile = librariesDirectory.resolve(RUNTIME_LOCK_FILE);
        DiscordRuntimeResolver.RuntimePlan cachedPlan = loadRuntimeLock(runtimeLockFile);
        DiscordRuntimeResolver.RuntimePlan activePlan;
        boolean usedCachedFallback = false;

        try {
            activePlan = resolver.resolve(versionSelector);
        } catch (Exception exception) {
            if (cachedPlan != null && planAvailable(cachedPlan, librariesDirectory)) {
                LoggerUtil.warn("Discord runtime resolution failed. Falling back to the cached Discord runtime.");
                LoggerUtil.warn("Cause: " + exception.getMessage());
                activePlan = cachedPlan;
                usedCachedFallback = true;
            } else {
                throw exception;
            }
        }

        try {
            syncLibraries(resolver, activePlan, librariesDirectory, cleanupStaleLibraries);
        } catch (Exception exception) {
            if (!usedCachedFallback
                    && cachedPlan != null
                    && !activePlan.equals(cachedPlan)
                    && planAvailable(cachedPlan, librariesDirectory)) {
                LoggerUtil.warn("Discord runtime download failed for the newly resolved version. Falling back to the cached Discord runtime.");
                LoggerUtil.warn("Cause: " + exception.getMessage());
                activePlan = cachedPlan;
                syncLibraries(resolver, activePlan, librariesDirectory, false);
            } else {
                throw exception;
            }
        }

        saveRuntimeLock(runtimeLockFile, activePlan);
        return buildLibraryPaths(activePlan, librariesDirectory);
    }

    private void syncLibraries(DiscordRuntimeResolver resolver,
                               DiscordRuntimeResolver.RuntimePlan plan,
                               Path librariesDirectory,
                               boolean cleanupStaleLibraries) throws Exception {
        List<DiscordRuntimeResolver.ResolvedArtifact> missingArtifacts = new ArrayList<>();
        int cachedArtifacts = 0;
        for (DiscordRuntimeResolver.ResolvedArtifact artifact : plan.artifacts()) {
            Path localPath = artifact.localJarPath(librariesDirectory);
            if (Files.notExists(localPath) || Files.size(localPath) <= 0L) {
                missingArtifacts.add(artifact);
            } else {
                cachedArtifacts++;
            }
        }

        LoggerUtil.info("Discord bot runtime target: JDA " + plan.rootVersion()
                + " with " + plan.artifacts().size() + " libraries.");

        if (missingArtifacts.isEmpty()) {
            LoggerUtil.info("Discord bot libraries ready: " + cachedArtifacts + '/' + plan.artifacts().size()
                    + " cached in " + librariesDirectory.toAbsolutePath() + '.');
        } else {
            LoggerUtil.info("Discord bot library bootstrap started in " + librariesDirectory.toAbsolutePath() + '.');
            LoggerUtil.info("Discord bot libraries: " + cachedArtifacts + '/' + plan.artifacts().size()
                    + " cached, " + missingArtifacts.size() + " missing.");
            for (int index = 0; index < missingArtifacts.size(); index++) {
                resolver.downloadJar(missingArtifacts.get(index), librariesDirectory, index + 1, missingArtifacts.size());
            }
            LoggerUtil.info("Discord bot libraries ready: " + plan.artifacts().size() + '/' + plan.artifacts().size()
                    + " available in " + librariesDirectory.toAbsolutePath() + '.');
        }

        if (cleanupStaleLibraries) {
            cleanupStaleLibraries(plan, librariesDirectory);
        }
    }

    private void cleanupStaleLibraries(DiscordRuntimeResolver.RuntimePlan plan, Path librariesDirectory) throws Exception {
        Set<Path> expectedFiles = new HashSet<>();
        for (DiscordRuntimeResolver.ResolvedArtifact artifact : plan.artifacts()) {
            expectedFiles.add(artifact.localJarPath(librariesDirectory).normalize());
        }

        int removedFiles = 0;
        try (Stream<Path> stream = Files.walk(librariesDirectory)) {
            List<Path> candidates = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.endsWith(".jar") || name.endsWith(".part");
                    })
                    .sorted(Comparator.reverseOrder())
                    .toList();
            for (Path candidate : candidates) {
                if (expectedFiles.contains(candidate.normalize())) {
                    continue;
                }
                Files.deleteIfExists(candidate);
                removedFiles++;
            }
        }

        removeEmptyDirectories(librariesDirectory);
        if (removedFiles > 0) {
            LoggerUtil.info("Discord bot cleanup removed " + removedFiles + " stale library file(s).");
        }
    }

    private void removeEmptyDirectories(Path librariesDirectory) throws Exception {
        try (Stream<Path> stream = Files.walk(librariesDirectory)) {
            List<Path> directories = stream
                    .filter(Files::isDirectory)
                    .sorted(Comparator.reverseOrder())
                    .toList();
            for (Path directory : directories) {
                if (directory.equals(librariesDirectory)) {
                    continue;
                }
                try (Stream<Path> children = Files.list(directory)) {
                    if (children.findAny().isEmpty()) {
                        Files.deleteIfExists(directory);
                    }
                }
            }
        }
    }

    private List<Path> buildLibraryPaths(DiscordRuntimeResolver.RuntimePlan plan, Path librariesDirectory) {
        List<Path> result = new ArrayList<>(plan.artifacts().size());
        for (DiscordRuntimeResolver.ResolvedArtifact artifact : plan.artifacts()) {
            result.add(artifact.localJarPath(librariesDirectory));
        }
        return result;
    }

    private boolean planAvailable(DiscordRuntimeResolver.RuntimePlan plan, Path librariesDirectory) {
        for (DiscordRuntimeResolver.ResolvedArtifact artifact : plan.artifacts()) {
            Path localPath = artifact.localJarPath(librariesDirectory);
            try {
                if (Files.notExists(localPath) || Files.size(localPath) <= 0L) {
                    return false;
                }
            } catch (Exception exception) {
                return false;
            }
        }
        return !plan.artifacts().isEmpty();
    }

    private DiscordRuntimeResolver.RuntimePlan loadRuntimeLock(Path runtimeLockFile) {
        if (Files.notExists(runtimeLockFile)) {
            return null;
        }

        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(runtimeLockFile)) {
            properties.load(inputStream);
        } catch (Exception exception) {
            LoggerUtil.warn("The Discord runtime lock file could not be read and will be ignored.");
            return null;
        }

        int artifactCount;
        try {
            artifactCount = Integer.parseInt(properties.getProperty("artifact.count", "0"));
        } catch (NumberFormatException exception) {
            LoggerUtil.warn("The Discord runtime lock file is invalid and will be ignored.");
            return null;
        }

        List<DiscordRuntimeResolver.ResolvedArtifact> artifacts = new ArrayList<>(artifactCount);
        for (int index = 0; index < artifactCount; index++) {
            String coordinates = properties.getProperty("artifact." + index, "");
            if (coordinates == null || coordinates.isBlank()) {
                continue;
            }
            try {
                artifacts.add(DiscordRuntimeResolver.ResolvedArtifact.parse(coordinates));
            } catch (IllegalArgumentException exception) {
                LoggerUtil.warn("The Discord runtime lock file contains an invalid artifact entry and will be ignored.");
                return null;
            }
        }

        if (artifacts.isEmpty()) {
            return null;
        }

        String rootVersion = properties.getProperty("root.version", artifacts.getFirst().version());
        return new DiscordRuntimeResolver.RuntimePlan(rootVersion, List.copyOf(artifacts));
    }

    private void saveRuntimeLock(Path runtimeLockFile, DiscordRuntimeResolver.RuntimePlan plan) throws Exception {
        Properties properties = new Properties();
        properties.setProperty("root.version", plan.rootVersion());
        properties.setProperty("artifact.count", Integer.toString(plan.artifacts().size()));
        for (int index = 0; index < plan.artifacts().size(); index++) {
            properties.setProperty("artifact." + index, plan.artifacts().get(index).coordinates());
        }

        try (OutputStream outputStream = Files.newOutputStream(runtimeLockFile)) {
            properties.store(outputStream, "StarBans Discord runtime lock");
        }
    }

    private URL[] buildRuntimeUrls(List<Path> libraries) throws Exception {
        List<URL> urls = new ArrayList<>(libraries.size() + 1);
        urls.add(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI().toURL());
        for (Path library : libraries) {
            urls.add(library.toUri().toURL());
        }
        return urls.toArray(URL[]::new);
    }

    private Path resolveLibrariesDirectory() {
        String configured = plugin.getConfig().getString("discord-bot.libs-directory", "libs");
        Path path = Path.of(configured == null || configured.isBlank() ? "libs" : configured);
        if (path.isAbsolute()) {
            return path;
        }
        return plugin.getDataFolder().toPath().resolve(path).normalize();
    }

    private String determineVersionSelector(boolean autoUpdate, String configuredVersion) {
        String selector = configuredVersion == null ? "" : configuredVersion.trim();
        if (selector.isBlank()) {
            return autoUpdate ? "RELEASE" : DiscordRuntimeResolver.DEFAULT_JDA_VERSION;
        }
        if (!autoUpdate && isDynamicSelector(selector)) {
            return DiscordRuntimeResolver.DEFAULT_JDA_VERSION;
        }
        return selector;
    }

    private boolean isDynamicSelector(String selector) {
        return "LATEST".equalsIgnoreCase(selector) || "RELEASE".equalsIgnoreCase(selector);
    }

    private String normalizeRepositoryUrl(String input) {
        String value = input == null || input.isBlank() ? "https://repo1.maven.org/maven2/" : input.trim();
        return value.endsWith("/") ? value : value + "/";
    }

    @Override
    public synchronized void close() {
        closeActive();
    }

    private void closeActive() {
        if (bridge != null) {
            try {
                bridge.close();
            } catch (Exception exception) {
                LoggerUtil.error("The Discord bot runtime could not be shut down cleanly.", exception);
            }
            bridge = null;
        }

        if (classLoader != null) {
            try {
                classLoader.close();
            } catch (Exception exception) {
                LoggerUtil.error("The Discord bot classloader could not be closed cleanly.", exception);
            }
            classLoader = null;
        }
    }
}
