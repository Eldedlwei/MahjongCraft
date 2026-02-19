package doublemoon.mahjongcraft.paper.game;

public record TableRules(
        GameLength gameLength,
        int startingPoints,
        int minPointsToWin,
        int minimumHan
) {
    public static TableRules defaults() {
        return new TableRules(GameLength.TWO_WIND, 25000, 30000, 1);
    }
}
