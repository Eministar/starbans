package dev.eministar.starbans.utils;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

public final class Banner {

    private static final int INNER_WIDTH = 42;

    private Banner() {
    }

    public static void print(JavaPlugin plugin) {
        String storage = plugin.getConfig().getString("database.type", "SQLITE").toUpperCase(Locale.ROOT);
        String networkMode = plugin.getConfig().getBoolean("network.proxy-support.enabled", false)
                ? plugin.getConfig().getString("network.proxy-support.mode", "PROXY").toUpperCase(Locale.ROOT)
                : "STANDALONE";
        Bukkit.getConsoleSender().sendMessage("");
        Bukkit.getConsoleSender().sendMessage(border("╔", "╗"));
        Bukkit.getConsoleSender().sendMessage(line(center("&#FFB84D&lS T A R B A N S")));
        Bukkit.getConsoleSender().sendMessage(line(center("&#63E6FFThe Ultimate Moderation Experience")));
        Bukkit.getConsoleSender().sendMessage(line(center("&7v&f" + Version.get() + " &8• &7By &fEministar")));
        Bukkit.getConsoleSender().sendMessage(line(center("&7Storage: &f" + storage + " &8• &7Netzwerk: &f" + networkMode)));
        Bukkit.getConsoleSender().sendMessage(border("╚", "╝"));
        Bukkit.getConsoleSender().sendMessage("");
    }

    public static void printEnabled() {
        Bukkit.getConsoleSender().sendMessage(ColorUtil.color(
                "&8[&#FFB84D&lStarBans&8] &aAktiviert &8• &7System bereit."
        ));
    }

    private static String border(String left, String right) {
        return ColorUtil.color("&8" + left + "═".repeat(INNER_WIDTH + 2) + right);
    }

    private static String line(String content) {
        int visibleLength = visibleLength(content);
        String padded = content;
        if (visibleLength < INNER_WIDTH) {
            padded += " ".repeat(INNER_WIDTH - visibleLength);
        }
        return ColorUtil.color("&8║ ") + padded + ColorUtil.color("&8 ║");
    }

    private static String center(String input) {
        int visibleLength = visibleLength(input);
        int leftPadding = Math.max(0, (INNER_WIDTH - visibleLength) / 2);
        int rightPadding = Math.max(0, INNER_WIDTH - visibleLength - leftPadding);
        return " ".repeat(leftPadding) + ColorUtil.color(input) + " ".repeat(rightPadding);
    }

    private static int visibleLength(String input) {
        return ChatColor.stripColor(ColorUtil.color(input)).length();
    }
}
