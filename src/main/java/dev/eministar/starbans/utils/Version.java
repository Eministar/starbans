package dev.eministar.starbans.utils;

import org.bukkit.plugin.java.JavaPlugin;

public final class Version {

    private static String version = "1.0.0";

    private Version() {
    }

    public static void init(JavaPlugin plugin) {
        version = plugin.getDescription().getVersion();
    }

    public static String get() {
        return version;
    }
}
