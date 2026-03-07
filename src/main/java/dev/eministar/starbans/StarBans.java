package dev.eministar.starbans;

import dev.eministar.starbans.command.DynamicCommandRegistrar;
import dev.eministar.starbans.command.StarBansCommand;
import dev.eministar.starbans.database.ModerationStorage;
import dev.eministar.starbans.database.StorageFactory;
import dev.eministar.starbans.discord.DiscordWebhookService;
import dev.eministar.starbans.listener.ChatListener;
import dev.eministar.starbans.listener.GuiListener;
import dev.eministar.starbans.listener.LoginListener;
import dev.eministar.starbans.network.NetworkSyncService;
import dev.eministar.starbans.network.SharedNetworkSnapshotPublisher;
import dev.eministar.starbans.placeholder.StarBansPlaceholderExpansion;
import dev.eministar.starbans.service.ModerationService;
import dev.eministar.starbans.service.PlayerLookupService;
import dev.eministar.starbans.service.VpnCheckService;
import dev.eministar.starbans.service.GuiInputService;
import dev.eministar.starbans.utils.Banner;
import dev.eministar.starbans.utils.Lang;
import dev.eministar.starbans.utils.LoggerUtil;
import dev.eministar.starbans.utils.UpdateChecker;
import dev.eministar.starbans.utils.Version;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class StarBans extends JavaPlugin {

    private Lang lang;
    private ModerationStorage storage;
    private ModerationService moderationService;
    private PlayerLookupService playerLookupService;
    private DiscordWebhookService discordWebhookService;
    private VpnCheckService vpnCheckService;
    private GuiInputService guiInputService;
    private NetworkSyncService networkSyncService;
    private SharedNetworkSnapshotPublisher sharedNetworkSnapshotPublisher;
    private StarBansPlaceholderExpansion placeholderExpansion;
    private UpdateChecker updateChecker;
    private StarBansCommand commandHandler;
    private DynamicCommandRegistrar dynamicCommandRegistrar;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        installBundledResources();

        Version.init(this);
        LoggerUtil.init(this);
        Banner.print(this);
        networkSyncService = new NetworkSyncService(this);
        sharedNetworkSnapshotPublisher = new SharedNetworkSnapshotPublisher(this);

        registerCommands();

        if (!reloadPluginState()) {
            return;
        }

        registerListeners();

        updateChecker = new UpdateChecker(this);
        getServer().getPluginManager().registerEvents(updateChecker, this);
        updateChecker.reload();

        Banner.printEnabled();
    }

    @Override
    public void onDisable() {
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
            placeholderExpansion = null;
        }

        if (dynamicCommandRegistrar != null) {
            dynamicCommandRegistrar.unregisterAll();
        }

        if (storage != null) {
            try {
                storage.close();
            } catch (Exception exception) {
                LoggerUtil.error("The storage could not be closed cleanly.", exception);
            }
            storage = null;
        }

        if (networkSyncService != null) {
            networkSyncService.unregister();
        }
        if (sharedNetworkSnapshotPublisher != null) {
            sharedNetworkSnapshotPublisher.close();
        }

        LoggerUtil.success("StarBans has been disabled.");
    }

    public boolean reloadPluginState() {
        reloadConfig();
        installBundledResources();

        if (lang == null) {
            lang = new Lang(this);
        } else {
            lang.reload();
        }

        if (storage != null) {
            try {
                storage.close();
            } catch (Exception exception) {
                LoggerUtil.error("The previous storage connection could not be closed.", exception);
            }
        }

        try {
            storage = StorageFactory.create(this);
            storage.init();
        } catch (Exception exception) {
            LoggerUtil.error("The configured storage backend could not be initialized.", exception);
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }

        discordWebhookService = new DiscordWebhookService(this);
        vpnCheckService = new VpnCheckService(this);
        moderationService = new ModerationService(this, storage, lang, discordWebhookService);
        playerLookupService = new PlayerLookupService(this);
        guiInputService = new GuiInputService(this);
        if (networkSyncService != null) {
            networkSyncService.reload();
        }
        if (sharedNetworkSnapshotPublisher != null) {
            sharedNetworkSnapshotPublisher.reload();
        }
        if (networkSyncService != null) {
            networkSyncService.requestImmediateSync();
        }
        syncOnlineProfiles();
        logProxyMode();

        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
            placeholderExpansion = null;
        }
        registerPlaceholderExpansion();

        if (dynamicCommandRegistrar != null && commandHandler != null) {
            dynamicCommandRegistrar.reload(commandHandler);
        }

        if (updateChecker != null) {
            updateChecker.reload();
        }

        return true;
    }

    public Lang getLang() {
        return lang;
    }

    public ModerationStorage getStorage() {
        return storage;
    }

    public ModerationService getModerationService() {
        return moderationService;
    }

    public PlayerLookupService getPlayerLookupService() {
        return playerLookupService;
    }

    public DiscordWebhookService getDiscordWebhookService() {
        return discordWebhookService;
    }

    public VpnCheckService getVpnCheckService() {
        return vpnCheckService;
    }

    public GuiInputService getGuiInputService() {
        return guiInputService;
    }

    public NetworkSyncService getNetworkSyncService() {
        return networkSyncService;
    }

    private void installBundledResources() {
        saveIfMissing("lang-en.yml");
        saveIfMissing("lang-de.yml");
        saveIfMissing("lang.de.yml");
        saveIfMissing("docs/commands.md");
        saveIfMissing("docs/placeholders.md");
        saveIfMissing("docs/database.md");
        saveIfMissing("docs/features.md");
        saveIfMissing("docs/permissions.md");
    }

    private void saveIfMissing(String path) {
        File target = new File(getDataFolder(), path);
        if (!target.exists()) {
            saveResource(path, false);
        }
    }

    private void registerCommands() {
        if (commandHandler == null) {
            commandHandler = new StarBansCommand(this);
        }
        bindCommand("starbans");
        bindCommand("sban");
        bindCommand("stempban");
        bindCommand("sunban");
        bindCommand("sipban");
        bindCommand("stempipban");
        bindCommand("sunipban");
        bindCommand("smute");
        bindCommand("stempmute");
        bindCommand("sunmute");
        bindCommand("skick");
        bindCommand("snote");
        bindCommand("snotes");
        bindCommand("scases");
        bindCommand("salt");
        bindCommand("sipblacklist");
        bindCommand("bancheck");
        bindCommand("banhistory");

        if (dynamicCommandRegistrar == null) {
            dynamicCommandRegistrar = new DynamicCommandRegistrar(this);
        }
        dynamicCommandRegistrar.reload(commandHandler);
    }

    private void bindCommand(String name) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            LoggerUtil.warn("The command '" + name + "' is missing in plugin.yml.");
            return;
        }
        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new LoginListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(), this);
    }

    private void registerPlaceholderExpansion() {
        Plugin placeholderApi = getServer().getPluginManager().getPlugin("PlaceholderAPI");
        if (placeholderApi == null) {
            return;
        }

        placeholderExpansion = new StarBansPlaceholderExpansion(this);
        Logger logger = placeholderApi.getLogger();
        Level previousLevel = logger.getLevel();
        try {
            logger.setLevel(Level.WARNING);
            placeholderExpansion.register();
        } finally {
            logger.setLevel(previousLevel);
        }
    }

    private void syncOnlineProfiles() {
        long now = System.currentTimeMillis();
        for (Player player : getServer().getOnlinePlayers()) {
            try {
                String ipAddress = player.getAddress() == null || player.getAddress().getAddress() == null
                        ? null
                        : player.getAddress().getAddress().getHostAddress();
                moderationService.trackPlayer(
                        new dev.eministar.starbans.model.PlayerIdentity(player.getUniqueId(), player.getName()),
                        ipAddress,
                        now
                );
            } catch (Exception exception) {
                LoggerUtil.error("An online profile could not be synchronized during reload.", exception);
            }
        }
    }

    private void logProxyMode() {
        if (!getConfig().getBoolean("network.proxy-support.enabled", false)) {
            return;
        }

        String mode = getConfig().getString("network.proxy-support.mode", "NONE");
        LoggerUtil.info("Proxy-aware mode enabled for " + mode + ". This Paper/Spigot jar does not run natively on Velocity; a separate proxy bootstrap is required for that.");
    }
}
