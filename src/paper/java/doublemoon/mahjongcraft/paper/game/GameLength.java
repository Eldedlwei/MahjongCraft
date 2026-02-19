package doublemoon.mahjongcraft.paper.game;

public enum GameLength {
    ONE_GAME(1, Wind.EAST, 3),
    EAST(4, Wind.SOUTH, 3),
    SOUTH(4, Wind.WEST, 3),
    TWO_WIND(8, Wind.WEST, 3);

    private final int rounds;
    private final Wind finalWind;
    private final int finalDealerSeat;

    GameLength(int rounds, Wind finalWind, int finalDealerSeat) {
        this.rounds = rounds;
        this.finalWind = finalWind;
        this.finalDealerSeat = finalDealerSeat;
    }

    public int rounds() {
        return rounds;
    }

    public Wind finalWind() {
        return finalWind;
    }

    public int finalDealerSeat() {
        return finalDealerSeat;
    }
}
