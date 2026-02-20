package doublemoon.mahjongcraft.paper.listener;

import doublemoon.mahjongcraft.paper.MahjongCraftPaperPlugin;
import doublemoon.mahjongcraft.paper.game.MahjongTable;
import doublemoon.mahjongcraft.paper.game.MahjongTableManager;
import doublemoon.mahjongcraft.paper.message.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public final class TableClickStartListener implements Listener {
    private static final double CLICK_RADIUS_SQ = 3.0 * 3.0;

    private final MahjongCraftPaperPlugin plugin;
    private final MahjongTableManager manager;

    public TableClickStartListener(MahjongCraftPaperPlugin plugin, MahjongTableManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onRightClickBlock(PlayerInteractEvent event) {
        switch (event.getAction()) {
            case RIGHT_CLICK_BLOCK -> {
                if (event.getClickedBlock() == null) {
                    return;
                }
                Player player = event.getPlayer();
                Location click = event.getClickedBlock().getLocation().add(0.5, 0.5, 0.5);
                MahjongTable table = manager.findNearestTable(click, CLICK_RADIUS_SQ);
                if (table == null || table.center().getWorld() == null) {
                    return;
                }
                handleClick(player, table);
                event.setCancelled(true);
            }
            default -> {
                return;
            }
        }
    }

    private void handleClick(Player player, MahjongTable table) {
        MahjongTable current = manager.getTableByPlayer(player.getUniqueId());
        if (current == null) {
            if (!table.started()) {
                if (manager.join(player, table.id())) {
                    table.broadcastKey("table.player_joined", MessageUtil.args("player", player.getName()));
                    table.broadcast(table.status());
                    plugin.entityGuiManager().openFor(player, table);
                    plugin.entityGuiManager().refreshTable(table.id());
                } else {
                    MessageUtil.send(player, "cmd.err_join_failed");
                }
                return;
            }
            MessageUtil.send(player, "cmd.err_join_failed");
            return;
        }
        if (!current.id().equalsIgnoreCase(table.id())) {
            MessageUtil.send(player, "cmd.err_already_in_table");
            return;
        }
        if (table.started()) {
            plugin.entityGuiManager().openFor(player, table);
            return;
        }
        if (!table.host().equals(player.getUniqueId())) {
            MessageUtil.send(player, "cmd.err_only_host");
            return;
        }

        String result = table.start();
        table.broadcast(result);
        table.players().keySet().forEach(uuid -> {
            Player member = Bukkit.getPlayer(uuid);
            if (member != null && member.isOnline()) {
                plugin.entityGuiManager().openFor(member, table);
            }
        });
        runBotsAndSync(table);
    }

    private void runBotsAndSync(MahjongTable table) {
        if (!table.started()) {
            return;
        }
        String botResult = table.runBots();
        if (!botResult.isBlank()) {
            table.broadcast(botResult);
        }
        table.broadcast(table.status());
        plugin.entityGuiManager().refreshTable(table.id());
    }
}
