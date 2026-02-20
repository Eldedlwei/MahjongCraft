package doublemoon.mahjongcraft.paper.integration;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

public final class PacketEventsBootstrap {
    private PacketEventsBootstrap() {
    }

    public static boolean init(JavaPlugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("packetevents") == null
                && Bukkit.getPluginManager().getPlugin("PacketEvents") == null) {
            plugin.getLogger().warning("PacketEvents plugin not found. Packet-level features disabled.");
            return false;
        }
        try {
            Class<?> builderClass = Class.forName("io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder");
            Method build = builderClass.getMethod("build", org.bukkit.plugin.Plugin.class);
            Object api = build.invoke(null, plugin);

            Class<?> packetEventsClass = Class.forName("io.github.retrooper.packetevents.PacketEvents");
            Method setApi = null;
            for (Method method : packetEventsClass.getMethods()) {
                if ("setAPI".equals(method.getName()) && method.getParameterCount() == 1) {
                    setApi = method;
                    break;
                }
            }
            if (setApi == null) {
                throw new NoSuchMethodException("PacketEvents.setAPI");
            }
            setApi.invoke(null, api);

            Method getApi = packetEventsClass.getMethod("getAPI");
            Object packetApi = getApi.invoke(null);
            Class<?> apiInterface = Class.forName("io.github.retrooper.packetevents.PacketEventsAPI");
            apiInterface.getMethod("load").invoke(packetApi);
            apiInterface.getMethod("init").invoke(packetApi);

            plugin.getLogger().info("PacketEvents initialized.");
            return true;
        } catch (Throwable throwable) {
            plugin.getLogger().warning("PacketEvents init failed: " + throwable.getClass().getSimpleName() + " " + throwable.getMessage());
            return false;
        }
    }

    public static void shutdown(JavaPlugin plugin) {
        try {
            Class<?> packetEventsClass = Class.forName("io.github.retrooper.packetevents.PacketEvents");
            Method getApi = packetEventsClass.getMethod("getAPI");
            Object packetApi = getApi.invoke(null);
            if (packetApi != null) {
                Class<?> apiInterface = Class.forName("io.github.retrooper.packetevents.PacketEventsAPI");
                apiInterface.getMethod("terminate").invoke(packetApi);
                plugin.getLogger().info("PacketEvents terminated.");
            }
        } catch (Throwable ignored) {
            // ignored on shutdown
        }
    }
}
