package doublemoon.mahjongcraft.paper.game;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MahjongPlayerState {
    private final UUID uuid;
    private final List<MahjongTile> hand = new ArrayList<>();
    private final List<MahjongTile> discards = new ArrayList<>();
    private final List<MahjongMeld> melds = new ArrayList<>();
    private int points = 25000;
    private boolean riichi = false;
    private boolean ippatsuEligible = false;
    private boolean temporaryFuriten = false;
    private boolean riichiFuriten = false;
    private boolean lastDrawRinshan = false;
    private int lastDiscardGlobalIndex = -1;
    private int riichiDeclarationGlobalIndex = -1;

    public MahjongPlayerState(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID uuid() {
        return uuid;
    }

    public List<MahjongTile> hand() {
        return hand;
    }

    public List<MahjongTile> discards() {
        return discards;
    }

    public int points() {
        return points;
    }

    public void points(int points) {
        this.points = points;
    }

    public List<MahjongMeld> melds() {
        return melds;
    }

    public boolean riichi() {
        return riichi;
    }

    public void riichi(boolean riichi) {
        this.riichi = riichi;
    }

    public boolean ippatsuEligible() {
        return ippatsuEligible;
    }

    public void ippatsuEligible(boolean ippatsuEligible) {
        this.ippatsuEligible = ippatsuEligible;
    }

    public boolean temporaryFuriten() {
        return temporaryFuriten;
    }

    public void temporaryFuriten(boolean temporaryFuriten) {
        this.temporaryFuriten = temporaryFuriten;
    }

    public boolean riichiFuriten() {
        return riichiFuriten;
    }

    public void riichiFuriten(boolean riichiFuriten) {
        this.riichiFuriten = riichiFuriten;
    }

    public boolean lastDrawRinshan() {
        return lastDrawRinshan;
    }

    public void lastDrawRinshan(boolean lastDrawRinshan) {
        this.lastDrawRinshan = lastDrawRinshan;
    }

    public int lastDiscardGlobalIndex() {
        return lastDiscardGlobalIndex;
    }

    public void lastDiscardGlobalIndex(int lastDiscardGlobalIndex) {
        this.lastDiscardGlobalIndex = lastDiscardGlobalIndex;
    }

    public int riichiDeclarationGlobalIndex() {
        return riichiDeclarationGlobalIndex;
    }

    public void riichiDeclarationGlobalIndex(int riichiDeclarationGlobalIndex) {
        this.riichiDeclarationGlobalIndex = riichiDeclarationGlobalIndex;
    }

    public boolean closedHand() {
        return melds.stream().noneMatch(MahjongMeld::open);
    }
}
