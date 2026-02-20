package doublemoon.mahjongcraft.paper.integration;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

public final class PacketEventsBootstrap {
    private PacketEventsBootstrap() {
    }

    public static boolean init(JavaPlugin plugin) {
        org.bukkit.plugin.Plugin packetEventsPlugin = Bukkit.getPluginManager().getPlugin("PacketEvents");
        if (packetEventsPlugin == null) {
            packetEventsPlugin = Bukkit.getPluginManager().getPlugin("packetevents");
        }
        if (packetEventsPlugin == null) {
            plugin.getLogger().warning("PacketEvents plugin not found. Packet-level features disabled.");
            return false;
        }
        try {
            ClassLoader loader = packetEventsPlugin.getClass().getClassLoader();
            Class<?> packetEventsClass = loadClass(loader,
                    "io.github.retrooper.packetevents.PacketEvents",
                    "com.github.retrooper.packetevents.PacketEvents"
            );
            Method getApi = packetEventsClass.getMethod("getAPI");
            Object packetApi = getApi.invoke(null);
            if (packetApi == null) {
                plugin.getLogger().warning("PacketEvents API not initialized yet. Features disabled.");
                return false;
            }
            plugin.getLogger().info("PacketEvents detected.");
            return true;
        } catch (Throwable throwable) {
            plugin.getLogger().warning("PacketEvents init failed: " + throwable.getClass().getSimpleName() + " " + throwable.getMessage());
            return false;
        }
    }

    public static void shutdown(JavaPlugin plugin) {
        // PacketEvents lifecycle is owned by the PacketEvents plugin when not bundled.
    }

    private static Class<?> loadClass(ClassLoader loader, String primary, String fallback) throws ClassNotFoundException {
        try {
            return Class.forName(primary, false, loader);
        } catch (ClassNotFoundException ignored) {
            return Class.forName(fallback, false, loader);
        }
    }
}
