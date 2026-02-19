package doublemoon.mahjongcraft.paper.game;

public enum MeldType {
    CHII,
    PON,
    KAN_OPEN,
    KAN_CLOSED,
    KAN_ADDED;

    public boolean isKan() {
        return this == KAN_OPEN || this == KAN_CLOSED || this == KAN_ADDED;
    }
}
