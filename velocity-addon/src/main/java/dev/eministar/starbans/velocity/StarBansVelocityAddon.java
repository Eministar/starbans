/*
 * ███████╗████████╗ █████╗ ██████╗ ██████╗  █████╗ ███╗   ██╗███████╗
 * ██╔════╝╚══██╔══╝██╔══██╗██╔══██╗██╔══██╗██╔══██╗████╗  ██║██╔════╝
 * ███████╗   ██║   ███████║██████╔╝██████╔╝███████║██╔██╗ ██║███████╗
 * ╚════██║   ██║   ██╔══██║██╔══██╗██╔══██╗██╔══██║██║╚██╗██║╚════██║
 * ███████║   ██║   ██║  ██║██║  ██║██████╔╝██║  ██║██║ ╚████║███████║
 * ╚══════╝   ╚═╝   ╚═╝  ╚═╝╚═╝  ╚═╝╚═════╝ ╚═╝  ╚═╝╚═╝  ╚═══╝╚══════╝
 *
 * StarBans - Advanced Minecraft Moderation System
 * Copyright (c) 2026 Eministar
 *
 * Licensed under the MIT License.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

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
    private MinecraftChannelIdentifier bridgeIdentifier;

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

        server.getEventManager().register(this, new ProxyLoginListener(this));
        server.getEventManager().register(this, new BridgeMessageListener(this));
        registerCommands();
        logger.info("StarBans Velocity Addon enabled.");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        closeStorage();
    }

    public synchronized boolean reload() {
        YamlConfig loadedConfig = null;
        YamlConfig loadedLangConfig = null;
        VelocityStorage newStorage = null;
        SharedNetworkSnapshotService newSnapshotService = null;
        YamlConfig previousConfig = config;
        YamlConfig previousLangConfig = langConfig;
        MessageUtil previousLang = lang;
        VelocityStorage previousStorage = storage;
        ModerationService previousModerationService = moderationService;
        ActiveBanEnforcer previousActiveBanEnforcer = activeBanEnforcer;
        SharedNetworkSnapshotService previousSnapshotService = sharedNetworkSnapshotService;
        MinecraftChannelIdentifier previousBridgeIdentifier = bridgeIdentifier;
        try {
            loadedConfig = new YamlConfig(dataDirectory.resolve("config.yml"), "config.yml", getClass().getClassLoader());
            loadedConfig.load();
            validateStorageSettings(loadedConfig);

            String languageFile = loadedConfig.getString("settings.language-file", "lang-en.yml");
            loadedLangConfig = new YamlConfig(dataDirectory.resolve(languageFile), languageFile, getClass().getClassLoader());
            loadedLangConfig.load();
            MessageUtil loadedLang = new MessageUtil(loadedLangConfig);

            newStorage = StorageFactory.create(this, loadedConfig);
            newStorage.init();

            newSnapshotService = new SharedNetworkSnapshotService(this);
            newSnapshotService.reload(loadedConfig);
            MinecraftChannelIdentifier nextBridgeIdentifier = resolveBridgeIdentifier(loadedConfig);

            config = loadedConfig;
            langConfig = loadedLangConfig;
            lang = loadedLang;
            storage = newStorage;
            moderationService = new ModerationService(this, newStorage);
            activeBanEnforcer = new ActiveBanEnforcer(this);
            sharedNetworkSnapshotService = newSnapshotService;

            logStorageHints(StorageSettings.fromConfig(loadedConfig));
            updateBridgeRegistration(nextBridgeIdentifier);
            scheduleEnforcementTask();

            closeStorage(previousStorage, previousSnapshotService);
            return true;
        } catch (Exception exception) {
            config = previousConfig;
            langConfig = previousLangConfig;
            lang = previousLang;
            storage = previousStorage;
            moderationService = previousModerationService;
            activeBanEnforcer = previousActiveBanEnforcer;
            sharedNetworkSnapshotService = previousSnapshotService;
            updateBridgeRegistration(previousBridgeIdentifier);
            if (previousConfig != null && previousActiveBanEnforcer != null) {
                scheduleEnforcementTask();
            }
            closeStorage(newStorage, newSnapshotService);
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

    public boolean isFailClosedOnStorageError() {
        return config != null && config.getBoolean("resilience.fail-closed-on-storage-error", true);
    }

    public String getStorageUnavailableText() {
        if (lang == null) {
            return "&cThe moderation backend is currently unavailable. Please try again in a moment.";
        }
        return lang.prefixed("messages.storage-unavailable");
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

        updateBridgeRegistration(null);
        closeStorage(storage, sharedNetworkSnapshotService);
        storage = null;
        sharedNetworkSnapshotService = null;
    }

    private void logStorageHints(StorageSettings settings) {
        if (settings.type() == StorageType.JSON) {
            logger.warn("JSON storage is not recommended for proxy + backend setups. Prefer MariaDB.");
        } else if (settings.type() == StorageType.SQLITE) {
            logger.warn("SQLite can work for local proxy + backend setups, but MariaDB is the safer shared-storage option.");
        }
    }

    private void updateBridgeRegistration() {
        updateBridgeRegistration(resolveBridgeIdentifier(config));
    }

    private void updateBridgeRegistration(MinecraftChannelIdentifier nextIdentifier) {
        if (bridgeIdentifier != null && !bridgeIdentifier.equals(nextIdentifier)) {
            try {
                server.getChannelRegistrar().unregister(bridgeIdentifier);
            } catch (Exception exception) {
                logger.warn("StarBans Velocity bridge channel {} could not be unregistered cleanly.", bridgeIdentifier.getId(), exception);
            }
            bridgeIdentifier = null;
        }

        if (nextIdentifier != null && !nextIdentifier.equals(bridgeIdentifier)) {
            server.getChannelRegistrar().register(nextIdentifier);
            bridgeIdentifier = nextIdentifier;
        }
    }

    private MinecraftChannelIdentifier resolveBridgeIdentifier(YamlConfig configSource) {
        if (configSource == null || !configSource.getBoolean("bridge.enabled", true)) {
            return null;
        }

        String channel = configSource.getString("bridge.channel", "starbans:sync").toLowerCase();
        return MinecraftChannelIdentifier.from(channel);
    }

    private void validateStorageSettings(YamlConfig configSource) {
        StorageSettings settings = StorageSettings.fromConfig(configSource);
        if (settings.type() != StorageType.MARIADB) {
            throw new IllegalStateException(
                    "StarBans Velocity Addon requires database.type=MARIADB for synchronized network moderation. " +
                            "Configured type: " + settings.type().name()
            );
        }
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

    private void closeStorage(VelocityStorage storageToClose, SharedNetworkSnapshotService snapshotServiceToClose) {
        if (storageToClose != null) {
            try {
                storageToClose.close();
            } catch (Exception exception) {
                logger.error("StarBans Velocity storage could not be closed cleanly.", exception);
            }
        }
        if (snapshotServiceToClose != null) {
            try {
                snapshotServiceToClose.close();
            } catch (Exception exception) {
                logger.error("StarBans Velocity network snapshot storage could not be closed cleanly.", exception);
            }
        }
    }
}
