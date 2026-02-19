package doublemoon.mahjongcraft.paper.game;

import doublemoon.mahjongcraft.paper.integration.MoneyGateway;
import doublemoon.mahjongcraft.paper.message.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class MahjongTable {
    private final String id;
    private final UUID host;
    private final Location center;
    private final MoneyGateway moneyGateway;
    private final LinkedHashMap<UUID, MahjongPlayerState> players = new LinkedHashMap<>();
    private final Map<UUID, MahjongPlayerState> playersView = Collections.unmodifiableMap(players);
    private final ArrayDeque<MahjongTile> wall = new ArrayDeque<>();
    private ItemDisplay centerDisplay;
    private final Map<Integer, List<ItemDisplay>> seatDiscardDisplays = new HashMap<>();
    private final Map<Integer, List<ItemDisplay>> seatMeldDisplays = new HashMap<>();
    private int lastDisplayStateHash = Integer.MIN_VALUE;

    private boolean started = false;
    private List<UUID> turnOrder = new ArrayList<>();
    private int turnIndex = 0;

    private int dealerSeat = 0;
    private Wind roundWind = Wind.EAST;
    private int spentRounds = 0;
    private int honba = 0;
    private int riichiPot = 0;
    private double moneyPot = 0.0;
    private final GameLength gameLength;
    private final int minPointsToWin;
    private final int minimumHan;
    private final int startingPoints;
    private final List<MahjongTile> discardHistory = new ArrayList<>();
    private final Set<Integer> calledDiscardIndices = new HashSet<>();

    private MahjongTile pendingDiscardTile;
    private UUID pendingDiscarder;
    private int pendingDiscardGlobalIndex = -1;
    private final Map<UUID, EnumSet<ClaimAction>> pendingOptions = new LinkedHashMap<>();
    private final Map<UUID, ClaimDeclaration> pendingDeclarations = new LinkedHashMap<>();
    private ClaimSource pendingClaimSource = ClaimSource.NONE;
    private PendingKakan pendingKakan;
    private UUID pendingAnkanOwner;

    public MahjongTable(String id, UUID host, Location center, MoneyGateway moneyGateway, TableRules rules) {
        this.id = id;
        this.host = host;
        this.center = center.clone();
        this.moneyGateway = moneyGateway;
        TableRules safeRules = rules == null ? TableRules.defaults() : rules;
        this.gameLength = safeRules.gameLength();
        this.startingPoints = safeRules.startingPoints();
        this.minPointsToWin = safeRules.minPointsToWin();
        this.minimumHan = safeRules.minimumHan();
        this.players.put(host, new MahjongPlayerState(host));
    }

    public String id() {
        return id;
    }

    public UUID host() {
        return host;
    }

    public boolean started() {
        return started;
    }

    public Map<UUID, MahjongPlayerState> players() {
        return playersView;
    }

    public boolean addPlayer(UUID uuid) {
        ensureMainThread();
        if (started || players.size() >= 4 || players.containsKey(uuid)) {
            return false;
        }
        players.put(uuid, new MahjongPlayerState(uuid));
        refreshDisplays();
        return true;
    }

    public boolean removePlayer(UUID uuid) {
        ensureMainThread();
        boolean removed = players.remove(uuid) != null;
        if (!removed) {
            return false;
        }
        pendingOptions.remove(uuid);
        pendingDeclarations.remove(uuid);
        refreshDisplays();
        return true;
    }

    public void destroy() {
        ensureMainThread();
        clearDisplays();
    }

    public String start() {
        ensureMainThread();
        if (started) {
            return "Game already started.";
        }
        if (players.size() != 4) {
            return "Need 4 players to start.";
        }
        turnOrder = new ArrayList<>(players.keySet());
        dealerSeat = 0;
        roundWind = Wind.EAST;
        spentRounds = 0;
        honba = 0;
        riichiPot = 0;
        for (MahjongPlayerState state : players.values()) {
            state.points(startingPoints);
        }
        return startNewHand("Round started.");
    }

    public UUID currentTurnPlayer() {
        if (turnOrder.isEmpty()) {
            return null;
        }
        return turnOrder.get(turnIndex % turnOrder.size());
    }

    public String discard(UUID uuid, int handIndex) {
        ensureMainThread();
        if (!started) {
            return "Game not started.";
        }
        if (hasPendingClaims()) {
            return "Waiting claim responses on last discard.";
        }
        if (!uuid.equals(currentTurnPlayer())) {
            return "Not your turn.";
        }
        MahjongPlayerState state = players.get(uuid);
        if (state == null) {
            return "You are not in this table.";
        }
        if (handIndex < 1 || handIndex > state.hand().size()) {
            return "Hand index out of range.";
        }
        if (state.hand().size() % 3 != 2) {
            return "You must draw before discarding.";
        }

        MahjongTile tile = state.hand().remove(handIndex - 1);
        state.discards().add(tile);
        discardHistory.add(tile);
        pendingDiscardGlobalIndex = discardHistory.size() - 1;
        state.lastDiscardGlobalIndex(pendingDiscardGlobalIndex);
        if (state.riichi() && state.riichiDeclarationGlobalIndex() < 0) {
            state.riichiDeclarationGlobalIndex(pendingDiscardGlobalIndex);
        }
        state.hand().sort(MahjongTile.SORTER);
        state.ippatsuEligible(false);
        state.lastDrawRinshan(false);

        if (isSuufonRenda()) {
            return endByAbortiveDraw("Abortive draw: suufon renda.");
        }
        if (isSuuchaRiichi()) {
            return endByAbortiveDraw("Abortive draw: suucha riichi.");
        }

        openClaimWindow(uuid, tile);
        refreshDisplays();
        if (hasPendingClaims()) {
            return "Discarded " + tile.shortName() + ". Available claims: ron > pon/kan > chii.";
        }

        if (!advanceTurnAndDraw()) {
            return endByDraw("Wall exhausted.");
        }
        refreshDisplays();
        return "Discarded " + tile.shortName() + ". Next player drew.";
    }

    public String riichi(UUID uuid) {
        ensureMainThread();
        if (!started || hasPendingClaims()) {
            return "Cannot riichi now.";
        }
        MahjongPlayerState state = players.get(uuid);
        if (state == null || !uuid.equals(currentTurnPlayer())) {
            return "Not your turn.";
        }
        if (!state.closedHand()) {
            return "Riichi requires closed hand.";
        }
        if (state.riichi()) {
            return "Already in riichi.";
        }
        if (state.points() < 1000) {
            return "Not enough points for riichi.";
        }
        if (!HandAnalyzer.isTenpai(state.hand(), state.melds())) {
            return "Hand is not tenpai.";
        }
        state.points(state.points() - 1000);
        state.riichi(true);
        state.ippatsuEligible(true);
        riichiPot += 1000;
        return "Riichi declared.";
    }

    public String tsumo(UUID uuid) {
        ensureMainThread();
        if (!started) {
            return "Game not started.";
        }
        if (hasPendingClaims()) {
            return "Resolve claims first.";
        }
        if (!uuid.equals(currentTurnPlayer())) {
            return "Not your turn.";
        }
        MahjongPlayerState state = players.get(uuid);
        if (state == null) {
            return "You are not in this table.";
        }
        if (state.hand().size() % 3 != 2) {
            return "You cannot tsumo now.";
        }

        HandAnalyzer.WinPattern pattern = HandAnalyzer.analyzeWinning(state.hand(), state.melds());
        if (pattern == null) {
            return "Hand is not winning.";
        }
        MahjongTile agariTile = state.hand().isEmpty() ? null : state.hand().get(state.hand().size() - 1);
        if (agariTile == null) {
            return "Hand is not winning.";
        }
        HandValue value = ScoreEngine.evaluateHand(
                state,
                state.hand(),
                agariTile,
                true,
                seatWindOf(uuid),
                roundWind,
                buildTsumoContext(state)
        );
        if (!canWinByRule(value)) {
            return "Hand does not meet minimum han rule.";
        }
        Settlement settlement = ScoreEngine.settleTsumo(uuid, turnOrder, value, dealerUuid(), honba, riichiPot);
        applySettlement(settlement, List.of(uuid));
        payoutMoneyPot(List.of(uuid));
        refreshDisplays();
        return settlement.summary() + " " + pointsSnapshot();
    }

    public String ron(UUID uuid) {
        return declare(uuid, ClaimAction.RON, List.of());
    }

    public String pon(UUID uuid) {
        return declare(uuid, ClaimAction.PON, List.of());
    }

    public String kan(UUID uuid) {
        return kan(uuid, null);
    }

    public String kan(UUID uuid, String tileName) {
        ensureMainThread();
        MahjongTile wanted = tileName == null ? null : MahjongTile.parse(tileName);
        if (tileName != null && wanted == null) {
            return "Invalid tile name.";
        }
        if (hasPendingClaims()) {
            return declare(uuid, ClaimAction.KAN, List.of());
        }
        return selfKan(uuid, wanted);
    }

    public String chii(UUID uuid, String first, String second) {
        MahjongTile t1 = MahjongTile.parse(first);
        MahjongTile t2 = MahjongTile.parse(second);
        if (t1 == null || t2 == null) {
            return "Invalid tile names for chii.";
        }
        return declare(uuid, ClaimAction.CHII, List.of(t1, t2));
    }

    public String pass(UUID uuid) {
        return declare(uuid, ClaimAction.PASS, List.of());
    }

    public String kyuushuKyuuhai(UUID uuid) {
        ensureMainThread();
        if (!started) {
            return "Game not started.";
        }
        if (!uuid.equals(currentTurnPlayer())) {
            return "Not your turn.";
        }
        MahjongPlayerState state = players.get(uuid);
        if (state == null) {
            return "You are not in this table.";
        }
        if (!canDeclareKyuushuKyuuhai(state)) {
            return "Kyuushu kyuuhai is not available now.";
        }
        return endByAbortiveDraw("Abortive draw: kyuushu kyuuhai.");
    }

    public String handView(UUID uuid) {
        MahjongPlayerState state = players.get(uuid);
        if (state == null) {
            return "You are not in this table.";
        }
        if (!started) {
            return "Game not started.";
        }
        List<MahjongTile> hand = state.hand();
        StringBuilder sb = new StringBuilder();
        sb.append("Hand(").append(hand.size()).append("): ");
        for (int i = 0; i < hand.size(); i++) {
            sb.append(i + 1).append(".").append(hand.get(i).shortName());
            if (i < hand.size() - 1) {
                sb.append(" ");
            }
        }
        return sb.toString();
    }

    public String pay(UUID uuid, double amount) {
        ensureMainThread();
        if (amount <= 0) {
            return "Amount must be > 0.";
        }
        if (!players.containsKey(uuid)) {
            return "You are not in this table.";
        }
        if (!moneyGateway.enabled()) {
            return "Economy is not available.";
        }
        if (!moneyGateway.has(uuid, amount)) {
            return "Insufficient funds.";
        }
        if (!moneyGateway.withdraw(uuid, amount)) {
            return "Withdraw failed.";
        }
        moneyPot += amount;
        return nameOf(uuid) + " paid " + moneyGateway.format(amount) + " into pot. Pot=" + moneyGateway.format(moneyPot);
    }

    public String status() {
        String current = "-";
        UUID currentUuid = currentTurnPlayer();
        if (currentUuid != null) {
            current = nameOf(currentUuid);
        }
        UUID dealer = dealerUuid();
        String pending = hasPendingClaims()
                ? pendingDiscardTile.shortName() + " by " + nameOf(pendingDiscarder)
                : "none";
        return "Table " + id
                + " | players " + players.size() + "/4"
                + " | started " + started
                + " | length " + gameLength
                + " | minHan " + minimumHan
                + " | target " + minPointsToWin
                + " | round " + roundWind
                + " | dealer " + (dealer == null ? "-" : nameOf(dealer))
                + " | honba " + honba
                + " | riichiPot " + riichiPot
                + " | moneyPot " + (moneyGateway.enabled() ? moneyGateway.format(moneyPot) : "disabled")
                + " | turn " + current
                + " | wall " + wall.size()
                + " | claim " + pending;
    }

    public String claimView(UUID uuid) {
        if (!started) {
            return "Game not started.";
        }
        if (!hasPendingClaims()) {
            return "No pending claim.";
        }
        EnumSet<ClaimAction> options = pendingOptions.get(uuid);
        if (options == null) {
            return "No claim options for you.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Claim options: ").append(options);
        if (options.contains(ClaimAction.CHII)) {
            List<List<MahjongTile>> choices = getChiiChoices(uuid);
            if (!choices.isEmpty()) {
                sb.append(" | chii choices: ");
                for (int i = 0; i < choices.size(); i++) {
                    List<MahjongTile> pair = choices.get(i);
                    sb.append(pair.get(0).shortName()).append("+").append(pair.get(1).shortName());
                    if (i < choices.size() - 1) {
                        sb.append(", ");
                    }
                }
            }
        }
        return sb.toString();
    }

    public Location center() {
        return center.clone();
    }

    public int seatIndex(UUID uuid) {
        int idx = seatIndexOf(uuid);
        if (idx >= 0) {
            return idx;
        }
        int fallback = 0;
        for (UUID playerId : players.keySet()) {
            if (playerId.equals(uuid)) {
                return fallback;
            }
            fallback++;
        }
        return -1;
    }

    public float seatYaw(UUID uuid) {
        int seat = seatIndex(uuid);
        if (seat < 0) {
            return 0f;
        }
        return yawForSeat(seat);
    }

    public Location seatAnchor(UUID uuid, double xOffset, double zOffset, double y) {
        int seat = seatIndex(uuid);
        if (seat < 0) {
            return center.clone().add(xOffset, y, zOffset);
        }
        return seatPoint(seat, xOffset, zOffset, y);
    }

    public List<MahjongTile> handSnapshot(UUID uuid) {
        MahjongPlayerState state = players.get(uuid);
        if (state == null) {
            return List.of();
        }
        return List.copyOf(state.hand());
    }

    public Set<ClaimAction> claimOptions(UUID uuid) {
        EnumSet<ClaimAction> options = pendingOptions.get(uuid);
        if (options == null || options.isEmpty()) {
            return Set.of();
        }
        return EnumSet.copyOf(options);
    }

    public List<List<MahjongTile>> chiiChoices(UUID uuid) {
        return List.copyOf(getChiiChoices(uuid));
    }

    public boolean canDiscardNow(UUID uuid) {
        MahjongPlayerState state = players.get(uuid);
        return started
                && state != null
                && !hasPendingClaims()
                && uuid.equals(currentTurnPlayer())
                && state.hand().size() % 3 == 2;
    }

    public boolean canRiichiNow(UUID uuid) {
        MahjongPlayerState state = players.get(uuid);
        return started
                && state != null
                && !hasPendingClaims()
                && uuid.equals(currentTurnPlayer())
                && state.closedHand()
                && !state.riichi()
                && state.points() >= 1000
                && HandAnalyzer.isTenpai(state.hand(), state.melds());
    }

    public boolean canTsumoNow(UUID uuid) {
        MahjongPlayerState state = players.get(uuid);
        if (!started || state == null || hasPendingClaims() || !uuid.equals(currentTurnPlayer())) {
            return false;
        }
        if (state.hand().size() % 3 != 2) {
            return false;
        }
        MahjongTile agariTile = state.hand().isEmpty() ? null : state.hand().get(state.hand().size() - 1);
        if (agariTile == null || HandAnalyzer.analyzeWinning(state.hand(), state.melds()) == null) {
            return false;
        }
        HandValue value = ScoreEngine.evaluateHand(
                state,
                state.hand(),
                agariTile,
                true,
                seatWindOf(uuid),
                roundWind,
                buildTsumoContext(state)
        );
        return canWinByRule(value);
    }

    public boolean canSelfKanNow(UUID uuid) {
        MahjongPlayerState state = players.get(uuid);
        if (!started || state == null || hasPendingClaims() || !uuid.equals(currentTurnPlayer())) {
            return false;
        }
        if (state.hand().size() % 3 != 2 || !canDeclareKanNow()) {
            return false;
        }
        if (hasAddedKanOption(state)) {
            return true;
        }
        return hasConcealedKanOption(state);
    }

    public boolean canKyuushuNow(UUID uuid) {
        MahjongPlayerState state = players.get(uuid);
        return started
                && state != null
                && uuid.equals(currentTurnPlayer())
                && canDeclareKyuushuKyuuhai(state);
    }

    public void broadcast(String message) {
        ensureMainThread();
        for (UUID uuid : players.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                MessageUtil.sendRaw(player, message);
            }
        }
    }

    public void broadcastKey(String key) {
        broadcastKey(key, Map.of());
    }

    public void broadcastKey(String key, Map<String, String> args) {
        ensureMainThread();
        for (UUID uuid : players.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                MessageUtil.send(player, key, args);
            }
        }
    }

    public boolean isEmpty() {
        return players.isEmpty();
    }

    private String declare(UUID uuid, ClaimAction action, List<MahjongTile> chosenTiles) {
        ensureMainThread();
        if (!started) {
            return "Game not started.";
        }
        if (!hasPendingClaims()) {
            return "No pending discard for claim.";
        }
        EnumSet<ClaimAction> options = pendingOptions.get(uuid);
        if (options == null || (!options.contains(action) && action != ClaimAction.PASS)) {
            return "This claim is not available for you.";
        }
        if (action == ClaimAction.RON && isFuriten(uuid)) {
            return "Furiten: ron is not allowed.";
        }
        if (action == ClaimAction.CHII && !isValidChiiChoice(uuid, chosenTiles)) {
            return "Invalid chii tiles.";
        }
        markFuritenOnDecline(uuid, action, options);

        pendingDeclarations.put(uuid, new ClaimDeclaration(action, chosenTiles));
        if (action == ClaimAction.PASS) {
            pendingDeclarations.put(uuid, new ClaimDeclaration(ClaimAction.PASS, List.of()));
        }

        if (pendingDeclarations.size() < pendingOptions.size()) {
            return "Claim recorded. Waiting others.";
        }

        String result = resolveClaims();
        refreshDisplays();
        return result;
    }

    private String resolveClaims() {
        List<UUID> ronWinners = new ArrayList<>();
        for (Map.Entry<UUID, ClaimDeclaration> entry : pendingDeclarations.entrySet()) {
            if (entry.getValue().action == ClaimAction.RON) {
                ronWinners.add(entry.getKey());
            }
        }
        if (!ronWinners.isEmpty()) {
            ronWinners.sort((a, b) -> Integer.compare(seatDistance(pendingDiscarder, a), seatDistance(pendingDiscarder, b)));
            UUID atamahane = ronWinners.get(0);
            int pot = riichiPot;
            riichiPot = 0;
            boolean dealerWin = false;
            List<String> summary = new ArrayList<>();
            Map<UUID, Integer> delta = new HashMap<>();
            int loserLoss = honba * 300;
            for (UUID winner : ronWinners) {
                MahjongPlayerState winnerState = players.get(winner);
                if (winnerState == null) {
                    continue;
                }
                HandAnalyzer.WinPattern pattern = HandAnalyzer.analyzeWinning(
                        concat(winnerState.hand(), pendingDiscardTile),
                        winnerState.melds()
                );
                if (pattern == null) {
                    continue;
                }
                HandValue value = ScoreEngine.evaluateHand(
                        winnerState,
                        concat(winnerState.hand(), pendingDiscardTile),
                        pendingDiscardTile,
                        false,
                        seatWindOf(winner),
                        roundWind,
                        buildRonContext(winner)
                );
                if (!canWinByRule(value)) {
                    continue;
                }
                int basic = winner.equals(dealerUuid()) ? value.ronDealer() : value.ronChild();
                loserLoss += basic;
                int gain = basic;
                if (winner.equals(atamahane)) {
                    gain += pot + honba * 300;
                }
                delta.put(winner, delta.getOrDefault(winner, 0) + gain);
                summary.add(nameOf(winner) + " ron (" + value.han() + " han " + value.fu() + " fu)");
                if (winner.equals(dealerUuid())) {
                    dealerWin = true;
                }
                winnerState.ippatsuEligible(false);
            }
            if (summary.isEmpty()) {
                clearClaims();
                if (!advanceTurnAndDraw()) {
                    return endByDraw("All passed and wall exhausted.");
                }
                return "All passed. Next player drew.";
            }
            delta.put(pendingDiscarder, delta.getOrDefault(pendingDiscarder, 0) - loserLoss);
            applyPointDelta(delta);
            onHandFinished(dealerWin, false);
            clearClaims();
            payoutMoneyPot(ronWinners);
            return String.join(", ", summary) + " | " + pointsSnapshot();
        }

        UUID ponKanWinner = null;
        ClaimAction ponKanAction = null;
        int best = Integer.MAX_VALUE;
        for (Map.Entry<UUID, ClaimDeclaration> entry : pendingDeclarations.entrySet()) {
            ClaimAction action = entry.getValue().action;
            if (action != ClaimAction.PON && action != ClaimAction.KAN) {
                continue;
            }
            int dist = seatDistance(pendingDiscarder, entry.getKey());
            if (dist < best) {
                best = dist;
                ponKanWinner = entry.getKey();
                ponKanAction = action;
            }
        }
        if (ponKanWinner != null) {
            String result = applyPonOrKan(ponKanWinner, ponKanAction);
            clearClaims();
            return result;
        }

        UUID chiiWinner = nextPlayerOf(pendingDiscarder);
        ClaimDeclaration chiiDecl = pendingDeclarations.get(chiiWinner);
        if (chiiDecl != null && chiiDecl.action == ClaimAction.CHII) {
            String result = applyChii(chiiWinner, chiiDecl.tiles);
            clearClaims();
            return result;
        }

        if (pendingClaimSource == ClaimSource.CHANKAN_KAKAN && pendingKakan != null) {
            String result = finalizeAddedKan(pendingKakan.owner(), pendingKakan.meldIndex(), pendingKakan.tile());
            clearClaims();
            return result;
        }
        if (pendingClaimSource == ClaimSource.CHANKAN_ANKAN && pendingAnkanOwner != null) {
            MahjongPlayerState owner = players.get(pendingAnkanOwner);
            if (owner == null || !drawSupplementTile(owner)) {
                return endByDraw("Concealed kan resolved and wall exhausted.");
            }
            if (shouldAbortBySuukaikan()) {
                String abort = endByAbortiveDraw("Abortive draw: suukaikan.");
                clearClaims();
                return abort;
            }
            String result = nameOf(pendingAnkanOwner) + " concealed kan resolved and drew a supplement tile.";
            clearClaims();
            return result;
        }
        clearClaims();
        if (!advanceTurnAndDraw()) {
            return endByDraw("All passed and wall exhausted.");
        }
        return "All passed. Next player drew.";
    }

    private String applyPonOrKan(UUID caller, ClaimAction action) {
        MahjongPlayerState state = players.get(caller);
        if (state == null || pendingDiscardTile == null) {
            return "Claim failed.";
        }
        removeLastDiscardFrom(pendingDiscarder, pendingDiscardTile);
        if (pendingDiscardGlobalIndex >= 0) {
            calledDiscardIndices.add(pendingDiscardGlobalIndex);
        }
        List<MahjongTile> removed = removeTilesByType(state.hand(), pendingDiscardTile, action == ClaimAction.KAN ? 3 : 2);
        if (removed.size() < (action == ClaimAction.KAN ? 3 : 2)) {
            return "Claim failed: tiles missing.";
        }
        List<MahjongTile> meldTiles = new ArrayList<>(removed);
        meldTiles.add(pendingDiscardTile);
        state.melds().add(new MahjongMeld(action == ClaimAction.KAN ? MeldType.KAN_OPEN : MeldType.PON, meldTiles, true));
        state.temporaryFuriten(false);
        turnIndex = seatIndexOf(caller);
        clearIppatsuAll();
        if (action == ClaimAction.KAN) {
            if (!drawSupplementTile(state)) {
                return endByDraw("Kan resolved and wall exhausted.");
            }
            if (shouldAbortBySuukaikan()) {
                return endByAbortiveDraw("Abortive draw: suukaikan.");
            }
            return nameOf(caller) + " declared kan on " + pendingDiscardTile.shortName() + " and drew a supplement tile.";
        }
        return nameOf(caller) + " declared pon on " + pendingDiscardTile.shortName() + ".";
    }

    private String applyChii(UUID caller, List<MahjongTile> chosenTiles) {
        MahjongPlayerState state = players.get(caller);
        if (state == null || pendingDiscardTile == null || chosenTiles.size() != 2) {
            return "Chii failed.";
        }
        removeLastDiscardFrom(pendingDiscarder, pendingDiscardTile);
        if (pendingDiscardGlobalIndex >= 0) {
            calledDiscardIndices.add(pendingDiscardGlobalIndex);
        }
        MahjongTile t1 = consumeOne(state.hand(), chosenTiles.get(0));
        MahjongTile t2 = consumeOne(state.hand(), chosenTiles.get(1));
        if (t1 == null || t2 == null) {
            return "Chii failed: tiles missing.";
        }
        List<MahjongTile> meld = new ArrayList<>();
        meld.add(t1);
        meld.add(t2);
        meld.add(pendingDiscardTile);
        meld.sort(MahjongTile.SORTER);
        state.melds().add(new MahjongMeld(MeldType.CHII, meld, true));
        state.temporaryFuriten(false);
        turnIndex = seatIndexOf(caller);
        clearIppatsuAll();
        return nameOf(caller) + " declared chii on " + pendingDiscardTile.shortName() + ".";
    }

    private void openClaimWindow(UUID discarder, MahjongTile discarded) {
        pendingClaimSource = ClaimSource.DISCARD;
        pendingKakan = null;
        pendingAnkanOwner = null;
        pendingDiscarder = discarder;
        pendingDiscardTile = discarded;
        pendingOptions.clear();
        pendingDeclarations.clear();

        for (UUID uuid : turnOrder) {
            if (uuid.equals(discarder)) {
                continue;
            }
            MahjongPlayerState state = players.get(uuid);
            if (state == null) {
                continue;
            }
            EnumSet<ClaimAction> set = EnumSet.of(ClaimAction.PASS);
            if (canRonAgainstDiscard(uuid, discarded)) {
                set.add(ClaimAction.RON);
            }
            boolean houtei = wall.isEmpty();
            if (!houtei && !state.riichi()) {
                if (countSameType(state.hand(), discarded) >= 3 && canOpenKanFromDiscard(uuid, discarder)) {
                    if (canDeclareKanNow()) {
                        set.add(ClaimAction.KAN);
                    }
                }
                if (countSameType(state.hand(), discarded) >= 2) {
                    set.add(ClaimAction.PON);
                }
                if (uuid.equals(nextPlayerOf(discarder)) && hasChiiOption(state.hand(), discarded)) {
                    set.add(ClaimAction.CHII);
                }
            }
            if (set.size() > 1) {
                pendingOptions.put(uuid, set);
            }
        }
        if (pendingOptions.isEmpty()) {
            clearClaims();
        }
    }

    private boolean canOpenKanFromDiscard(UUID caller, UUID discarder) {
        return !caller.equals(nextPlayerOf(discarder));
    }

    private void openChankanWindow(UUID kanPlayer, MahjongTile tile, int meldIndex, boolean ankan) {
        pendingClaimSource = ankan ? ClaimSource.CHANKAN_ANKAN : ClaimSource.CHANKAN_KAKAN;
        pendingKakan = ankan ? null : new PendingKakan(kanPlayer, meldIndex, tile);
        pendingAnkanOwner = ankan ? kanPlayer : null;
        pendingDiscarder = kanPlayer;
        pendingDiscardTile = tile;
        pendingDiscardGlobalIndex = -1;
        pendingOptions.clear();
        pendingDeclarations.clear();
        for (UUID uuid : turnOrder) {
            if (uuid.equals(kanPlayer)) {
                continue;
            }
            if (canRonAgainstDiscard(uuid, tile) && (!ankan || canRobAnkanWithKokushi(uuid, tile))) {
                pendingOptions.put(uuid, EnumSet.of(ClaimAction.PASS, ClaimAction.RON));
            }
        }
        if (pendingOptions.isEmpty()) {
            clearClaims();
        }
    }

    private String selfKan(UUID uuid, MahjongTile wanted) {
        if (!started) {
            return "Game not started.";
        }
        if (!uuid.equals(currentTurnPlayer())) {
            return "Not your turn.";
        }
        MahjongPlayerState state = players.get(uuid);
        if (state == null) {
            return "You are not in this table.";
        }
        if (state.hand().size() % 3 != 2) {
            return "You cannot kan now.";
        }
        if (!canDeclareKanNow()) {
            return "No valid kan available.";
        }
        if (state.riichi() && wanted == null) {
            return "Specify tile for kan when in riichi.";
        }
        String added = tryAddedKan(uuid, state, wanted);
        if (added != null) {
            return added;
        }
        String concealed = tryConcealedKan(uuid, state, wanted);
        if (concealed != null) {
            return concealed;
        }
        return "No valid kan available.";
    }

    private String tryConcealedKan(UUID uuid, MahjongPlayerState state, MahjongTile wanted) {
        MahjongTile target = findConcealedKanTile(state.hand(), wanted);
        if (target == null) {
            return null;
        }
        if (state.riichi()) {
            Set<Integer> before = winningSortOrders(state.hand(), state.melds());
            List<MahjongTile> handAfter = new ArrayList<>(state.hand());
            List<MahjongTile> removedPreview = removeTilesByType(handAfter, target, 4);
            if (removedPreview.size() < 4) {
                return null;
            }
            List<MahjongMeld> meldsAfter = new ArrayList<>(state.melds());
            removedPreview.sort(MahjongTile.SORTER);
            meldsAfter.add(new MahjongMeld(MeldType.KAN_CLOSED, removedPreview, false));
            Set<Integer> after = winningSortOrders(handAfter, meldsAfter);
            if (!before.equals(after)) {
                return "Riichi kan must keep waits unchanged.";
            }
        }
        List<MahjongTile> removed = removeTilesByType(state.hand(), target, 4);
        if (removed.size() < 4) {
            return null;
        }
        removed.sort(MahjongTile.SORTER);
        state.melds().add(new MahjongMeld(MeldType.KAN_CLOSED, removed, false));
        state.temporaryFuriten(false);
        clearIppatsuAll();
        openChankanWindow(uuid, target, -1, true);
        if (hasPendingClaims()) {
            return nameOf(uuid) + " declared concealed kan on " + target.shortName() + ". Waiting chankan responses.";
        }
        if (!drawSupplementTile(state)) {
            return endByDraw("Concealed kan resolved and wall exhausted.");
        }
        if (shouldAbortBySuukaikan()) {
            return endByAbortiveDraw("Abortive draw: suukaikan.");
        }
        return nameOf(uuid) + " declared concealed kan on " + target.shortName() + ".";
    }

    private String tryAddedKan(UUID uuid, MahjongPlayerState state, MahjongTile wanted) {
        for (int i = 0; i < state.melds().size(); i++) {
            MahjongMeld meld = state.melds().get(i);
            if (meld.type() != MeldType.PON || !meld.open()) {
                continue;
            }
            MahjongTile base = meld.baseTile();
            if (wanted != null && !base.sameType(wanted)) {
                continue;
            }
            MahjongTile added = consumeOne(state.hand(), base);
            if (added == null) {
                continue;
            }
            openChankanWindow(uuid, added, i, false);
            if (!hasPendingClaims()) {
                return finalizeAddedKan(uuid, i, added);
            }
            return nameOf(uuid) + " declared added kan on " + base.shortName() + ". Waiting chankan responses.";
        }
        return null;
    }

    private String finalizeAddedKan(UUID owner, int meldIndex, MahjongTile addedTile) {
        MahjongPlayerState state = players.get(owner);
        if (state == null || meldIndex < 0 || meldIndex >= state.melds().size()) {
            return "Added kan failed.";
        }
        MahjongMeld old = state.melds().get(meldIndex);
        if (old.type() != MeldType.PON) {
            return "Added kan failed.";
        }
        List<MahjongTile> tiles = new ArrayList<>(old.tiles());
        tiles.add(addedTile);
        tiles.sort(MahjongTile.SORTER);
        state.melds().set(meldIndex, new MahjongMeld(MeldType.KAN_ADDED, tiles, true));
        state.temporaryFuriten(false);
        clearIppatsuAll();
        if (!drawSupplementTile(state)) {
            return endByDraw("Added kan resolved and wall exhausted.");
        }
        if (shouldAbortBySuukaikan()) {
            return endByAbortiveDraw("Abortive draw: suukaikan.");
        }
        return nameOf(owner) + " completed added kan on " + addedTile.shortName() + ".";
    }

    private MahjongTile findConcealedKanTile(List<MahjongTile> hand, MahjongTile wanted) {
        for (int sortOrder = 0; sortOrder <= 33; sortOrder++) {
            MahjongTile base = MahjongTile.fromSortOrder(sortOrder);
            if (wanted != null && !base.sameType(wanted)) {
                continue;
            }
            if (countSameType(hand, base) >= 4) {
                return base;
            }
        }
        return null;
    }

    private boolean hasAddedKanOption(MahjongPlayerState state) {
        for (MahjongMeld meld : state.melds()) {
            if (meld.type() != MeldType.PON || !meld.open()) {
                continue;
            }
            if (countSameType(state.hand(), meld.baseTile()) >= 1) {
                return true;
            }
        }
        return false;
    }

    private boolean hasConcealedKanOption(MahjongPlayerState state) {
        Set<Integer> before = null;
        if (state.riichi()) {
            before = winningSortOrders(state.hand(), state.melds());
        }
        for (int sortOrder = 0; sortOrder <= 33; sortOrder++) {
            MahjongTile base = MahjongTile.fromSortOrder(sortOrder);
            if (countSameType(state.hand(), base) < 4) {
                continue;
            }
            if (!state.riichi()) {
                return true;
            }
            List<MahjongTile> handAfter = new ArrayList<>(state.hand());
            List<MahjongTile> removed = removeTilesByType(handAfter, base, 4);
            if (removed.size() < 4) {
                continue;
            }
            List<MahjongMeld> meldsAfter = new ArrayList<>(state.melds());
            removed.sort(MahjongTile.SORTER);
            meldsAfter.add(new MahjongMeld(MeldType.KAN_CLOSED, removed, false));
            Set<Integer> after = winningSortOrders(handAfter, meldsAfter);
            if (Objects.equals(before, after)) {
                return true;
            }
        }
        return false;
    }

    private boolean drawInto(MahjongPlayerState state, boolean rinshan) {
        MahjongTile drawn = wall.pollFirst();
        if (drawn == null) {
            return false;
        }
        state.hand().add(drawn);
        state.hand().sort(MahjongTile.SORTER);
        state.lastDrawRinshan(rinshan);
        state.temporaryFuriten(false);
        return true;
    }

    private boolean drawSupplementTile(MahjongPlayerState state) {
        return drawInto(state, true);
    }

    private void clearIppatsuAll() {
        for (MahjongPlayerState state : players.values()) {
            state.ippatsuEligible(false);
        }
    }

    private void markFuritenOnDecline(UUID uuid, ClaimAction action, EnumSet<ClaimAction> options) {
        if (!options.contains(ClaimAction.RON) || action == ClaimAction.RON) {
            return;
        }
        MahjongPlayerState state = players.get(uuid);
        if (state == null) {
            return;
        }
        if (state.riichi()) {
            state.riichiFuriten(true);
        } else {
            state.temporaryFuriten(true);
        }
    }

    private boolean canRonAgainstDiscard(UUID uuid, MahjongTile tile) {
        MahjongPlayerState state = players.get(uuid);
        if (state == null) {
            return false;
        }
        if (!HandAnalyzer.canRon(state.hand(), tile, state.melds())) {
            return false;
        }
        HandValue value = ScoreEngine.evaluateHand(
                state,
                concat(state.hand(), tile),
                tile,
                false,
                seatWindOf(uuid),
                roundWind,
                pendingClaimSource == ClaimSource.DISCARD
                        ? new WinContext(isIppatsu(state), false, wall.isEmpty(), false, false)
                        : new WinContext(isIppatsu(state), false, false, false, true)
        );
        if (!canWinByRule(value)) {
            return false;
        }
        return !isFuriten(uuid);
    }

    private boolean isFuriten(UUID uuid) {
        MahjongPlayerState state = players.get(uuid);
        if (state == null || pendingDiscardTile == null) {
            return false;
        }
        if (state.temporaryFuriten()) {
            return true;
        }
        Set<Integer> waits = winningSortOrders(state);
        if (waits.isEmpty()) {
            return false;
        }
        for (MahjongTile discarded : state.discards()) {
            if (waits.contains(discarded.sortOrder())) {
                return true;
            }
        }
        int endExclusive = discardHistory.size() - 1;
        if (endExclusive > 0) {
            int sameTurnStart = state.lastDiscardGlobalIndex();
            if (sameTurnStart >= 0 && sameTurnStart < endExclusive) {
                for (int i = sameTurnStart; i < endExclusive; i++) {
                    if (waits.contains(discardHistory.get(i).sortOrder())) {
                        return true;
                    }
                }
            }
            if (state.riichi() && state.riichiDeclarationGlobalIndex() >= 0) {
                int riichiStart = state.riichiDeclarationGlobalIndex();
                for (int i = riichiStart; i < endExclusive; i++) {
                    if (waits.contains(discardHistory.get(i).sortOrder())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private Set<Integer> winningSortOrders(MahjongPlayerState state) {
        return winningSortOrders(state.hand(), state.melds());
    }

    private Set<Integer> winningSortOrders(List<MahjongTile> hand, List<MahjongMeld> melds) {
        Set<Integer> waits = new HashSet<>();
        for (int sortOrder = 0; sortOrder <= 33; sortOrder++) {
            MahjongTile tile = MahjongTile.fromSortOrder(sortOrder);
            if (HandAnalyzer.canRon(hand, tile, melds)) {
                waits.add(sortOrder);
            }
        }
        return waits;
    }

    private WinContext buildTsumoContext(MahjongPlayerState state) {
        boolean ippatsu = isIppatsu(state);
        boolean rinshan = state.lastDrawRinshan();
        boolean haitei = wall.isEmpty() && !rinshan;
        return new WinContext(ippatsu, haitei, false, rinshan, false);
    }

    private WinContext buildRonContext(UUID winner) {
        MahjongPlayerState state = players.get(winner);
        if (state == null) {
            return WinContext.none();
        }
        boolean ippatsu = isIppatsu(state);
        boolean houtei = pendingClaimSource == ClaimSource.DISCARD && wall.isEmpty();
        boolean chankan = pendingClaimSource == ClaimSource.CHANKAN_ANKAN || pendingClaimSource == ClaimSource.CHANKAN_KAKAN;
        return new WinContext(ippatsu, false, houtei, false, chankan);
    }

    private boolean isIppatsu(MahjongPlayerState state) {
        if (!state.riichi()) {
            return false;
        }
        int riichiIndex = state.riichiDeclarationGlobalIndex();
        if (riichiIndex < 0 || discardHistory.isEmpty()) {
            return false;
        }
        int lastIndex = discardHistory.size() - 1;
        if (lastIndex - riichiIndex > 4) {
            return false;
        }
        for (Integer calledIndex : calledDiscardIndices) {
            if (calledIndex >= riichiIndex && calledIndex <= lastIndex) {
                return false;
            }
        }
        return true;
    }

    private boolean canRobAnkanWithKokushi(UUID uuid, MahjongTile tile) {
        MahjongPlayerState state = players.get(uuid);
        if (state == null) {
            return false;
        }
        return isKokushiMusou(concat(state.hand(), tile), state.melds());
    }

    private boolean canDeclareKyuushuKyuuhai(MahjongPlayerState state) {
        if (hasPendingClaims() || !state.discards().isEmpty() || state.hand().size() % 3 != 2) {
            return false;
        }
        int totalDiscards = 0;
        for (MahjongPlayerState p : players.values()) {
            totalDiscards += p.discards().size();
        }
        if (totalDiscards >= 4) {
            return false;
        }
        Set<Integer> yaochuTypes = new HashSet<>();
        for (MahjongTile tile : state.hand()) {
            if (tile.isHonor() || tile.isTerminal()) {
                yaochuTypes.add(tile.sortOrder());
            }
        }
        return yaochuTypes.size() >= 9;
    }

    private boolean isSuufonRenda() {
        if (!calledDiscardIndices.isEmpty() || turnOrder.size() != 4) {
            return false;
        }
        if (discardHistory.size() != 4) {
            return false;
        }
        List<MahjongTile> firstDiscards = new ArrayList<>(4);
        for (UUID uuid : turnOrder) {
            MahjongPlayerState state = players.get(uuid);
            if (state == null || state.discards().isEmpty()) {
                return false;
            }
            firstDiscards.add(state.discards().get(0));
        }
        MahjongTile first = firstDiscards.get(0);
        if (!isWindTile(first)) {
            return false;
        }
        for (MahjongTile tile : firstDiscards) {
            if (!tile.sameType(first)) {
                return false;
            }
        }
        return true;
    }

    private boolean isSuuchaRiichi() {
        if (turnOrder.size() != 4) {
            return false;
        }
        for (UUID uuid : turnOrder) {
            MahjongPlayerState state = players.get(uuid);
            if (state == null || !state.riichi()) {
                return false;
            }
        }
        return true;
    }

    private boolean shouldAbortBySuukaikan() {
        int totalKan = 0;
        for (MahjongPlayerState state : players.values()) {
            for (MahjongMeld meld : state.melds()) {
                if (meld.type().isKan()) {
                    totalKan++;
                }
            }
        }
        if (totalKan < 4) {
            return false;
        }
        for (MahjongPlayerState state : players.values()) {
            int selfKan = 0;
            for (MahjongMeld meld : state.melds()) {
                if (meld.type().isKan()) {
                    selfKan++;
                }
            }
            if (selfKan >= 4) {
                return false;
            }
        }
        return true;
    }

    private boolean canDeclareKanNow() {
        int totalKan = 0;
        for (MahjongPlayerState state : players.values()) {
            for (MahjongMeld meld : state.melds()) {
                if (meld.type().isKan()) {
                    totalKan++;
                }
            }
        }
        return totalKan < 4;
    }

    private boolean isWindTile(MahjongTile tile) {
        return tile == MahjongTile.EAST || tile == MahjongTile.SOUTH || tile == MahjongTile.WEST || tile == MahjongTile.NORTH;
    }

    private boolean canWinByRule(HandValue value) {
        return value != null && (value.yakuman() || value.han() >= minimumHan);
    }

    private boolean isAllLast() {
        return (spentRounds + 1) >= gameLength.rounds();
    }

    private boolean hasAnyPlayerReachedTarget() {
        for (MahjongPlayerState state : players.values()) {
            if (state.points() >= minPointsToWin) {
                return true;
            }
        }
        return false;
    }

    private boolean isFinalRound() {
        return roundWind == gameLength.finalWind() && dealerSeat == gameLength.finalDealerSeat();
    }

    private void finishGame() {
        started = false;
        clearClaims();
        refreshDisplays();
    }

    private boolean isKokushiMusou(List<MahjongTile> tiles, List<MahjongMeld> melds) {
        if (!melds.isEmpty() || tiles.size() != 14) {
            return false;
        }
        Set<Integer> yaochu = Set.of(0, 8, 9, 17, 18, 26, 27, 28, 29, 30, 31, 32, 33);
        int pairCount = 0;
        for (Integer sortOrder : yaochu) {
            int count = 0;
            for (MahjongTile tile : tiles) {
                if (tile.sortOrder() == sortOrder) {
                    count++;
                }
            }
            if (count == 0) {
                return false;
            }
            if (count >= 2) {
                pairCount++;
            }
        }
        return pairCount == 1;
    }

    private void rotateDealer() {
        dealerSeat = (dealerSeat + 1) % 4;
        spentRounds++;
        if (dealerSeat == 0) {
            roundWind = roundWind.next();
        }
    }

    private boolean hasChiiOption(List<MahjongTile> hand, MahjongTile discard) {
        if (!discard.isSuit()) {
            return false;
        }
        int n = discard.number();
        int suit = discard.suitIndex();
        return hasTile(hand, suit, n - 2) && hasTile(hand, suit, n - 1)
                || hasTile(hand, suit, n - 1) && hasTile(hand, suit, n + 1)
                || hasTile(hand, suit, n + 1) && hasTile(hand, suit, n + 2);
    }

    private boolean isValidChiiChoice(UUID uuid, List<MahjongTile> chosenTiles) {
        if (chosenTiles.size() != 2 || pendingDiscardTile == null) {
            return false;
        }
        if (!uuid.equals(nextPlayerOf(pendingDiscarder))) {
            return false;
        }
        MahjongPlayerState state = players.get(uuid);
        if (state == null) {
            return false;
        }
        MahjongTile a = chosenTiles.get(0);
        MahjongTile b = chosenTiles.get(1);
        if (!hasOne(state.hand(), a) || !hasOne(state.hand(), b)) {
            return false;
        }
        if (!pendingDiscardTile.isSuit()) {
            return false;
        }
        int suit = pendingDiscardTile.suitIndex();
        int n = pendingDiscardTile.number();
        int x = a.number();
        int y = b.number();
        if (a.suitIndex() != suit || b.suitIndex() != suit) {
            return false;
        }
        Set<Integer> numbers = new HashSet<>();
        numbers.add(x);
        numbers.add(y);
        numbers.add(n);
        return numbers.size() == 3 && Collections.max(numbers) - Collections.min(numbers) == 2;
    }

    private List<List<MahjongTile>> getChiiChoices(UUID uuid) {
        if (pendingDiscardTile == null || !pendingDiscardTile.isSuit()) {
            return List.of();
        }
        MahjongPlayerState state = players.get(uuid);
        if (state == null) {
            return List.of();
        }
        int suit = pendingDiscardTile.suitIndex();
        int n = pendingDiscardTile.number();
        List<List<MahjongTile>> out = new ArrayList<>();

        addChiiChoice(out, state.hand(), suit, n - 2, n - 1);
        addChiiChoice(out, state.hand(), suit, n - 1, n + 1);
        addChiiChoice(out, state.hand(), suit, n + 1, n + 2);
        return out;
    }

    private void addChiiChoice(List<List<MahjongTile>> out, List<MahjongTile> hand, int suit, int n1, int n2) {
        if (n1 < 1 || n1 > 9 || n2 < 1 || n2 > 9) {
            return;
        }
        MahjongTile t1 = firstBySortOrder(hand, suit * 9 + (n1 - 1));
        MahjongTile t2 = firstBySortOrder(hand, suit * 9 + (n2 - 1));
        if (t1 != null && t2 != null) {
            out.add(List.of(t1, t2));
        }
    }

    private boolean advanceTurnAndDraw() {
        turnIndex = (turnIndex + 1) % turnOrder.size();
        MahjongPlayerState next = players.get(currentTurnPlayer());
        if (next == null) {
            return false;
        }
        return drawInto(next, false);
    }

    private String startNewHand(String prefix) {
        started = true;
        wall.clear();
        discardHistory.clear();
        calledDiscardIndices.clear();
        clearClaims();
        turnIndex = dealerSeat;

        List<MahjongTile> shuffled = MahjongTile.buildWallWithThreeRedFives();
        Collections.shuffle(shuffled);
        wall.addAll(shuffled);

        for (MahjongPlayerState state : players.values()) {
            state.hand().clear();
            state.discards().clear();
            state.melds().clear();
            state.riichi(false);
            state.ippatsuEligible(false);
            state.temporaryFuriten(false);
            state.riichiFuriten(false);
            state.lastDrawRinshan(false);
            state.lastDiscardGlobalIndex(-1);
            state.riichiDeclarationGlobalIndex(-1);
            for (int i = 0; i < 13; i++) {
                state.hand().add(Objects.requireNonNull(wall.pollFirst()));
            }
            state.hand().sort(MahjongTile.SORTER);
        }
        MahjongPlayerState dealer = players.get(dealerUuid());
        if (dealer != null) {
            dealer.hand().add(Objects.requireNonNull(wall.pollFirst()));
            dealer.hand().sort(MahjongTile.SORTER);
        }
        refreshDisplays();
        return prefix + " Dealer: " + nameOf(dealerUuid());
    }

    private void applySettlement(Settlement settlement, List<UUID> winners) {
        applyPointDelta(settlement.pointDelta());
        if (!winners.isEmpty()) {
            riichiPot = 0;
        }
        onHandFinished(settlement.dealerContinues(), settlement.draw());
    }

    private void applyPointDelta(Map<UUID, Integer> delta) {
        for (Map.Entry<UUID, Integer> entry : delta.entrySet()) {
            MahjongPlayerState state = players.get(entry.getKey());
            if (state == null) {
                continue;
            }
            state.points(state.points() + entry.getValue());
        }
    }

    private void onHandFinished(boolean dealerContinues, boolean draw) {
        boolean allLast = isAllLast();
        if (!allLast) {
            if (dealerContinues) {
                honba++;
            } else {
                honba = 0;
                rotateDealer();
            }
            startNewHand("Next hand.");
            return;
        }
        if (hasAnyPlayerReachedTarget()) {
            finishGame();
            return;
        }
        if (dealerContinues) {
            honba++;
            startNewHand("Next hand.");
            return;
        }
        honba = 0;
        if (isFinalRound()) {
            finishGame();
            return;
        }
        rotateDealer();
        startNewHand("Next hand.");
    }

    private String endByDraw(String reason) {
        Set<UUID> tenpaiPlayers = new HashSet<>();
        for (UUID uuid : turnOrder) {
            MahjongPlayerState state = players.get(uuid);
            if (state != null && HandAnalyzer.isTenpai(state.hand(), state.melds())) {
                tenpaiPlayers.add(uuid);
            }
        }
        Settlement settlement = ScoreEngine.settleExhaustiveDraw(turnOrder, tenpaiPlayers, dealerUuid());
        applySettlement(settlement, List.of());
        return reason + " " + settlement.summary();
    }

    private String endByAbortiveDraw(String reason) {
        Settlement settlement = ScoreEngine.settleAbortiveDraw(reason);
        applySettlement(settlement, List.of());
        return reason;
    }

    private void clearClaims() {
        pendingDiscardTile = null;
        pendingDiscarder = null;
        pendingDiscardGlobalIndex = -1;
        pendingOptions.clear();
        pendingDeclarations.clear();
        pendingClaimSource = ClaimSource.NONE;
        pendingKakan = null;
        pendingAnkanOwner = null;
    }

    private void payoutMoneyPot(List<UUID> winners) {
        if (!moneyGateway.enabled() || winners == null || winners.isEmpty() || moneyPot <= 0.0) {
            return;
        }
        double share = moneyPot / winners.size();
        for (UUID winner : winners) {
            moneyGateway.deposit(winner, share);
        }
        broadcast("Pot payout: " + moneyGateway.format(moneyPot) + " to " + winners.size() + " winner(s).");
        moneyPot = 0.0;
    }

    private boolean hasPendingClaims() {
        return pendingDiscardTile != null && !pendingOptions.isEmpty();
    }

    private UUID dealerUuid() {
        if (turnOrder.isEmpty()) {
            return null;
        }
        return turnOrder.get(dealerSeat % turnOrder.size());
    }

    private Wind seatWindOf(UUID uuid) {
        int seat = seatIndexOf(uuid);
        int relative = (seat - dealerSeat + 4) % 4;
        return Wind.values()[relative];
    }

    private int seatIndexOf(UUID uuid) {
        return turnOrder.indexOf(uuid);
    }

    private int seatDistance(UUID from, UUID to) {
        int i1 = seatIndexOf(from);
        int i2 = seatIndexOf(to);
        return (i2 - i1 + 4) % 4;
    }

    private UUID nextPlayerOf(UUID uuid) {
        int idx = seatIndexOf(uuid);
        return turnOrder.get((idx + 1) % 4);
    }

    private int countSameType(List<MahjongTile> hand, MahjongTile tile) {
        int c = 0;
        for (MahjongTile t : hand) {
            if (t.sameType(tile)) {
                c++;
            }
        }
        return c;
    }

    private List<MahjongTile> removeTilesByType(List<MahjongTile> hand, MahjongTile tile, int count) {
        List<MahjongTile> removed = new ArrayList<>();
        for (int i = hand.size() - 1; i >= 0 && removed.size() < count; i--) {
            if (hand.get(i).sameType(tile)) {
                removed.add(hand.remove(i));
            }
        }
        return removed;
    }

    private MahjongTile consumeOne(List<MahjongTile> hand, MahjongTile wanted) {
        for (int i = 0; i < hand.size(); i++) {
            MahjongTile tile = hand.get(i);
            if (tile == wanted || tile.sameType(wanted)) {
                return hand.remove(i);
            }
        }
        return null;
    }

    private boolean hasOne(List<MahjongTile> hand, MahjongTile wanted) {
        for (MahjongTile tile : hand) {
            if (tile == wanted || tile.sameType(wanted)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasTile(List<MahjongTile> hand, int suit, int number) {
        if (number < 1 || number > 9) {
            return false;
        }
        int sortOrder = suit * 9 + (number - 1);
        for (MahjongTile tile : hand) {
            if (tile.sortOrder() == sortOrder) {
                return true;
            }
        }
        return false;
    }

    private MahjongTile firstBySortOrder(List<MahjongTile> hand, int sortOrder) {
        for (MahjongTile tile : hand) {
            if (tile.sortOrder() == sortOrder) {
                return tile;
            }
        }
        return null;
    }

    private List<MahjongTile> concat(List<MahjongTile> hand, MahjongTile tile) {
        List<MahjongTile> list = new ArrayList<>(hand);
        list.add(tile);
        return list;
    }

    private String pointsSnapshot() {
        StringBuilder sb = new StringBuilder("Points:");
        for (UUID uuid : turnOrder) {
            MahjongPlayerState state = players.get(uuid);
            if (state != null) {
                sb.append(" ").append(nameOf(uuid)).append("=").append(state.points());
            }
        }
        return sb.toString();
    }

    private void removeLastDiscardFrom(UUID discarder, MahjongTile tile) {
        MahjongPlayerState state = players.get(discarder);
        if (state == null || state.discards().isEmpty()) {
            return;
        }
        List<MahjongTile> discards = state.discards();
        MahjongTile last = discards.get(discards.size() - 1);
        if (last.sameType(tile)) {
            discards.remove(discards.size() - 1);
        }
    }

    private String nameOf(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        return player != null ? player.getName() : uuid.toString();
    }

    private void clearDisplays() {
        removeDisplay(centerDisplay);
        centerDisplay = null;
        for (List<ItemDisplay> list : seatDiscardDisplays.values()) {
            removeDisplays(list);
        }
        for (List<ItemDisplay> list : seatMeldDisplays.values()) {
            removeDisplays(list);
        }
        seatDiscardDisplays.clear();
        seatMeldDisplays.clear();
        lastDisplayStateHash = Integer.MIN_VALUE;
    }

    private void refreshDisplays() {
        ensureMainThread();
        if (!started || center.getWorld() == null) {
            clearDisplays();
            return;
        }
        World world = center.getWorld();
        int stateHash = computeDisplayStateHash();
        if (stateHash == lastDisplayStateHash && displaysValid(world)) {
            return;
        }
        updateCenterMarker(world);
        for (int seat = 0; seat < turnOrder.size(); seat++) {
            UUID uuid = turnOrder.get(seat);
            MahjongPlayerState state = players.get(uuid);
            if (state == null) {
                continue;
            }
            updateDiscards(world, seat, state.discards());
            updateMelds(world, seat, state.melds());
        }
        trimUnusedSeats(seatDiscardDisplays, turnOrder.size());
        trimUnusedSeats(seatMeldDisplays, turnOrder.size());
        lastDisplayStateHash = stateHash;
    }

    private void updateCenterMarker(World world) {
        Location loc = center.clone().add(0, 1.1, 0);
        if (centerDisplay == null || !centerDisplay.isValid() || !world.equals(centerDisplay.getWorld())) {
            removeDisplay(centerDisplay);
            centerDisplay = world.spawn(loc, ItemDisplay.class);
            centerDisplay.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(),
                    new Vector3f(0.5f, 0.5f, 0.5f),
                    new AxisAngle4f()
            ));
        } else {
            centerDisplay.teleport(loc);
        }
        MahjongTile marker = pendingDiscardTile != null ? pendingDiscardTile : MahjongTile.EAST;
        centerDisplay.setItemStack(TileVisuals.createTileItem(marker, pendingDiscardTile == null ? "Round " : "Claim "));
    }

    private void updateDiscards(World world, int seat, List<MahjongTile> discards) {
        List<ItemDisplay> list = seatDiscardDisplays.computeIfAbsent(seat, key -> new ArrayList<>());
        for (int i = 0; i < discards.size(); i++) {
            MahjongTile tile = discards.get(i);
            double row = i / 6;
            double col = i % 6;
            Location loc = seatPoint(seat, -0.8 + col * 0.25, 0.25 + row * 0.2, 1.03);
            ItemDisplay display = ensureDisplay(list, i, world, loc, yawForSeat(seat));
            display.teleport(loc);
            display.setItemStack(TileVisuals.createTileItem(tile, ""));
        }
        trimList(list, discards.size());
    }

    private void updateMelds(World world, int seat, List<MahjongMeld> melds) {
        List<ItemDisplay> list = seatMeldDisplays.computeIfAbsent(seat, key -> new ArrayList<>());
        int layoutIndex = 0;
        int displayIndex = 0;
        for (MahjongMeld meld : melds) {
            for (int j = 0; j < meld.tiles().size(); j++) {
                MahjongTile tile = meld.tiles().get(j);
                Location loc = seatPoint(seat, -1.2 + layoutIndex * 0.22, -0.35, 1.03);
                ItemDisplay display = ensureDisplay(list, displayIndex, world, loc, yawForSeat(seat));
                display.teleport(loc);
                display.setItemStack(TileVisuals.createTileItem(tile, ""));
                layoutIndex++;
                displayIndex++;
            }
            layoutIndex++;
        }
        trimList(list, displayIndex);
    }

    private ItemDisplay ensureDisplay(List<ItemDisplay> list, int index, World world, Location loc, float yaw) {
        if (index < list.size()) {
            ItemDisplay existing = list.get(index);
            if (existing != null && existing.isValid() && world.equals(existing.getWorld())) {
                return existing;
            }
            removeDisplay(existing);
        }
        ItemDisplay created = world.spawn(loc, ItemDisplay.class);
        applyFlatTransform(created, yaw);
        if (index < list.size()) {
            list.set(index, created);
        } else {
            list.add(created);
        }
        return created;
    }

    private void trimList(List<ItemDisplay> displays, int keep) {
        for (int i = displays.size() - 1; i >= keep; i--) {
            removeDisplay(displays.remove(i));
        }
    }

    private void trimUnusedSeats(Map<Integer, List<ItemDisplay>> source, int seats) {
        List<Integer> toRemove = new ArrayList<>();
        for (Map.Entry<Integer, List<ItemDisplay>> entry : source.entrySet()) {
            if (entry.getKey() >= seats) {
                removeDisplays(entry.getValue());
                toRemove.add(entry.getKey());
            }
        }
        for (Integer seat : toRemove) {
            source.remove(seat);
        }
    }

    private void removeDisplays(List<ItemDisplay> displays) {
        for (ItemDisplay display : displays) {
            removeDisplay(display);
        }
        displays.clear();
    }

    private void removeDisplay(Entity entity) {
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
    }

    private boolean displaysValid(World world) {
        if (centerDisplay == null || !centerDisplay.isValid() || !world.equals(centerDisplay.getWorld())) {
            return false;
        }
        return listWorldValid(seatDiscardDisplays, world) && listWorldValid(seatMeldDisplays, world);
    }

    private boolean listWorldValid(Map<Integer, List<ItemDisplay>> source, World world) {
        for (List<ItemDisplay> list : source.values()) {
            for (ItemDisplay display : list) {
                if (display == null || !display.isValid() || !world.equals(display.getWorld())) {
                    return false;
                }
            }
        }
        return true;
    }

    private int computeDisplayStateHash() {
        int hash = 1;
        hash = 31 * hash + turnOrder.hashCode();
        hash = 31 * hash + (pendingDiscardTile == null ? 0 : pendingDiscardTile.sortOrder());
        hash = 31 * hash + (pendingDiscarder == null ? 0 : pendingDiscarder.hashCode());
        for (UUID uuid : turnOrder) {
            MahjongPlayerState state = players.get(uuid);
            if (state == null) {
                continue;
            }
            hash = hashTiles(hash, state.discards());
            hash = hashMelds(hash, state.melds());
        }
        return hash;
    }

    private int hashTiles(int seed, List<MahjongTile> tiles) {
        int hash = 31 * seed + tiles.size();
        for (MahjongTile tile : tiles) {
            hash = 31 * hash + tile.sortOrder();
        }
        return hash;
    }

    private int hashMelds(int seed, List<MahjongMeld> melds) {
        int hash = 31 * seed + melds.size();
        for (MahjongMeld meld : melds) {
            hash = 31 * hash + meld.type().ordinal();
            hash = 31 * hash + (meld.open() ? 1 : 0);
            hash = hashTiles(hash, meld.tiles());
        }
        return hash;
    }

    private void ensureMainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("MahjongTable must run on main server thread");
        }
    }

    private void applyFlatTransform(ItemDisplay display, float yaw) {
        display.setRotation(yaw, 0);
        display.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f(),
                new Vector3f(0.45f, 0.45f, 0.45f),
                new AxisAngle4f()
        ));
    }

    private Location seatPoint(int seat, double xOffset, double zOffset, double y) {
        return switch (seat) {
            case 0 -> center.clone().add(xOffset, y, 2.0 + zOffset);
            case 1 -> center.clone().add(2.0 + zOffset, y, -xOffset);
            case 2 -> center.clone().add(-xOffset, y, -2.0 - zOffset);
            default -> center.clone().add(-2.0 - zOffset, y, xOffset);
        };
    }

    private float yawForSeat(int seat) {
        return switch (seat) {
            case 0 -> 180f;
            case 1 -> -90f;
            case 2 -> 0f;
            default -> 90f;
        };
    }

    private enum ClaimSource {
        NONE,
        DISCARD,
        CHANKAN_KAKAN,
        CHANKAN_ANKAN
    }

    private record PendingKakan(UUID owner, int meldIndex, MahjongTile tile) {
    }

    private static final class ClaimDeclaration {
        private final ClaimAction action;
        private final List<MahjongTile> tiles;

        private ClaimDeclaration(ClaimAction action, List<MahjongTile> tiles) {
            this.action = action;
            this.tiles = new ArrayList<>(tiles);
        }
    }
}
