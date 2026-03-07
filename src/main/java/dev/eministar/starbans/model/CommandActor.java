package dev.eministar.starbans.model;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public record CommandActor(UUID uniqueId, String name, boolean console) {

    public static CommandActor fromSender(CommandSender sender) {
        if (sender instanceof Player player) {
            return new CommandActor(player.getUniqueId(), player.getName(), false);
        }
        return new CommandActor(null, sender.getName(), true);
    }

    public static CommandActor system() {
        return new CommandActor(null, "SYSTEM", true);
    }
}
