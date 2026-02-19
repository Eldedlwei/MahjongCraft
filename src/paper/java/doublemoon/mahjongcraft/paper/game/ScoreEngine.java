package doublemoon.mahjongcraft.paper.game;

import doublemoon.mahjongcraft.paper.integration.MahjongUtilsFacade;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ScoreEngine {
    private ScoreEngine() {
    }

    public static HandValue evaluateHand(
            MahjongPlayerState winner,
            List<MahjongTile> winningTiles,
            MahjongTile agariTile,
            boolean tsumo,
            Wind seatWind,
            Wind roundWind
    ) {
        return evaluateHand(winner, winningTiles, agariTile, tsumo, seatWind, roundWind, WinContext.none());
    }

    public static HandValue evaluateHand(
            MahjongPlayerState winner,
            List<MahjongTile> winningTiles,
            MahjongTile agariTile,
            boolean tsumo,
            Wind seatWind,
            Wind roundWind,
            WinContext context
    ) {
        MahjongUtilsFacade.HoraResult hora = MahjongUtilsFacade.get().hora(
                winningTiles,
                winner.melds(),
                agariTile,
                tsumo,
                seatWind,
                roundWind
        );
        if (hora == null) {
            return new HandValue(0, 0, false, List.of("INVALID_HORA"), 0, 0, 0, 0, 0);
        }
        HandValue base = new HandValue(
                hora.han(),
                hora.fu(),
                hora.yakuman(),
                hora.yaku(),
                hora.ronDealer(),
                hora.ronChild(),
                hora.tsumoDealerEach(),
                hora.tsumoChildDealerPay(),
                hora.tsumoChildOtherPay()
        );
        return applyContext(base, context);
    }

    public static Settlement settleRon(
            UUID winner,
            UUID loser,
            HandValue value,
            boolean dealerWin,
            int honba,
            int riichiPot
    ) {
        Map<UUID, Integer> delta = new HashMap<>();
        int baseRon = dealerWin ? value.ronDealer() : value.ronChild();
        int transfer = baseRon + honba * 300;
        delta.put(winner, transfer + riichiPot);
        delta.put(loser, -transfer);
        String summary = "Ron " + value.han() + " han " + value.fu() + " fu (" + String.join(", ", value.yaku()) + ")";
        return new Settlement(summary, value, delta, dealerWin, false);
    }

    public static Settlement settleTsumo(
            UUID winner,
            List<UUID> players,
            HandValue value,
            UUID dealer,
            int honba,
            int riichiPot
    ) {
        Map<UUID, Integer> delta = new HashMap<>();
        boolean dealerWin = winner.equals(dealer);
        int gain = riichiPot;
        if (dealerWin) {
            int eachPay = value.tsumoDealerEach() + honba * 100;
            for (UUID p : players) {
                if (p.equals(winner)) {
                    continue;
                }
                delta.put(p, delta.getOrDefault(p, 0) - eachPay);
                gain += eachPay;
            }
        } else {
            int dealerPay = value.tsumoChildDealerPay() + honba * 100;
            int otherPay = value.tsumoChildOtherPay() + honba * 100;
            for (UUID p : players) {
                if (p.equals(winner)) {
                    continue;
                }
                int pay = p.equals(dealer) ? dealerPay : otherPay;
                delta.put(p, delta.getOrDefault(p, 0) - pay);
                gain += pay;
            }
        }
        delta.put(winner, delta.getOrDefault(winner, 0) + gain);
        String summary = "Tsumo " + value.han() + " han " + value.fu() + " fu (" + String.join(", ", value.yaku()) + ")";
        return new Settlement(summary, value, delta, dealerWin, false);
    }

    public static Settlement settleExhaustiveDraw(List<UUID> players, Set<UUID> tenpaiPlayers, UUID dealer) {
        Map<UUID, Integer> delta = new HashMap<>();
        int tenpaiCount = tenpaiPlayers.size();
        int notenCount = players.size() - tenpaiCount;
        if (tenpaiCount > 0 && tenpaiCount < players.size()) {
            int gainEach = 3000 / tenpaiCount;
            int payEach = 3000 / notenCount;
            for (UUID player : players) {
                if (tenpaiPlayers.contains(player)) {
                    delta.put(player, delta.getOrDefault(player, 0) + gainEach);
                } else {
                    delta.put(player, delta.getOrDefault(player, 0) - payEach);
                }
            }
        }
        boolean dealerContinues = tenpaiPlayers.contains(dealer);
        String summary = "Exhaustive draw: tenpai " + tenpaiCount + ", noten " + notenCount;
        return new Settlement(summary, new HandValue(0, 0, false, List.of(), 0, 0, 0, 0, 0), delta, dealerContinues, true);
    }

    public static Settlement settleAbortiveDraw(String reason) {
        return new Settlement(reason, new HandValue(0, 0, false, List.of(), 0, 0, 0, 0, 0), Map.of(), true, true);
    }

    private static HandValue applyContext(HandValue base, WinContext context) {
        if (base.yakuman() || context == null) {
            return base;
        }
        int bonusHan = 0;
        Set<String> yakuNames = new LinkedHashSet<>(base.yaku());
        if (context.ippatsu()) {
            bonusHan++;
            yakuNames.add("Ippatsu");
        }
        if (context.haitei()) {
            bonusHan++;
            yakuNames.add("Haitei");
        }
        if (context.houtei()) {
            bonusHan++;
            yakuNames.add("Houtei");
        }
        if (context.rinshan()) {
            bonusHan++;
            yakuNames.add("Rinshan");
        }
        if (context.chankan()) {
            bonusHan++;
            yakuNames.add("Chankan");
        }
        if (bonusHan == 0) {
            return base;
        }
        int adjustedHan = base.han() + bonusHan;
        Points p = calculatePoints(adjustedHan, base.fu());
        return new HandValue(
                adjustedHan,
                base.fu(),
                false,
                List.copyOf(yakuNames),
                p.ronDealer,
                p.ronChild,
                p.tsumoDealerEach,
                p.tsumoChildDealerPay,
                p.tsumoChildOtherPay
        );
    }

    private static Points calculatePoints(int han, int fu) {
        int basePoints = calculateBasePoints(han, fu);
        return new Points(
                roundUp100(basePoints * 6),
                roundUp100(basePoints * 4),
                roundUp100(basePoints * 2),
                roundUp100(basePoints * 2),
                roundUp100(basePoints)
        );
    }

    private static int calculateBasePoints(int han, int fu) {
        if (han >= 13) {
            return 8000;
        }
        if (han >= 11) {
            return 6000;
        }
        if (han >= 8) {
            return 4000;
        }
        if (han >= 6) {
            return 3000;
        }
        if (han == 5 || (han == 4 && fu >= 40) || (han == 3 && fu >= 70)) {
            return 2000;
        }
        int raw = fu * (1 << (han + 2));
        return Math.min(raw, 2000);
    }

    private static int roundUp100(int value) {
        return ((value + 99) / 100) * 100;
    }

    private static final class Points {
        private final int ronDealer;
        private final int ronChild;
        private final int tsumoDealerEach;
        private final int tsumoChildDealerPay;
        private final int tsumoChildOtherPay;

        private Points(int ronDealer, int ronChild, int tsumoDealerEach, int tsumoChildDealerPay, int tsumoChildOtherPay) {
            this.ronDealer = ronDealer;
            this.ronChild = ronChild;
            this.tsumoDealerEach = tsumoDealerEach;
            this.tsumoChildDealerPay = tsumoChildDealerPay;
            this.tsumoChildOtherPay = tsumoChildOtherPay;
        }
    }
}
