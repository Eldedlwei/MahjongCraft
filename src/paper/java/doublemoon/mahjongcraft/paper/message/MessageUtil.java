package doublemoon.mahjongcraft.paper.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

public final class MessageUtil {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final Map<String, Properties> BUNDLES = new HashMap<>();
    private static final String DEFAULT_LOCALE = "en_us";

    static {
        loadBundle("en_us");
        loadBundle("zh_cn");
    }

    private MessageUtil() {
    }

    public static void send(CommandSender sender, String key) {
        sender.sendMessage(component(sender, key));
    }

    public static void send(CommandSender sender, String key, Map<String, String> args) {
        sender.sendMessage(component(sender, key, args));
    }

    public static void sendRaw(CommandSender sender, String raw) {
        String template = tr(sender, "msg.raw");
        template = template.replace("{raw}", escape(raw));
        sender.sendMessage(MINI.deserialize(template));
    }

    public static Component component(CommandSender sender, String key) {
        return component(sender, key, Map.of());
    }

    public static Component component(CommandSender sender, String key, Map<String, String> args) {
        String template = tr(sender, key);
        for (Map.Entry<String, String> entry : args.entrySet()) {
            template = template.replace("{" + entry.getKey() + "}", escape(entry.getValue()));
        }
        return MINI.deserialize(template);
    }

    public static Map<String, String> args(String... keyValues) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private static String tr(CommandSender sender, String key) {
        String locale = localeOf(sender);
        Properties bundle = BUNDLES.getOrDefault(locale, BUNDLES.get(DEFAULT_LOCALE));
        if (bundle == null) {
            return "<gray>[Mahjong]</gray> " + key;
        }
        String value = bundle.getProperty(key);
        if (value != null) {
            return value;
        }
        Properties fallback = BUNDLES.get(DEFAULT_LOCALE);
        if (fallback == null) {
            return "<gray>[Mahjong]</gray> " + key;
        }
        return fallback.getProperty(key, "<gray>[Mahjong]</gray> " + key);
    }

    private static String localeOf(CommandSender sender) {
        if (sender instanceof Player player) {
            String raw = player.locale();
            if (raw != null && !raw.isBlank()) {
                return raw.toLowerCase(Locale.ROOT);
            }
        }
        return DEFAULT_LOCALE;
    }

    private static String escape(String text) {
        return MINI.escapeTags(text);
    }

    private static void loadBundle(String locale) {
        String path = "lang/messages_" + locale + ".properties";
        try (InputStream stream = MessageUtil.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                return;
            }
            Properties props = new Properties();
            props.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            BUNDLES.put(locale, props);
        } catch (Exception ignored) {
            // ignore broken locale files
        }
    }
}

