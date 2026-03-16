package dev.eministar.starbans.velocity.config;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class YamlConfig {

    private final Yaml yaml = new Yaml();
    private final Path path;
    private final String bundledName;
    private final ClassLoader classLoader;

    private Map<String, Object> data = new LinkedHashMap<>();

    public YamlConfig(Path path, String bundledName, ClassLoader classLoader) {
        this.path = path;
        this.bundledName = bundledName;
        this.classLoader = classLoader;
    }

    public void load() throws IOException {
        if (Files.notExists(path.getParent())) {
            Files.createDirectories(path.getParent());
        }
        if (Files.notExists(path)) {
            InputStream bundled = classLoader.getResourceAsStream(bundledName);
            if (bundled == null && !"lang.yml".equalsIgnoreCase(bundledName)) {
                bundled = classLoader.getResourceAsStream("lang.yml");
            }
            if (bundled == null && !"lang-en.yml".equalsIgnoreCase(bundledName)) {
                bundled = classLoader.getResourceAsStream("lang-en.yml");
            }
            try (InputStream inputStream = bundled) {
                if (inputStream == null) {
                    throw new IOException("Bundled resource not found: " + bundledName);
                }
                Files.copy(inputStream, path);
            }
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            Object loaded = yaml.load(reader);
            if (loaded instanceof Map<?, ?> map) {
                data = convertMap(map);
            } else {
                data = new LinkedHashMap<>();
            }
        }
    }

    public void save() throws IOException {
        if (Files.notExists(path.getParent())) {
            Files.createDirectories(path.getParent());
        }
        try (Writer writer = Files.newBufferedWriter(path)) {
            yaml.dump(data, writer);
        }
    }

    public String getString(String path, String fallback) {
        Object value = get(path);
        return value == null ? fallback : String.valueOf(value);
    }

    public boolean getBoolean(String path, boolean fallback) {
        Object value = get(path);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String string) {
            return Boolean.parseBoolean(string);
        }
        return fallback;
    }

    public int getInt(String path, int fallback) {
        Object value = get(path);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string) {
            try {
                return Integer.parseInt(string);
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    public long getLong(String path, long fallback) {
        Object value = get(path);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string) {
            try {
                return Long.parseLong(string);
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    public double getDouble(String path, double fallback) {
        Object value = get(path);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String string) {
            try {
                return Double.parseDouble(string);
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    public List<String> getStringList(String path) {
        Object value = get(path);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }

        List<String> output = new ArrayList<>(list.size());
        for (Object element : list) {
            output.add(String.valueOf(element));
        }
        return output;
    }

    public Object get(String path) {
        String[] parts = path.split("\\.");
        Object current = data;
        for (String part : parts) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private Map<String, Object> convertMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nestedMap) {
                result.put(key, convertMap(nestedMap));
            } else if (value instanceof List<?> list) {
                result.put(key, convertList(list));
            } else {
                result.put(key, value);
            }
        }
        return result;
    }

    private List<Object> convertList(List<?> source) {
        if (source.isEmpty()) {
            return Collections.emptyList();
        }

        List<Object> result = new ArrayList<>(source.size());
        for (Object value : source) {
            if (value instanceof Map<?, ?> nestedMap) {
                result.add(convertMap(nestedMap));
            } else if (value instanceof List<?> nestedList) {
                result.add(convertList(nestedList));
            } else {
                result.add(value);
            }
        }
        return result;
    }
}
