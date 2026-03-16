package dev.eministar.starbans.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.eministar.starbans.StarBans;
import dev.eministar.starbans.database.StorageFactory;
import dev.eministar.starbans.database.StorageSettings;
import dev.eministar.starbans.database.StorageType;
import dev.eministar.starbans.utils.LoggerUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SharedNetworkSnapshotPublisher implements AutoCloseable {

    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    private final StarBans plugin;

    private HikariDataSource dataSource;
    private StorageSettings settings;

    public SharedNetworkSnapshotPublisher(StarBans plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        close();
        settings = StorageFactory.readSettings(plugin);
        try {
            init();
            publishSnapshot();
        } catch (Exception exception) {
            LoggerUtil.error("The shared network snapshot could not be published.", exception);
        }
    }

    public void publishSnapshot() {
        if (dataSource == null || settings == null) {
            return;
        }

        String table = settings.table() + "_network_config";
        String payload = gson.toJson(buildSnapshot());

        try (Connection connection = dataSource.getConnection()) {
            if (settings.type() == StorageType.SQLITE) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT OR REPLACE INTO " + table + " (config_key, config_value, updated_at) VALUES (?, ?, ?)"
                )) {
                    statement.setString(1, "snapshot");
                    statement.setString(2, payload);
                    statement.setLong(3, System.currentTimeMillis());
                    statement.executeUpdate();
                }
            } else {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO " + table + " (config_key, config_value, updated_at) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE config_value = VALUES(config_value), updated_at = VALUES(updated_at)"
                )) {
                    statement.setString(1, "snapshot");
                    statement.setString(2, payload);
                    statement.setLong(3, System.currentTimeMillis());
                    statement.executeUpdate();
                }
            }
        } catch (Exception exception) {
            LoggerUtil.error("The shared network snapshot row could not be written.", exception);
        }
    }

    @Override
    public void close() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }

    private void init() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setPoolName("StarBans-NetworkSnapshot-" + settings.type().name());
        config.setConnectionTimeout(settings.connectionTimeoutMillis());

        if (settings.type() == StorageType.SQLITE) {
            Path databaseFile = plugin.getDataFolder().toPath().resolve(settings.sqliteFileName());
            if (Files.notExists(databaseFile.getParent())) {
                Files.createDirectories(databaseFile.getParent());
            }
            config.setJdbcUrl("jdbc:sqlite:" + databaseFile.toAbsolutePath().toString().replace('\\', '/'));
            config.setMaximumPoolSize(1);
            config.setMinimumIdle(1);
            config.setConnectionTestQuery("SELECT 1");
        } else if (settings.type() == StorageType.MARIADB) {
            String jdbcUrl = "jdbc:mariadb://" + settings.mariaHost() + ':' + settings.mariaPort() + '/' + settings.mariaDatabase();
            if (settings.mariaParameters() != null && !settings.mariaParameters().isBlank()) {
                jdbcUrl += '?' + settings.mariaParameters();
            }
            config.setJdbcUrl(jdbcUrl);
            config.setUsername(settings.mariaUsername());
            config.setPassword(settings.mariaPassword());
            config.setMaximumPoolSize(Math.max(1, settings.maximumPoolSize()));
            config.setMinimumIdle(Math.max(0, settings.minimumIdle()));
        } else {
            return;
        }

        LoggerUtil.quietThirdPartyStartupLogs();
        dataSource = new HikariDataSource(config);
        createSchema();
    }

    private void createSchema() throws Exception {
        if (dataSource == null || settings == null) {
            return;
        }

        String table = settings.table() + "_network_config";
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            if (settings.type() == StorageType.SQLITE) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + table + " ("
                        + "config_key VARCHAR(64) PRIMARY KEY, "
                        + "config_value TEXT NOT NULL, "
                        + "updated_at BIGINT NOT NULL)"
                );
            } else {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + table + " ("
                        + "config_key VARCHAR(64) NOT NULL PRIMARY KEY, "
                        + "config_value LONGTEXT NOT NULL, "
                        + "updated_at BIGINT NOT NULL"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
                );
            }
        }
    }

    private Map<String, Object> buildSnapshot() {
        Map<String, Object> root = new LinkedHashMap<>();

        Map<String, Object> general = new LinkedHashMap<>();
        general.put("prefix", plugin.getLang().getRaw("general.prefix", "&8[&#FFB84D&lStarBans&8] &7>> "));
        root.put("general", general);

        Map<String, Object> settingsMap = new LinkedHashMap<>();
        settingsMap.put("date-format", plugin.getConfig().getString("settings.date-format", "dd.MM.yyyy HH:mm:ss"));
        settingsMap.put("timezone", plugin.getConfig().getString("settings.timezone", "system"));
        root.put("settings", settingsMap);

        Map<String, Object> labels = new LinkedHashMap<>();
        labels.put("none", plugin.getLang().getRaw("labels.none", "&7none"));
        root.put("labels", labels);

        Map<String, Object> time = new LinkedHashMap<>();
        time.put("permanent", plugin.getLang().getRaw("time.permanent", "&cPermanent"));
        time.put("expired", plugin.getLang().getRaw("time.expired", "&7Expired"));
        Map<String, Object> units = new LinkedHashMap<>();
        units.put("y", plugin.getLang().getRaw("time.units.y", "y"));
        units.put("mo", plugin.getLang().getRaw("time.units.mo", "mo"));
        units.put("w", plugin.getLang().getRaw("time.units.w", "w"));
        units.put("d", plugin.getLang().getRaw("time.units.d", "d"));
        units.put("h", plugin.getLang().getRaw("time.units.h", "h"));
        units.put("m", plugin.getLang().getRaw("time.units.m", "m"));
        units.put("s", plugin.getLang().getRaw("time.units.s", "s"));
        time.put("units", units);
        root.put("time", time);

        Map<String, Object> messages = new LinkedHashMap<>();
        messages.put("no-permission", plugin.getLang().getRaw("messages.no-permission", "&cYou do not have permission for this action."));
        messages.put("internal-error", plugin.getLang().getRaw("messages.internal-error", "&cAn internal error occurred. Check the console log."));
        messages.put("player-not-found", plugin.getLang().getRaw("messages.player-not-found", "&cNo known player found for &f{player}&c."));
        messages.put("check-header", plugin.getLang().getRaw("messages.check-header", "&8---------- &6Moderation Check for &f{player} &8----------"));
        messages.put("check-lines", plugin.getLang().getRawList("messages.check-lines"));
        root.put("messages", messages);

        Map<String, Object> screens = new LinkedHashMap<>();
        screens.put("player-ban", plugin.getLang().getRawList("screens.player-ban"));
        screens.put("ip-ban", plugin.getLang().getRawList("screens.ip-ban"));
        screens.put("kick", plugin.getLang().getRawList("screens.kick"));
        screens.put("ip-blacklist", plugin.getLang().getRawList("screens.ip-blacklist"));
        root.put("screens", screens);

        return root;
    }
}
