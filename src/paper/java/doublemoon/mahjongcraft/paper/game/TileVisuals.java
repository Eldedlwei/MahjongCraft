package doublemoon.mahjongcraft.paper.game;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class TileVisuals {
    private TileVisuals() {
    }

    public static ItemStack createTileItem(MahjongTile tile, String namePrefix) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.setDisplayName(namePrefix + tile.shortName());
        applyTileModel(meta, tile);
        stack.setItemMeta(meta);
        return stack;
    }

    public static void applyTileModel(ItemMeta meta, MahjongTile tile) {
        float cmd = 1000f + tile.ordinal();
        var modelData = meta.getCustomModelDataComponent();
        modelData.setFloats(List.of(cmd));
        meta.setCustomModelDataComponent(modelData);
        meta.setCustomModelData(1000 + tile.ordinal());
    }
}
