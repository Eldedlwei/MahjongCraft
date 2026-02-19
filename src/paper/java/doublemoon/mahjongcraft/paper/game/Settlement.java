package doublemoon.mahjongcraft.paper.game;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class Settlement {
    private final String summary;
    private final HandValue handValue;
    private final Map<UUID, Integer> pointDelta;
    private final boolean dealerContinues;
    private final boolean draw;

    public Settlement(String summary, HandValue handValue, Map<UUID, Integer> pointDelta, boolean dealerContinues, boolean draw) {
        this.summary = summary;
        this.handValue = handValue;
        this.pointDelta = new LinkedHashMap<>(pointDelta);
        this.dealerContinues = dealerContinues;
        this.draw = draw;
    }

    public String summary() {
        return summary;
    }

    public HandValue handValue() {
        return handValue;
    }

    public Map<UUID, Integer> pointDelta() {
        return pointDelta;
    }

    public boolean dealerContinues() {
        return dealerContinues;
    }

    public boolean draw() {
        return draw;
    }
}

