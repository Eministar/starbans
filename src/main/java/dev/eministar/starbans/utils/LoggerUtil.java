package dev.eministar.starbans.utils;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class LoggerUtil {

    private static final DateTimeFormatter FILE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String PREFIX = "&8[&#FFB84D&lStarBans&8] &7";

    private static JavaPlugin plugin;

    private LoggerUtil() {
    }

    public static void init(JavaPlugin javaPlugin) {
        plugin = javaPlugin;
        quietThirdPartyStartupLogs();
    }

    public static void info(String message) {
        send("&7" + message);
    }

    public static void success(String message) {
        send("&a" + message);
    }

    public static void warn(String message) {
        send("&e" + message);
    }

    public static void error(String message) {
        send("&c" + message);
    }

    public static void error(String message, Throwable throwable) {
        send("&c" + message);

        if (plugin == null) {
            Bukkit.getLogger().log(Level.SEVERE, ChatColor.stripColor(ColorUtil.color(message)), throwable);
            return;
        }

        File logsFolder = new File(plugin.getDataFolder(), "logs");
        if (!logsFolder.exists() && !logsFolder.mkdirs()) {
            plugin.getLogger().log(Level.SEVERE, "The StarBans logs directory could not be created.", throwable);
            return;
        }

        File logFile = new File(logsFolder, "error-" + LocalDateTime.now().format(FILE_FORMAT) + ".log");
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile))) {
            writer.println("StarBans error log");
            writer.println("Time: " + LocalDateTime.now());
            writer.println("Message: " + message);
            writer.println();
            throwable.printStackTrace(writer);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "The StarBans error log could not be written.", exception);
        }
    }

    private static void send(String message) {
        ConsoleCommandSender console = Bukkit.getConsoleSender();
        console.sendMessage(ColorUtil.color(PREFIX + message));
    }

    public static void quietThirdPartyStartupLogs() {
        setLoggerThreshold("com.zaxxer.hikari", Level.SEVERE);
        setLoggerThreshold("com.zaxxer.hikari.HikariConfig", Level.SEVERE);
        setLoggerThreshold("com.zaxxer.hikari.HikariDataSource", Level.SEVERE);
        setLoggerThreshold("com.zaxxer.hikari.pool.HikariPool", Level.SEVERE);
        setLoggerThreshold("com.zaxxer.hikari.pool.PoolBase", Level.SEVERE);

        setLoggerThreshold("dev.eministar.starbans.lib.hikari", Level.SEVERE);
        setLoggerThreshold("dev.eministar.starbans.lib.hikari.HikariConfig", Level.SEVERE);
        setLoggerThreshold("dev.eministar.starbans.lib.hikari.HikariDataSource", Level.SEVERE);
        setLoggerThreshold("dev.eministar.starbans.lib.hikari.pool.HikariPool", Level.SEVERE);
        setLoggerThreshold("dev.eministar.starbans.lib.hikari.pool.PoolBase", Level.SEVERE);

        setLoggerThreshold("org.sqlite", Level.SEVERE);
        setLoggerThreshold("dev.eministar.starbans.lib.sqlite", Level.SEVERE);
    }

    private static void setLoggerThreshold(String name, Level level) {
        Logger logger = Logger.getLogger(name);
        logger.setLevel(level);
        logger.setFilter(record -> record == null || record.getLevel().intValue() >= level.intValue());
    }
}
