package dev.eministar.starbans.discord;

import dev.eministar.starbans.StarBans;
import dev.eministar.starbans.utils.LoggerUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DiscordRuntimeResolver {

    static final String DEFAULT_JDA_VERSION = "5.5.1";

    private static final ResolvedArtifact ROOT_ARTIFACT = new ResolvedArtifact("net.dv8tion", "JDA", DEFAULT_JDA_VERSION);
    private static final Set<String> ROOT_EXCLUSIONS = Set.of(
            "club.minnced:opus-java",
            "org.slf4j:slf4j-api"
    );
    private static final Set<String> SKIPPED_SCOPES = Set.of("test", "provided", "system");
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private final StarBans plugin;
    private final String repositoryUrl;
    private final int connectTimeout;
    private final int readTimeout;

    private final Map<String, PomModel> pomCache = new HashMap<>();

    DiscordRuntimeResolver(StarBans plugin, String repositoryUrl, int connectTimeout, int readTimeout) {
        this.plugin = plugin;
        this.repositoryUrl = repositoryUrl;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
    }

    RuntimePlan resolve(String versionSelector) throws Exception {
        String selectedRootVersion = resolveRootVersion(versionSelector);
        ResolvedArtifact rootArtifact = ROOT_ARTIFACT.withVersion(selectedRootVersion);

        LinkedHashMap<String, ResolvedArtifact> resolvedArtifacts = new LinkedHashMap<>();
        ArrayDeque<QueuedDependency> queue = new ArrayDeque<>();
        PomModel rootPom = loadPom(rootArtifact);
        for (DependencyDecl dependency : rootPom.dependencies()) {
            queue.addLast(new QueuedDependency(dependency, ROOT_EXCLUSIONS));
        }

        while (!queue.isEmpty()) {
            QueuedDependency queued = queue.removeFirst();
            DependencyDecl dependency = queued.dependency();
            String key = dependency.key();
            if (queued.exclusions().contains(key)) {
                continue;
            }
            if (dependency.optional() || SKIPPED_SCOPES.contains(dependency.scope())) {
                continue;
            }
            if (!"jar".equals(dependency.type())) {
                continue;
            }
            if (resolvedArtifacts.containsKey(key)) {
                continue;
            }

            ResolvedArtifact artifact = dependency.toArtifact();
            resolvedArtifacts.put(key, artifact);

            Set<String> mergedExclusions = new LinkedHashSet<>(queued.exclusions());
            mergedExclusions.addAll(dependency.exclusions());
            PomModel childPom = loadPom(artifact);
            for (DependencyDecl childDependency : childPom.dependencies()) {
                queue.addLast(new QueuedDependency(childDependency, mergedExclusions));
            }
        }

        List<ResolvedArtifact> artifacts = new ArrayList<>(resolvedArtifacts.size() + 1);
        artifacts.add(rootArtifact);
        artifacts.addAll(resolvedArtifacts.values());
        return new RuntimePlan(selectedRootVersion, List.copyOf(artifacts));
    }

    void downloadJar(ResolvedArtifact artifact, Path librariesDirectory, int position, int total) throws Exception {
        Path target = artifact.localJarPath(librariesDirectory);
        Files.createDirectories(target.getParent());

        Path temporary = target.resolveSibling(target.getFileName() + ".part");
        HttpURLConnection connection = null;
        try {
            LoggerUtil.info("Discord libs [" + position + '/' + total + "] Downloading " + artifact.coordinates() + "...");
            connection = openConnection(artifact.jarRepositoryPath());

            int statusCode = connection.getResponseCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw new IllegalStateException("Failed to download " + artifact.coordinates() + " (HTTP " + statusCode + ").");
            }

            try (InputStream inputStream = connection.getInputStream();
                 OutputStream outputStream = Files.newOutputStream(temporary)) {
                inputStream.transferTo(outputStream);
            }

            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            LoggerUtil.info("Discord libs [" + position + '/' + total + "] Ready " + artifact.coordinates()
                    + " (" + formatSize(Files.size(target)) + ").");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            Files.deleteIfExists(temporary);
        }
    }

    private String resolveRootVersion(String versionSelector) throws Exception {
        String selector = versionSelector == null || versionSelector.isBlank() ? "RELEASE" : versionSelector.trim();
        if (!"LATEST".equalsIgnoreCase(selector) && !"RELEASE".equalsIgnoreCase(selector)) {
            return selector;
        }

        Document metadata = loadXml(ROOT_ARTIFACT.metadataRepositoryPath());
        Element versioning = directChild(metadata.getDocumentElement(), "versioning");
        if (versioning == null) {
            throw new IllegalStateException("The Maven metadata for JDA did not contain a versioning section.");
        }

        String directValue = text(directChild(versioning, selector.equalsIgnoreCase("LATEST") ? "latest" : "release"));
        if (!directValue.isBlank()) {
            return directValue;
        }

        Element versionsElement = directChild(versioning, "versions");
        if (versionsElement == null) {
            throw new IllegalStateException("The Maven metadata for JDA did not contain any versions.");
        }

        List<String> versions = new ArrayList<>();
        for (Element versionElement : directChildren(versionsElement, "version")) {
            String value = text(versionElement);
            if (value.isBlank()) {
                continue;
            }
            if ("RELEASE".equalsIgnoreCase(selector) && value.toUpperCase(Locale.ROOT).endsWith("-SNAPSHOT")) {
                continue;
            }
            versions.add(value);
        }
        if (versions.isEmpty()) {
            throw new IllegalStateException("The Maven metadata for JDA did not contain a usable release version.");
        }
        return versions.getLast();
    }

    private PomModel loadPom(ResolvedArtifact artifact) throws Exception {
        PomModel cached = pomCache.get(artifact.coordinates());
        if (cached != null) {
            return cached;
        }

        Document document = loadXml(artifact.pomRepositoryPath());
        Element project = document.getDocumentElement();

        InheritedModel inheritedModel = loadInheritedModel(project);
        Map<String, String> properties = new LinkedHashMap<>(inheritedModel.properties());
        Map<String, String> dependencyManagement = new LinkedHashMap<>(inheritedModel.dependencyManagement());
        seedProjectProperties(properties, artifact);
        mergeProjectProperties(project, properties, artifact);
        mergeDependencyManagement(project, properties, dependencyManagement);
        List<DependencyDecl> dependencies = parseDependencies(project, properties, dependencyManagement);

        PomModel model = new PomModel(properties, dependencyManagement, dependencies);
        pomCache.put(artifact.coordinates(), model);
        return model;
    }

    private InheritedModel loadInheritedModel(Element project) throws Exception {
        Element parentElement = directChild(project, "parent");
        if (parentElement == null) {
            return new InheritedModel(Map.of(), Map.of());
        }

        String groupId = text(directChild(parentElement, "groupId"));
        String artifactId = text(directChild(parentElement, "artifactId"));
        String version = text(directChild(parentElement, "version"));
        if (groupId.isBlank() || artifactId.isBlank() || version.isBlank()) {
            return new InheritedModel(Map.of(), Map.of());
        }

        PomModel parent = loadPom(new ResolvedArtifact(groupId, artifactId, version));
        return new InheritedModel(
                new LinkedHashMap<>(parent.properties()),
                new LinkedHashMap<>(parent.dependencyManagement())
        );
    }

    private void seedProjectProperties(Map<String, String> properties, ResolvedArtifact artifact) {
        properties.put("project.groupId", artifact.groupId());
        properties.put("project.artifactId", artifact.artifactId());
        properties.put("project.version", artifact.version());
        properties.put("pom.groupId", artifact.groupId());
        properties.put("pom.artifactId", artifact.artifactId());
        properties.put("pom.version", artifact.version());
        properties.put("groupId", artifact.groupId());
        properties.put("artifactId", artifact.artifactId());
        properties.put("version", artifact.version());
    }

    private void mergeProjectProperties(Element project, Map<String, String> properties, ResolvedArtifact artifact) {
        Element propertiesElement = directChild(project, "properties");
        if (propertiesElement != null) {
            for (Element child : directChildElements(propertiesElement)) {
                properties.put(child.getTagName(), text(child));
            }
        }

        Map<String, String> resolvedValues = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            resolvedValues.put(entry.getKey(), resolveValue(entry.getValue(), properties));
        }
        properties.clear();
        properties.putAll(resolvedValues);
        seedProjectProperties(properties, artifact);
    }

    private void mergeDependencyManagement(Element project,
                                           Map<String, String> properties,
                                           Map<String, String> dependencyManagement) throws Exception {
        Element dependencyManagementElement = directChild(project, "dependencyManagement");
        if (dependencyManagementElement == null) {
            return;
        }

        Element dependenciesElement = directChild(dependencyManagementElement, "dependencies");
        if (dependenciesElement == null) {
            return;
        }

        for (Element dependencyElement : directChildren(dependenciesElement, "dependency")) {
            DependencyDecl dependency = parseDependency(dependencyElement, properties, dependencyManagement, true);
            if ("pom".equals(dependency.type()) && "import".equals(dependency.scope())) {
                PomModel importedModel = loadPom(dependency.toArtifact());
                for (Map.Entry<String, String> entry : importedModel.dependencyManagement().entrySet()) {
                    dependencyManagement.putIfAbsent(entry.getKey(), entry.getValue());
                }
                continue;
            }
            dependencyManagement.put(dependency.key(), dependency.version());
        }
    }

    private List<DependencyDecl> parseDependencies(Element project,
                                                   Map<String, String> properties,
                                                   Map<String, String> dependencyManagement) {
        Element dependenciesElement = directChild(project, "dependencies");
        if (dependenciesElement == null) {
            return List.of();
        }

        List<DependencyDecl> dependencies = new ArrayList<>();
        for (Element dependencyElement : directChildren(dependenciesElement, "dependency")) {
            dependencies.add(parseDependency(dependencyElement, properties, dependencyManagement, false));
        }
        return List.copyOf(dependencies);
    }

    private DependencyDecl parseDependency(Element dependencyElement,
                                           Map<String, String> properties,
                                           Map<String, String> dependencyManagement,
                                           boolean dependencyManagementSection) {
        String groupId = resolveValue(text(directChild(dependencyElement, "groupId")), properties);
        String artifactId = resolveValue(text(directChild(dependencyElement, "artifactId")), properties);
        if (groupId.isBlank() || artifactId.isBlank()) {
            throw new IllegalStateException("A Maven dependency entry is missing groupId or artifactId.");
        }

        String key = groupId + ':' + artifactId;
        String version = resolveValue(text(directChild(dependencyElement, "version")), properties);
        if (version.isBlank() && !dependencyManagementSection) {
            version = dependencyManagement.getOrDefault(key, "");
        }
        version = resolveValue(version, properties);
        if (version.isBlank()) {
            throw new IllegalStateException("The version for Maven dependency " + key + " could not be resolved.");
        }

        String type = resolveValue(text(directChild(dependencyElement, "type")), properties);
        if (type.isBlank()) {
            type = "jar";
        }

        String scope = resolveValue(text(directChild(dependencyElement, "scope")), properties);
        if (scope.isBlank()) {
            scope = "compile";
        }

        boolean optional = Boolean.parseBoolean(resolveValue(text(directChild(dependencyElement, "optional")), properties));
        Set<String> exclusions = new LinkedHashSet<>();
        Element exclusionsElement = directChild(dependencyElement, "exclusions");
        if (exclusionsElement != null) {
            for (Element exclusionElement : directChildren(exclusionsElement, "exclusion")) {
                String excludedGroupId = resolveValue(text(directChild(exclusionElement, "groupId")), properties);
                String excludedArtifactId = resolveValue(text(directChild(exclusionElement, "artifactId")), properties);
                if (!excludedGroupId.isBlank() && !excludedArtifactId.isBlank()) {
                    exclusions.add(excludedGroupId + ':' + excludedArtifactId);
                }
            }
        }

        return new DependencyDecl(
                groupId,
                artifactId,
                version,
                type.toLowerCase(Locale.ROOT),
                scope.toLowerCase(Locale.ROOT),
                optional,
                Set.copyOf(exclusions)
        );
    }

    private Document loadXml(String repositoryPath) throws Exception {
        HttpURLConnection connection = openConnection(repositoryPath);
        try {
            int statusCode = connection.getResponseCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw new IllegalStateException("Failed to download " + repositoryPath + " (HTTP " + statusCode + ").");
            }

            try (InputStream inputStream = connection.getInputStream()) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setExpandEntityReferences(false);
            factory.setXIncludeAware(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                return factory.newDocumentBuilder().parse(inputStream);
            }
        } finally {
            connection.disconnect();
        }
    }

    private HttpURLConnection openConnection(String repositoryPath) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(repositoryUrl + repositoryPath).toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(connectTimeout);
        connection.setReadTimeout(readTimeout);
        connection.setRequestProperty("User-Agent", "StarBans/" + plugin.getDescription().getVersion());
        return connection;
    }

    private String resolveValue(String input, Map<String, String> properties) {
        if (input == null || input.isBlank()) {
            return "";
        }

        String resolved = input.trim();
        for (int attempt = 0; attempt < 10; attempt++) {
            Matcher matcher = PLACEHOLDER_PATTERN.matcher(resolved);
            if (!matcher.find()) {
                return resolved;
            }

            StringBuffer buffer = new StringBuffer();
            matcher.reset();
            boolean changed = false;
            while (matcher.find()) {
                String replacement = properties.get(matcher.group(1));
                if (replacement == null) {
                    replacement = "";
                } else {
                    changed = true;
                }
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
            }
            matcher.appendTail(buffer);
            resolved = buffer.toString().trim();
            if (!changed) {
                return resolved;
            }
        }
        return resolved;
    }

    private Element directChild(Element parent, String name) {
        for (Element child : directChildElements(parent)) {
            if (name.equals(child.getTagName())) {
                return child;
            }
        }
        return null;
    }

    private List<Element> directChildren(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        for (Element child : directChildElements(parent)) {
            if (name.equals(child.getTagName())) {
                result.add(child);
            }
        }
        return result;
    }

    private List<Element> directChildElements(Element parent) {
        List<Element> result = new ArrayList<>();
        for (int index = 0; index < parent.getChildNodes().getLength(); index++) {
            Node node = parent.getChildNodes().item(index);
            if (node instanceof Element element) {
                result.add(element);
            }
        }
        return result;
    }

    private String text(Element element) {
        return element == null ? "" : element.getTextContent().trim();
    }

    private String formatSize(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return String.format("%.1f KB", bytes / 1024.0D);
        }
        return String.format("%.2f MB", bytes / (1024.0D * 1024.0D));
    }

    record RuntimePlan(String rootVersion, List<ResolvedArtifact> artifacts) {
    }

    record ResolvedArtifact(String groupId, String artifactId, String version) {

        static ResolvedArtifact parse(String coordinates) {
            String[] parts = coordinates.split(":");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid artifact coordinates: " + coordinates);
            }
            return new ResolvedArtifact(parts[0], parts[1], parts[2]);
        }

        String key() {
            return groupId + ':' + artifactId;
        }

        String coordinates() {
            return groupId + ':' + artifactId + ':' + version;
        }

        ResolvedArtifact withVersion(String updatedVersion) {
            return new ResolvedArtifact(groupId, artifactId, updatedVersion);
        }

        String metadataRepositoryPath() {
            return groupId.replace('.', '/') + '/' + artifactId + "/maven-metadata.xml";
        }

        String jarRepositoryPath() {
            return groupId.replace('.', '/') + '/' + artifactId + '/' + version + '/' + fileName(".jar");
        }

        String pomRepositoryPath() {
            return groupId.replace('.', '/') + '/' + artifactId + '/' + version + '/' + fileName(".pom");
        }

        Path localJarPath(Path librariesDirectory) {
            return librariesDirectory
                    .resolve(groupId.replace('.', '/'))
                    .resolve(artifactId)
                    .resolve(version)
                    .resolve(fileName(".jar"));
        }

        private String fileName(String suffix) {
            return artifactId + '-' + version + suffix;
        }
    }

    private record PomModel(Map<String, String> properties,
                            Map<String, String> dependencyManagement,
                            List<DependencyDecl> dependencies) {
    }

    private record InheritedModel(Map<String, String> properties, Map<String, String> dependencyManagement) {
    }

    private record DependencyDecl(String groupId,
                                  String artifactId,
                                  String version,
                                  String type,
                                  String scope,
                                  boolean optional,
                                  Set<String> exclusions) {

        String key() {
            return groupId + ':' + artifactId;
        }

        ResolvedArtifact toArtifact() {
            return new ResolvedArtifact(groupId, artifactId, version);
        }
    }

    private record QueuedDependency(DependencyDecl dependency, Set<String> exclusions) {
    }
}
