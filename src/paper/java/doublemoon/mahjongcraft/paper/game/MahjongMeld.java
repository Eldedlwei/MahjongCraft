package doublemoon.mahjongcraft.paper.game;

import java.util.List;

public record MahjongMeld(MeldType type, List<MahjongTile> tiles, boolean open) {
    public MahjongTile baseTile() {
        return tiles.get(0).normalized();
    }
}

