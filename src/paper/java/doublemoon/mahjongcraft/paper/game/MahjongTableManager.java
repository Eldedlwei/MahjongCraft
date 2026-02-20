package doublemoon.mahjongcraft.paper.game;

import doublemoon.mahjongcraft.paper.integration.MoneyGateway;
import doublemoon.mahjongcraft.paper.message.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Location;

import java.security.SecureRandom;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MahjongTableManager {
    private static final String TABLE_ID_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JavaPlugin plugin;
    private final MoneyGateway moneyGateway;
    private final TableRules rules;
    private final Map<String, MahjongTable> byId = new HashMap<>();
    private final Map<UUID, String> playerToTableId = new HashMap<>();

    public MahjongTableManager(JavaPlugin plugin, MoneyGateway moneyGateway, TableRules rules) {
        this.plugin = plugin;
        this.moneyGateway = moneyGateway;
        this.rules = rules;
    }

    public MahjongTable createTable(Player host) {
        ensureMainThread();
        if (getTableByPlayer(host.getUniqueId()) != null) {
            return null;
        }
        String id = nextTableId();
        double x = Math.floor(host.getLocation().getX()) + 0.5;
        double y = Math.floor(host.getLocation().getY());
        double z = Math.floor(host.getLocation().getZ()) + 0.5;
        Location center = new Location(host.getWorld(), x, y, z);
        MahjongTable table = new MahjongTable(plugin, id, host.getUniqueId(), center, moneyGateway, rules);
        byId.put(id, table);
        playerToTableId.put(host.getUniqueId(), id);
        return table;
    }

    public MahjongTable getTableById(String id) {
        if (id == null) {
            return null;
        }
        return byId.get(id.toUpperCase());
    }

    public MahjongTable getTableByPlayer(UUID uuid) {
        String id = playerToTableId.get(uuid);
        return id == null ? null : byId.get(id);
    }

    public List<MahjongTable> tables() {
        return List.copyOf(byId.values());
    }

    public MahjongTable findNearestTable(Location location, double maxDistanceSquared) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        MahjongTable nearest = null;
        double best = maxDistanceSquared;
        for (MahjongTable table : byId.values()) {
            Location center = table.center();
            if (center.getWorld() == null || !center.getWorld().equals(location.getWorld())) {
                continue;
            }
            double dist = center.distanceSquared(location);
            if (dist <= best) {
                best = dist;
                nearest = table;
            }
        }
        return nearest;
    }

    public boolean join(Player player, String id) {
        ensureMainThread();
        if (getTableByPlayer(player.getUniqueId()) != null) {
            return false;
        }
        MahjongTable table = getTableById(id);
        if (table == null) {
            return false;
        }
        boolean added = table.addPlayer(player.getUniqueId());
        if (added) {
            playerToTableId.put(player.getUniqueId(), table.id());
        }
        return added;
    }

    public boolean leave(UUID uuid) {
        ensureMainThread();
        MahjongTable table = getTableByPlayer(uuid);
        if (table == null) {
            return false;
        }
        if (table.started()) {
            disbandKey(table.id(), "table.player_left_game_disband", Map.of(
                    "player", nameOf(uuid)
            ));
            return true;
        }
        boolean removed = table.removePlayer(uuid);
        playerToTableId.remove(uuid);
        if (removed) {
            table.broadcastKey("table.player_left", MessageUtil.args("player", nameOf(uuid)));
            if (table.host().equals(uuid) || table.isEmpty()) {
                disbandKey(table.id(), "table.host_left_disband", Map.of(
                        "player", nameOf(uuid)
                ));
            }
        }
        return removed;
    }

    public void disband(String tableId, String reason) {
        ensureMainThread();
        MahjongTable table = byId.remove(tableId);
        if (table == null) {
            return;
        }
        Collection<UUID> uuids = table.players().keySet();
        uuids.forEach(playerToTableId::remove);
        table.broadcast(reason);
        table.destroy();
    }

    public void disbandKey(String tableId, String key, Map<String, String> args) {
        ensureMainThread();
        MahjongTable table = byId.remove(tableId);
        if (table == null) {
            return;
        }
        Collection<UUID> uuids = table.players().keySet();
        uuids.forEach(playerToTableId::remove);
        table.broadcastKey(key, args);
        table.destroy();
    }

    public void shutdown() {
        ensureMainThread();
        byId.values().forEach(table -> {
            table.broadcastKey("table.plugin_shutdown");
            table.destroy();
        });
        byId.clear();
        playerToTableId.clear();
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    private String nextTableId() {
        while (true) {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                int idx = RANDOM.nextInt(TABLE_ID_ALPHABET.length());
                sb.append(TABLE_ID_ALPHABET.charAt(idx));
            }
            String id = sb.toString();
            if (!byId.containsKey(id)) {
                return id;
            }
        }
    }

    private void ensureMainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("MahjongTableManager must run on main server thread");
        }
    }

    private String nameOf(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        return player != null ? player.getName() : uuid.toString();
    }
}
