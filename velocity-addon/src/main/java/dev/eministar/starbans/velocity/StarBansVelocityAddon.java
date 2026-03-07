package dev.eministar.starbans.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import dev.eministar.starbans.velocity.command.VelocityMainCommand;
import dev.eministar.starbans.velocity.config.YamlConfig;
import dev.eministar.starbans.velocity.database.StorageFactory;
import dev.eministar.starbans.velocity.database.StorageSettings;
import dev.eministar.starbans.velocity.database.StorageType;
import dev.eministar.starbans.velocity.database.VelocityStorage;
import dev.eministar.starbans.velocity.listener.BridgeMessageListener;
import dev.eministar.starbans.velocity.listener.ProxyLoginListener;
import dev.eministar.starbans.velocity.network.SharedNetworkSnapshotService;
import dev.eministar.starbans.velocity.service.ActiveBanEnforcer;
import dev.eministar.starbans.velocity.service.ModerationService;
import dev.eministar.starbans.velocity.task.ActiveBanEnforcementTask;
import dev.eministar.starbans.velocity.util.MessageUtil;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Plugin(
        id = "starbansvelocity",
        name = "StarBans Velocity Addon",
        version = "1.0.0",
        description = "Velocity proxy addon for StarBans shared moderation storage.",
        authors = {"Eministar"}
)
public final class StarBansVelocityAddon {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private YamlConfig config;
    private YamlConfig langConfig;
    private MessageUtil lang;
    private VelocityStorage storage;
    private ModerationService moderationService;
    private ActiveBanEnforcer activeBanEnforcer;
    private SharedNetworkSnapshotService sharedNetworkSnapshotService;
    private com.velocitypowered.api.scheduler.ScheduledTask enforcementTask;

    @Inject
    public StarBansVelocityAddon(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        if (!reload()) {
            return;
        }

        registerBridge();
        server.getEventManager().register(this, new ProxyLoginListener(this));
        server.getEventManager().register(this, new BridgeMessageListener(this));
        registerCommands();
        scheduleEnforcementTask();
        logger.info("StarBans Velocity Addon enabled.");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        closeStorage();
    }

    public boolean reload() {
        try {
            config = new YamlConfig(dataDirectory.resolve("config.yml"), "config.yml", getClass().getClassLoader());
            config.load();

            String languageFile = config.getString("settings.language-file", "lang-en.yml");
            langConfig = new YamlConfig(dataDirectory.resolve(languageFile), languageFile, getClass().getClassLoader());
            langConfig.load();
            lang = new MessageUtil(langConfig);

            closeStorage();
            storage = StorageFactory.create(this);
            storage.init();
            if (sharedNetworkSnapshotService == null) {
                sharedNetworkSnapshotService = new SharedNetworkSnapshotService(this);
            }
            sharedNetworkSnapshotService.reload();
            moderationService = new ModerationService(this, storage);
            activeBanEnforcer = new ActiveBanEnforcer(this);
            logStorageHints(StorageSettings.fromConfig(config));
            registerBridge();
            scheduleEnforcementTask();
            return true;
        } catch (Exception exception) {
            logger.error("StarBans Velocity Addon could not be loaded.", exception);
            return false;
        }
    }

    public ProxyServer getServer() {
        return server;
    }

    public Logger getLogger() {
        return logger;
    }

    public Path getDataDirectory() {
        return dataDirectory;
    }

    public YamlConfig getConfig() {
        return config;
    }

    public MessageUtil getLang() {
        return lang;
    }

    public ModerationService getModerationService() {
        return moderationService;
    }

    public ActiveBanEnforcer getActiveBanEnforcer() {
        return activeBanEnforcer;
    }

    public SharedNetworkSnapshotService getSharedNetworkSnapshotService() {
        return sharedNetworkSnapshotService;
    }

    public String getNetworkText(String path, String fallback, Object... replacements) {
        if (sharedNetworkSnapshotService != null) {
            sharedNetworkSnapshotService.refreshIfNeeded();
        }
        String template = sharedNetworkSnapshotService == null
                ? lang.get(path, fallback)
                : sharedNetworkSnapshotService.getString(path, lang.get(path, fallback));
        return lang.replace(template, replacements);
    }

    public List<String> getNetworkTextList(String path, List<String> fallback, Object... replacements) {
        if (sharedNetworkSnapshotService != null) {
            sharedNetworkSnapshotService.refreshIfNeeded();
        }
        List<String> templates = sharedNetworkSnapshotService == null
                ? fallback
                : sharedNetworkSnapshotService.getStringList(path);
        if (templates.isEmpty()) {
            templates = fallback;
        }
        return templates.stream()
                .map(line -> lang.replace(line, replacements))
                .toList();
    }

    public String getNetworkPrefix() {
        return getNetworkText("general.prefix", lang.get("general.prefix", ""));
    }

    private void registerCommands() {
        CommandManager commandManager = server.getCommandManager();
        List<String> aliases = new ArrayList<>(config.getStringList("commands.aliases"));
        CommandMeta.Builder builder = commandManager.metaBuilder("starbansvelocity");
        if (!aliases.isEmpty()) {
            builder.aliases(aliases.toArray(new String[0]));
        }
        CommandMeta meta = builder.build();
        commandManager.register(meta, new VelocityMainCommand(this));
    }

    private void closeStorage() {
        if (enforcementTask != null) {
            enforcementTask.cancel();
            enforcementTask = null;
        }
        if (storage == null) {
            if (sharedNetworkSnapshotService != null) {
                try {
                    sharedNetworkSnapshotService.close();
                } catch (Exception exception) {
                    logger.error("StarBans Velocity network snapshot storage could not be closed cleanly.", exception);
                }
            }
            return;
        }
        try {
            storage.close();
        } catch (Exception exception) {
            logger.error("StarBans Velocity storage could not be closed cleanly.", exception);
        }
        if (sharedNetworkSnapshotService != null) {
            try {
                sharedNetworkSnapshotService.close();
            } catch (Exception exception) {
                logger.error("StarBans Velocity network snapshot storage could not be closed cleanly.", exception);
            }
        }
        storage = null;
    }

    private void logStorageHints(StorageSettings settings) {
        if (settings.type() == StorageType.JSON) {
            logger.warn("JSON storage is not recommended for proxy + backend setups. Prefer MariaDB.");
        } else if (settings.type() == StorageType.SQLITE) {
            logger.warn("SQLite can work for local proxy + backend setups, but MariaDB is the safer shared-storage option.");
        }
    }

    private void registerBridge() {
        String channel = config.getString("bridge.channel", "starbans:sync").toLowerCase();
        server.getChannelRegistrar().register(MinecraftChannelIdentifier.from(channel));
    }

    private void scheduleEnforcementTask() {
        if (enforcementTask != null) {
            enforcementTask.cancel();
            enforcementTask = null;
        }

        if (!config.getBoolean("sync.online-enforcement.enabled", true)) {
            return;
        }

        long interval = Math.max(1L, config.getLong("sync.online-enforcement.interval-seconds", 2L));
        enforcementTask = server.getScheduler()
                .buildTask(this, new ActiveBanEnforcementTask(this))
                .repeat(interval, TimeUnit.SECONDS)
                .schedule();
    }
}
