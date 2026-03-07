package dev.eministar.starbans.utils;

import dev.eministar.starbans.StarBans;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public final class SoundUtil {

    private SoundUtil() {
    }

    public static void play(StarBans plugin, Player player, String path) {
        if (plugin == null || player == null || path == null || path.isBlank()) {
            return;
        }

        String basePath = "sounds." + path;
        if (!plugin.getConfig().getBoolean(basePath + ".enabled", true)) {
            return;
        }

        String soundName = plugin.getConfig().getString(basePath + ".sound", "");
        if (soundName == null || soundName.isBlank()) {
            return;
        }

        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            float volume = (float) plugin.getConfig().getDouble(basePath + ".volume", 1.0D);
            float pitch = (float) plugin.getConfig().getDouble(basePath + ".pitch", 1.0D);
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException exception) {
            LoggerUtil.warn("Invalid sound configured at '" + basePath + "': " + soundName);
        }
    }
}
