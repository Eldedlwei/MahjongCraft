package doublemoon.mahjongcraft.paper.game;

import doublemoon.mahjongcraft.paper.integration.MahjongUtilsFacade;

import java.util.ArrayList;
import java.util.List;

public final class HandAnalyzer {
    private HandAnalyzer() {
    }

    public static boolean isWinning(List<MahjongTile> tiles, List<MahjongMeld> melds) {
        Boolean result = MahjongUtilsFacade.get().isWinning(tiles, melds);
        return result != null && result;
    }

    public static WinPattern analyzeWinning(List<MahjongTile> tiles, List<MahjongMeld> melds) {
        if (!isWinning(tiles, melds)) {
            return null;
        }
        return WinPattern.library();
    }

    public static boolean canRon(List<MahjongTile> hand, MahjongTile winningTile, List<MahjongMeld> melds) {
        if (hand == null || winningTile == null) {
            return false;
        }
        List<MahjongTile> full = new ArrayList<>(hand);
        full.add(winningTile);
        return isWinning(full, melds);
    }

    public static boolean isTenpai(List<MahjongTile> hand, List<MahjongMeld> melds) {
        Boolean result = MahjongUtilsFacade.get().isTenpai(hand, melds);
        return result != null && result;
    }

    public enum WinType {
        LIBRARY
    }

    public record WinPattern(WinType type) {
        public static WinPattern library() {
            return new WinPattern(WinType.LIBRARY);
        }
    }
}

