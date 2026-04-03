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

package dev.eministar.starbans;

import dev.eministar.starbans.command.DynamicCommandRegistrar;
import dev.eministar.starbans.command.StarBansCommand;
import dev.eministar.starbans.config.BundledYamlConfigSynchronizer;
import dev.eministar.starbans.config.DiscordWebhookConfig;
import dev.eministar.starbans.database.ModerationStorage;
import dev.eministar.starbans.database.StorageFactory;
import dev.eministar.starbans.database.StorageSettings;
import dev.eministar.starbans.database.StorageType;
import dev.eministar.starbans.discord.DiscordBotManager;
import dev.eministar.starbans.discord.DiscordWorkflowStateStore;
import dev.eministar.starbans.discord.DiscordWebhookService;
import dev.eministar.starbans.listener.ChatListener;
import dev.eministar.starbans.listener.GuiListener;
import dev.eministar.starbans.listener.LoginListener;
import dev.eministar.starbans.listener.QuarantineListener;
import dev.eministar.starbans.network.NetworkSyncService;
import dev.eministar.starbans.network.SharedNetworkSnapshotPublisher;
import dev.eministar.starbans.placeholder.StarBansPlaceholderExpansion;
import dev.eministar.starbans.service.GuiInputService;
import dev.eministar.starbans.service.AltDetectionService;
import dev.eministar.starbans.service.AuditLogService;
import dev.eministar.starbans.service.CaseExportService;
import dev.eministar.starbans.service.FeedbackService;
import dev.eministar.starbans.service.JoinAlertExemptionService;
import dev.eministar.starbans.service.MigrationService;
import dev.eministar.starbans.service.ModerationService;
import dev.eministar.starbans.service.PlayerLookupService;
import dev.eministar.starbans.service.PunishmentTemplateService;
import dev.eministar.starbans.service.ReviewReminderService;
import dev.eministar.starbans.service.ServerRuleService;
import dev.eministar.starbans.service.SetupService;
import dev.eministar.starbans.service.StaffAlertService;
import dev.eministar.starbans.service.SupportDumpService;
import dev.eministar.starbans.service.VpnCheckService;
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
    private DiscordWebhookConfig discordWebhookConfig;
    private ModerationStorage storage;
    private ModerationService moderationService;
    private PlayerLookupService playerLookupService;
    private DiscordWebhookService discordWebhookService;
    private DiscordBotManager discordBotManager;
    private DiscordWorkflowStateStore discordWorkflowStateStore;
    private StaffAlertService staffAlertService;
    private VpnCheckService vpnCheckService;
    private ServerRuleService serverRuleService;
    private PunishmentTemplateService punishmentTemplateService;
    private AuditLogService auditLogService;
    private CaseExportService caseExportService;
    private SupportDumpService supportDumpService;
    private SetupService setupService;
    private FeedbackService feedbackService;
    private JoinAlertExemptionService joinAlertExemptionService;
    private AltDetectionService altDetectionService;
    private MigrationService migrationService;
    private ReviewReminderService reviewReminderService;
    private GuiInputService guiInputService;
    private NetworkSyncService networkSyncService;
    private SharedNetworkSnapshotPublisher sharedNetworkSnapshotPublisher;
    private StarBansPlaceholderExpansion placeholderExpansion;
    private UpdateChecker updateChecker;
    private StarBansCommand commandHandler;
    private DynamicCommandRegistrar dynamicCommandRegistrar;

    @Override
    public void onEnable() {
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

        if (discordWebhookService != null) {
            discordWebhookService.send("plugin-enabled", "trigger", "startup");
        }

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

        if (discordBotManager != null) {
            discordBotManager.close();
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
        if (reviewReminderService != null) {
            reviewReminderService.close();
        }

        LoggerUtil.success("StarBans has been disabled.");
    }

    public boolean reloadPluginState() {
        synchronizeBundledConfigurations();
        reloadConfig();
        installBundledResources();

        if (discordBotManager == null) {
            discordBotManager = new DiscordBotManager(this);
        } else {
            discordBotManager.close();
        }

        try {
            validateVelocityBridgeStorage();
        } catch (IllegalStateException exception) {
            LoggerUtil.error(exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }

        if (lang == null) {
            lang = new Lang(this);
        } else {
            lang.reload();
        }

        if (discordWebhookConfig == null) {
            discordWebhookConfig = new DiscordWebhookConfig(this);
        } else {
            discordWebhookConfig.reload();
        }

        if (discordWorkflowStateStore == null) {
            discordWorkflowStateStore = new DiscordWorkflowStateStore(this);
        } else {
            discordWorkflowStateStore.reload();
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

        discordWebhookService = new DiscordWebhookService(this, discordWebhookConfig);
        vpnCheckService = new VpnCheckService(this);
        serverRuleService = new ServerRuleService(this);
        punishmentTemplateService = new PunishmentTemplateService(this);
        moderationService = new ModerationService(this, storage, lang, discordWebhookService);
        playerLookupService = new PlayerLookupService(this);
        guiInputService = new GuiInputService(this);
        staffAlertService = new StaffAlertService(this);
        auditLogService = new AuditLogService(storage);
        caseExportService = new CaseExportService(this, moderationService);
        supportDumpService = new SupportDumpService(this);
        setupService = new SetupService(this);
        feedbackService = new FeedbackService(this);
        joinAlertExemptionService = new JoinAlertExemptionService(this, serverRuleService);
        altDetectionService = new AltDetectionService(this, moderationService);
        migrationService = new MigrationService(this);
        if (reviewReminderService == null) {
            reviewReminderService = new ReviewReminderService(this);
        }
        reviewReminderService.reload();
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

        try {
            discordBotManager.reload();
        } catch (Exception exception) {
            LoggerUtil.error("The optional Discord bot could not be initialized.", exception);
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }

        return true;
    }

    public Lang getLang() {
        return lang;
    }

    public DiscordWebhookConfig getDiscordWebhookConfig() {
        return discordWebhookConfig;
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

    public DiscordBotManager getDiscordBotManager() {
        return discordBotManager;
    }

    public DiscordWorkflowStateStore getDiscordWorkflowStateStore() {
        return discordWorkflowStateStore;
    }

    public StaffAlertService getStaffAlertService() {
        return staffAlertService;
    }

    public VpnCheckService getVpnCheckService() {
        return vpnCheckService;
    }

    public ServerRuleService getServerRuleService() {
        return serverRuleService;
    }

    public PunishmentTemplateService getPunishmentTemplateService() {
        return punishmentTemplateService;
    }

    public AuditLogService getAuditLogService() {
        return auditLogService;
    }

    public CaseExportService getCaseExportService() {
        return caseExportService;
    }

    public SupportDumpService getSupportDumpService() {
        return supportDumpService;
    }

    public SetupService getSetupService() {
        return setupService;
    }

    public FeedbackService getFeedbackService() {
        return feedbackService;
    }

    public JoinAlertExemptionService getJoinAlertExemptionService() {
        return joinAlertExemptionService;
    }

    public AltDetectionService getAltDetectionService() {
        return altDetectionService;
    }

    public MigrationService getMigrationService() {
        return migrationService;
    }

    public ReviewReminderService getReviewReminderService() {
        return reviewReminderService;
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

    private void synchronizeBundledConfigurations() {
        synchronizeBundledConfiguration("config.yml");
        synchronizeBundledConfiguration("lang-en.yml");
        synchronizeBundledConfiguration("lang-de.yml");
    }

    private void synchronizeBundledConfiguration(String path) {
        BundledYamlConfigSynchronizer.SyncResult syncResult =
                BundledYamlConfigSynchronizer.synchronize(this, path);
        String syncMessage = BundledYamlConfigSynchronizer.describe(path, syncResult);
        if (syncMessage != null) {
            LoggerUtil.info(syncMessage);
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
        bindCommand("report");

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
        getServer().getPluginManager().registerEvents(new QuarantineListener(this), this);
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

    private void validateVelocityBridgeStorage() {
        if (!getConfig().getBoolean("network.velocity-bridge.enabled", false)) {
            return;
        }

        StorageSettings settings = StorageFactory.readSettings(this);
        if (settings.type() != StorageType.MARIADB) {
            throw new IllegalStateException("network.velocity-bridge.enabled requires database.type=MARIADB for safe network synchronization.");
        }
    }
}
