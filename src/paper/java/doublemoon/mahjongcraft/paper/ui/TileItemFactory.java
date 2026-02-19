package doublemoon.mahjongcraft.paper.ui;

import doublemoon.mahjongcraft.paper.game.MahjongTile;
import doublemoon.mahjongcraft.paper.game.TileVisuals;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class TileItemFactory {
    private TileItemFactory() {
    }

    public static ItemStack create(MahjongTile tile, int handIndex) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.setDisplayName(handIndex + ". " + tile.shortName());
        TileVisuals.applyTileModel(meta, tile);
        stack.setItemMeta(meta);
        return stack;
    }
}
