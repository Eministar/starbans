package dev.eministar.starbans.gui;

import dev.eministar.starbans.StarBans;
import dev.eministar.starbans.utils.ColorUtil;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Arrays;
import java.util.List;

public final class GuiItemFactory {

    private GuiItemFactory() {
    }

    public static ItemStack create(StarBans plugin, ConfigurationSection section, OfflinePlayer skullOwner, Object... replacements) {
        if (section == null) {
            return new ItemStack(Material.BARRIER);
        }

        Material material = Material.matchMaterial(section.getString("material", "BARRIER"));
        if (material == null) {
            material = Material.BARRIER;
        }

        ItemStack itemStack = new ItemStack(material, Math.max(1, section.getInt("amount", 1)));
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return itemStack;
        }

        String displayName = resolveName(plugin, section, replacements);
        if (displayName != null) {
            meta.setDisplayName(displayName);
        }
        List<String> lore = resolveLore(plugin, section, replacements);
        if (lore != null) {
            meta.setLore(lore);
        }
        if (section.contains("custom-model-data")) {
            meta.setCustomModelData(section.getInt("custom-model-data"));
        }
        if (section.getBoolean("glow", false)) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        List<String> flags = section.getStringList("flags");
        for (String rawFlag : flags) {
            try {
                meta.addItemFlags(ItemFlag.valueOf(rawFlag.toUpperCase()));
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (meta instanceof SkullMeta skullMeta && skullOwner != null) {
            skullMeta.setOwningPlayer(skullOwner);
        }

        itemStack.setItemMeta(meta);
        return itemStack;
    }

    public static void fillEmpty(Inventory inventory, ItemStack filler) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack current = inventory.getItem(slot);
            if (current == null || current.getType() == Material.AIR) {
                inventory.setItem(slot, filler.clone());
            }
        }
    }

    public static List<Integer> toSlots(List<Integer> slots) {
        return Arrays.stream(slots.toArray(new Integer[0])).toList();
    }

    private static String resolveName(StarBans plugin, ConfigurationSection section, Object... replacements) {
        String langKey = section.getString("name-key", "");
        if (langKey.isBlank()) {
            langKey = derivedLangKey(section, "name");
        }
        if (!langKey.isBlank()) {
            String translated = plugin.getLang().getRaw(langKey, null);
            if (translated != null) {
                return plugin.getLang().get(langKey, replacements);
            }
        }
        if (section.contains("name")) {
            return ColorUtil.color(section.getString("name", ""), replacements);
        }
        return null;
    }

    private static List<String> resolveLore(StarBans plugin, ConfigurationSection section, Object... replacements) {
        String langKey = section.getString("lore-key", "");
        if (langKey.isBlank()) {
            langKey = derivedLangKey(section, "lore");
        }
        if (!langKey.isBlank()) {
            if (!plugin.getLang().getRawList(langKey).isEmpty() || plugin.getLang().getRaw(langKey, null) != null) {
                return plugin.getLang().getList(langKey, replacements);
            }
        }
        if (section.contains("lore")) {
            return ColorUtil.color(section.getStringList("lore"), replacements);
        }
        return null;
    }

    private static String derivedLangKey(ConfigurationSection section, String suffix) {
        String currentPath = section.getCurrentPath();
        if (currentPath == null || !currentPath.startsWith("gui.")) {
            return "";
        }
        return "gui-items." + currentPath.substring("gui.".length()) + "." + suffix;
    }
}
