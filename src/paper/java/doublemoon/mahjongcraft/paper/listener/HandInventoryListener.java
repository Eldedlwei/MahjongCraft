package doublemoon.mahjongcraft.paper.listener;

import doublemoon.mahjongcraft.paper.MahjongCraftPaperPlugin;
import doublemoon.mahjongcraft.paper.game.MahjongTable;
import doublemoon.mahjongcraft.paper.game.MahjongTableManager;
import doublemoon.mahjongcraft.paper.ui.HandView;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

public final class HandInventoryListener implements Listener {
    private final MahjongCraftPaperPlugin plugin;
    private final MahjongTableManager manager;

    public HandInventoryListener(MahjongCraftPaperPlugin plugin, MahjongTableManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onHandClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!HandView.TITLE.equals(event.getView().getTitle())) {
            return;
        }
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) {
            return;
        }

        String tableId = HandView.getOpenedTable(player.getUniqueId());
        if (tableId == null) {
            return;
        }
        MahjongTable table = manager.getTableById(tableId);
        if (table == null) {
            player.closeInventory();
            return;
        }
        String result = table.discard(player.getUniqueId(), slot + 1);
        table.broadcast(result);
        if (table.started()) {
            table.broadcast(table.status());
            HandView.open(plugin, player, table);
        } else {
            player.closeInventory();
        }
    }

    @EventHandler
    public void onHandClose(InventoryCloseEvent event) {
        if (HandView.TITLE.equals(event.getView().getTitle())) {
            HandView.close(event.getPlayer().getUniqueId());
        }
    }
}

