package doublemoon.mahjongcraft.paper.game;

public enum Wind {
    EAST(27),
    SOUTH(28),
    WEST(29),
    NORTH(30);

    private final int tileSortOrder;

    Wind(int tileSortOrder) {
        this.tileSortOrder = tileSortOrder;
    }

    public int tileSortOrder() {
        return tileSortOrder;
    }

    public Wind next() {
        return values()[(ordinal() + 1) % 4];
    }
}

