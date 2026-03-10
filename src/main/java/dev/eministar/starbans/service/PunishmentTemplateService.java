package dev.eministar.starbans.service;

import dev.eministar.starbans.StarBans;
import dev.eministar.starbans.model.CaseType;
import dev.eministar.starbans.model.CaseVisibility;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class PunishmentTemplateService {

    private final StarBans plugin;

    public PunishmentTemplateService(StarBans plugin) {
        this.plugin = plugin;
    }

    public List<Template> getTemplates() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("templates.entries");
        if (section == null) {
            return List.of();
        }

        List<Template> templates = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            Template template = parseTemplate(key, section.getConfigurationSection(key));
            if (template != null && template.enabled()) {
                templates.add(template);
            }
        }
        return templates;
    }

    public Optional<Template> getTemplate(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }

        ConfigurationSection section = plugin.getConfig().getConfigurationSection("templates.entries." + key);
        Template template = parseTemplate(key, section);
        return template == null || !template.enabled() ? Optional.empty() : Optional.of(template);
    }

    public List<String> getTemplateKeys() {
        return getTemplates().stream()
                .map(Template::key)
                .toList();
    }

    private Template parseTemplate(String key, ConfigurationSection section) {
        if (section == null) {
            return null;
        }

        CaseType type;
        try {
            type = CaseType.valueOf(section.getString("type", "NOTE").trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }

        Long duration = null;
        String durationRaw = section.getString("duration");
        if (durationRaw != null && !durationRaw.isBlank()) {
            try {
                duration = TimeUtil.parseDuration(durationRaw);
            } catch (IllegalArgumentException ignored) {
                duration = null;
            }
        }

        return new Template(
                key,
                section.getBoolean("enabled", true),
                section.getString("display-name", key),
                type,
                section.getString("label"),
                section.getString("category"),
                section.getString("reason", "No reason specified."),
                duration,
                Math.max(0, section.getInt("points", 0)),
                CaseVisibility.fromConfig(section.getString("visibility", "INTERNAL")),
                section.getStringList("tags")
        );
    }

    public record Template(String key,
                           boolean enabled,
                           String displayName,
                           CaseType type,
                           String label,
                           String category,
                           String reason,
                           Long durationMillis,
                           int points,
                           CaseVisibility visibility,
                           List<String> tags) {
    }
}
