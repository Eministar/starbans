package dev.eministar.starbans.discord;

import dev.eministar.starbans.StarBans;
import dev.eministar.starbans.utils.LoggerUtil;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public final class DiscordBotManager implements AutoCloseable {

    private static final String RUNTIME_CLASS = "dev.eministar.starbans.discord.bot.RuntimeDiscordBot";
    private static final List<LibraryArtifact> RUNTIME_LIBRARIES = List.of(
            new LibraryArtifact("net.dv8tion", "JDA", "5.5.1"),
            new LibraryArtifact("com.neovisionaries", "nv-websocket-client", "2.14"),
            new LibraryArtifact("com.squareup.okhttp3", "okhttp", "4.12.0"),
            new LibraryArtifact("com.squareup.okio", "okio", "3.6.0"),
            new LibraryArtifact("com.squareup.okio", "okio-jvm", "3.6.0"),
            new LibraryArtifact("org.jetbrains.kotlin", "kotlin-stdlib-jdk8", "1.8.21"),
            new LibraryArtifact("org.jetbrains.kotlin", "kotlin-stdlib", "1.8.21"),
            new LibraryArtifact("org.jetbrains.kotlin", "kotlin-stdlib-jdk7", "1.8.21"),
            new LibraryArtifact("org.jetbrains.kotlin", "kotlin-stdlib-common", "1.9.10"),
            new LibraryArtifact("com.fasterxml.jackson.core", "jackson-core", "2.18.3"),
            new LibraryArtifact("com.fasterxml.jackson.core", "jackson-databind", "2.18.3"),
            new LibraryArtifact("com.fasterxml.jackson.core", "jackson-annotations", "2.18.3")
    );

    private final StarBans plugin;

    private long generation;
    private DiscordBotBridge bridge;
    private DiscordBotClassLoader classLoader;

    public DiscordBotManager(StarBans plugin) {
        this.plugin = plugin;
    }

    public synchronized void reload() {
        generation++;
        long expectedGeneration = generation;
        closeActive();

        if (!plugin.getConfig().getBoolean("discord-bot.enabled", false)) {
            return;
        }

        String token = plugin.getConfig().getString("discord-bot.token", "");
        if (token == null || token.isBlank()) {
            LoggerUtil.warn("Discord bot startup skipped because discord-bot.enabled=true but no token is configured.");
            return;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> startAsync(expectedGeneration));
    }

    private void startAsync(long expectedGeneration) {
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

            synchronized (this) {
                if (generation != expectedGeneration) {
                    runtimeBridge.close();
                    startedClassLoader.close();
                    return;
                }
                bridge = runtimeBridge;
                classLoader = startedClassLoader;
            }
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
            LoggerUtil.error("The optional Discord bot could not be started.", exception);
        }
    }

    private List<Path> ensureLibrariesPresent() throws Exception {
        Path librariesDirectory = resolveLibrariesDirectory();
        Files.createDirectories(librariesDirectory);

        List<LibraryArtifact> missingArtifacts = new ArrayList<>();
        for (LibraryArtifact artifact : RUNTIME_LIBRARIES) {
            if (Files.notExists(artifact.localPath(librariesDirectory)) || Files.size(artifact.localPath(librariesDirectory)) <= 0L) {
                missingArtifacts.add(artifact);
            }
        }

        if (!missingArtifacts.isEmpty()) {
            LoggerUtil.info("Downloading Discord bot libraries into " + librariesDirectory.toAbsolutePath() + ".");
        }

        String repositoryUrl = normalizeRepositoryUrl(plugin.getConfig().getString("discord-bot.download.repository-url", "https://repo1.maven.org/maven2/"));
        int connectTimeout = Math.max(1000, plugin.getConfig().getInt("discord-bot.download.connect-timeout-ms", 10000));
        int readTimeout = Math.max(1000, plugin.getConfig().getInt("discord-bot.download.read-timeout-ms", 15000));

        for (LibraryArtifact artifact : missingArtifacts) {
            downloadArtifact(artifact, librariesDirectory, repositoryUrl, connectTimeout, readTimeout);
        }

        List<Path> result = new ArrayList<>(RUNTIME_LIBRARIES.size());
        for (LibraryArtifact artifact : RUNTIME_LIBRARIES) {
            result.add(artifact.localPath(librariesDirectory));
        }
        return result;
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

    private void downloadArtifact(LibraryArtifact artifact,
                                  Path librariesDirectory,
                                  String repositoryUrl,
                                  int connectTimeout,
                                  int readTimeout) throws Exception {
        Path target = artifact.localPath(librariesDirectory);
        Path temporary = target.resolveSibling(target.getFileName() + ".part");
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(repositoryUrl + artifact.mavenPath()).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(connectTimeout);
            connection.setReadTimeout(readTimeout);
            connection.setRequestProperty("User-Agent", "StarBans/" + plugin.getDescription().getVersion());

            int statusCode = connection.getResponseCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw new IllegalStateException("Failed to download " + artifact.fileName() + " (HTTP " + statusCode + ").");
            }

            try (InputStream inputStream = connection.getInputStream();
                 OutputStream outputStream = Files.newOutputStream(temporary)) {
                inputStream.transferTo(outputStream);
            }

            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            Files.deleteIfExists(temporary);
        }
    }

    private String normalizeRepositoryUrl(String input) {
        String value = input == null || input.isBlank() ? "https://repo1.maven.org/maven2/" : input.trim();
        return value.endsWith("/") ? value : value + "/";
    }

    @Override
    public synchronized void close() {
        generation++;
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

    private record LibraryArtifact(String groupId, String artifactId, String version) {

        private String fileName() {
            return artifactId + '-' + version + ".jar";
        }

        private String mavenPath() {
            return groupId.replace('.', '/') + '/' + artifactId + '/' + version + '/' + fileName();
        }

        private Path localPath(Path librariesDirectory) {
            return librariesDirectory.resolve(fileName());
        }
    }
}
