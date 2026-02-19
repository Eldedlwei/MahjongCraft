package doublemoon.mahjongcraft.paper.game;

import java.util.ArrayList;
import java.util.List;

public final class HandValue {
    private final int han;
    private final int fu;
    private final boolean yakuman;
    private final List<String> yaku;
    private final int ronDealer;
    private final int ronChild;
    private final int tsumoDealerEach;
    private final int tsumoChildDealerPay;
    private final int tsumoChildOtherPay;

    public HandValue(
            int han,
            int fu,
            boolean yakuman,
            List<String> yaku,
            int ronDealer,
            int ronChild,
            int tsumoDealerEach,
            int tsumoChildDealerPay,
            int tsumoChildOtherPay
    ) {
        this.han = han;
        this.fu = fu;
        this.yakuman = yakuman;
        this.yaku = new ArrayList<>(yaku);
        this.ronDealer = ronDealer;
        this.ronChild = ronChild;
        this.tsumoDealerEach = tsumoDealerEach;
        this.tsumoChildDealerPay = tsumoChildDealerPay;
        this.tsumoChildOtherPay = tsumoChildOtherPay;
    }

    public int han() {
        return han;
    }

    public int fu() {
        return fu;
    }

    public boolean yakuman() {
        return yakuman;
    }

    public List<String> yaku() {
        return yaku;
    }

    public int ronDealer() {
        return ronDealer;
    }

    public int ronChild() {
        return ronChild;
    }

    public int tsumoDealerEach() {
        return tsumoDealerEach;
    }

    public int tsumoChildDealerPay() {
        return tsumoChildDealerPay;
    }

    public int tsumoChildOtherPay() {
        return tsumoChildOtherPay;
    }
}

