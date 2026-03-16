package dev.eministar.starbans.velocity.network;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.eministar.starbans.velocity.StarBansVelocityAddon;
import dev.eministar.starbans.velocity.config.YamlConfig;
import dev.eministar.starbans.velocity.database.StorageSettings;
import dev.eministar.starbans.velocity.database.StorageType;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SharedNetworkSnapshotService implements AutoCloseable {

    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {
    }.getType();

    private final Gson gson = new Gson();
    private final StarBansVelocityAddon plugin;

    private HikariDataSource dataSource;
    private StorageSettings settings;
    private Map<String, Object> snapshot = new LinkedHashMap<>();
    private long lastRefresh;

    public SharedNetworkSnapshotService(StarBansVelocityAddon plugin) {
        this.plugin = plugin;
    }

    public void reload() throws Exception {
        reload(plugin.getConfig());
    }

    public void reload(YamlConfig configSource) throws Exception {
        close();
        settings = StorageSettings.fromConfig(configSource);
        if (settings.type() == StorageType.JSON) {
            snapshot = new LinkedHashMap<>();
            lastRefresh = System.currentTimeMillis();
            return;
        }

        HikariConfig config = new HikariConfig();
        config.setPoolName("StarBansVelocity-NetworkSnapshot-" + settings.type().name());
        config.setConnectionTimeout(settings.connectionTimeoutMillis());

        if (settings.type() == StorageType.SQLITE) {
            Path databaseFile = plugin.getDataDirectory().resolve(settings.sqliteFileName());
            if (Files.notExists(databaseFile.getParent())) {
                Files.createDirectories(databaseFile.getParent());
            }
            config.setJdbcUrl("jdbc:sqlite:" + databaseFile.toAbsolutePath().toString().replace('\\', '/'));
            config.setMaximumPoolSize(1);
            config.setMinimumIdle(1);
            config.setConnectionTestQuery("SELECT 1");
        } else {
            String jdbcUrl = "jdbc:mariadb://" + settings.mariaHost() + ':' + settings.mariaPort() + '/' + settings.mariaDatabase();
            if (settings.mariaParameters() != null && !settings.mariaParameters().isBlank()) {
                jdbcUrl += '?' + settings.mariaParameters();
            }
            config.setJdbcUrl(jdbcUrl);
            config.setUsername(settings.mariaUsername());
            config.setPassword(settings.mariaPassword());
            config.setMaximumPoolSize(Math.max(1, settings.maximumPoolSize()));
            config.setMinimumIdle(Math.max(0, settings.minimumIdle()));
        }

        dataSource = new HikariDataSource(config);
        refreshNow();
    }

    public void refreshIfNeeded() {
        long interval = Math.max(2L, plugin.getConfig().getLong("sync.online-enforcement.interval-seconds", 2L));
        if (System.currentTimeMillis() - lastRefresh < interval * 1000L) {
            return;
        }

        try {
            refreshNow();
        } catch (Exception exception) {
            plugin.getLogger().error("The shared network snapshot could not be refreshed.", exception);
        }
    }

    public void forceRefresh() {
        try {
            refreshNow();
        } catch (Exception exception) {
            plugin.getLogger().error("The shared network snapshot could not be refreshed.", exception);
        }
    }

    public String getString(String path, String fallback) {
        Object value = get(path);
        return value == null ? fallback : String.valueOf(value);
    }

    public List<String> getStringList(String path) {
        Object value = get(path);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }

        List<String> output = new ArrayList<>(list.size());
        for (Object entry : list) {
            output.add(String.valueOf(entry));
        }
        return output;
    }

    @Override
    public void close() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }

    private void refreshNow() throws Exception {
        if (dataSource == null || settings == null) {
            return;
        }

        String table = settings.table() + "_network_config";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT config_value FROM " + table + " WHERE config_key = ?")) {
            statement.setString(1, "snapshot");
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    snapshot = new LinkedHashMap<>();
                    lastRefresh = System.currentTimeMillis();
                    return;
                }

                String raw = resultSet.getString(1);
                Map<String, Object> loaded = gson.fromJson(raw, MAP_TYPE);
                snapshot = loaded == null ? new LinkedHashMap<>() : loaded;
                lastRefresh = System.currentTimeMillis();
            }
        }
    }

    private Object get(String path) {
        String[] parts = path.split("\\.");
        Object current = snapshot;
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
}
