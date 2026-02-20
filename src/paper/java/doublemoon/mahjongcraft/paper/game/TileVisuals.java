package doublemoon.mahjongcraft.paper.game;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;

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
        NamespacedKey key = new NamespacedKey("mahjongcraft", "tile/" + tile.textureKey());
        try {
            Method m = meta.getClass().getMethod("setItemModel", NamespacedKey.class);
            m.invoke(meta, key);
        } catch (ReflectiveOperationException ignored) {
            // Fallback for API variants.
        }
        // Always set custom model data as a legacy fallback so vanilla clients still pick up overrides.
        meta.setCustomModelData(1000 + tile.ordinal());
    }
}
