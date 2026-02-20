package doublemoon.mahjongcraft.paper.ui;

import doublemoon.mahjongcraft.paper.MahjongCraftPaperPlugin;
import doublemoon.mahjongcraft.paper.game.MahjongPlayerState;
import doublemoon.mahjongcraft.paper.game.MahjongTable;
import doublemoon.mahjongcraft.paper.game.MahjongTile;
import doublemoon.mahjongcraft.paper.message.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HandView {
    public static final String TITLE = "Mahjong Hand";
    private static final Map<UUID, String> OPENED_TABLE = new ConcurrentHashMap<>();

    private HandView() {
    }

    public static void open(MahjongCraftPaperPlugin plugin, Player player, MahjongTable table) {
        MahjongPlayerState state = table.players().get(player.getUniqueId());
        if (state == null) {
            MessageUtil.send(player, "cmd.err_not_in_table");
            return;
        }

        List<MahjongTile> hand = state.hand();
        int rows = Math.min(6, Math.max(1, (int) Math.ceil(hand.size() / 9.0)));
        Inventory inventory = Bukkit.createInventory(player, rows * 9, TITLE);
        for (int i = 0; i < hand.size() && i < inventory.getSize(); i++) {
            inventory.setItem(i, TileItemFactory.create(hand.get(i), i + 1));
        }

        OPENED_TABLE.put(player.getUniqueId(), table.id());
        // Use Paper's entity scheduler (Folia-safe) for player inventory actions.
        player.getScheduler().run(plugin, task -> player.openInventory(inventory), null);
    }

    public static String getOpenedTable(UUID uuid) {
        return OPENED_TABLE.get(uuid);
    }

    public static void close(UUID uuid) {
        OPENED_TABLE.remove(uuid);
    }
}
