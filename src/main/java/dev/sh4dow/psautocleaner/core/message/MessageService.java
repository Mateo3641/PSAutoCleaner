package dev.sh4dow.psautocleaner.core.message;

import dev.sh4dow.psautocleaner.core.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service responsible for parsing and dispatching Adventure MiniMessage components.
 */
public class MessageService {

    private final ConfigManager configManager;
    private final MiniMessage miniMessage;

    public MessageService(ConfigManager configManager) {
        this.configManager = configManager;
        this.miniMessage = MiniMessage.miniMessage();
    }

    /**
     * Sends a localized message from messages.yml to a CommandSender.
     */
    public void send(CommandSender sender, String messagePath, Map<String, String> placeholders) {
        String raw = configManager.getRawMessage(messagePath, "");
        if (raw == null || raw.isBlank()) {
            return;
        }

        Component component = parse(raw, placeholders, true);
        sender.sendMessage(component);
    }

    /**
     * Sends a localized message without prefix.
     */
    public void sendWithoutPrefix(CommandSender sender, String messagePath, Map<String, String> placeholders) {
        String raw = configManager.getRawMessage(messagePath, "");
        if (raw == null || raw.isBlank()) {
            return;
        }

        Component component = parse(raw, placeholders, false);
        sender.sendMessage(component);
    }

    /**
     * Sends a raw MiniMessage string directly.
     */
    public void sendRaw(CommandSender sender, String rawMessage, Map<String, String> placeholders, boolean withPrefix) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return;
        }
        sender.sendMessage(parse(rawMessage, placeholders, withPrefix));
    }

    /**
     * Parses a raw message template with placeholders and optional prefix.
     */
    public Component parse(String rawMessage, Map<String, String> placeholders, boolean withPrefix) {
        String prefix = withPrefix ? configManager.getRawMessage("prefix", "") : "";
        String fullMessage = prefix + rawMessage;

        List<TagResolver> resolvers = new ArrayList<>();
        if (placeholders != null) {
            placeholders.forEach((key, val) -> resolvers.add(Placeholder.parsed(key, val == null ? "" : val)));
        }

        return miniMessage.deserialize(fullMessage, TagResolver.resolver(resolvers));
    }

    public MiniMessage getMiniMessage() {
        return miniMessage;
    }
}
