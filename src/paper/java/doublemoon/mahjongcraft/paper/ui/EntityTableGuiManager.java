package doublemoon.mahjongcraft.paper.ui;

import doublemoon.mahjongcraft.paper.game.ClaimAction;
import doublemoon.mahjongcraft.paper.game.MahjongTable;
import doublemoon.mahjongcraft.paper.game.MahjongTableManager;
import doublemoon.mahjongcraft.paper.game.MahjongTile;
import doublemoon.mahjongcraft.paper.game.TileVisuals;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public final class EntityTableGuiManager implements Listener {
    // Inspired by text-display experiment style: anchor by table seat, render layered displays, route clicks via Interaction.
    private static final Skin SKIN = Skin.defaultSkin();
    private final MahjongTableManager tableManager;
    private final Map<UUID, GuiSession> sessions = new HashMap<>();
    private final Map<UUID, ClickBinding> bindings = new HashMap<>();
    private final Map<UUID, Long> clickThrottle = new HashMap<>();
    private final Map<UUID, Integer> selectedHandIndex = new HashMap<>();
    private final Set<String> pendingTableRefresh = new LinkedHashSet<>();
    private boolean refreshQueued = false;

    public EntityTableGuiManager(MahjongTableManager tableManager) {
        this.tableManager = tableManager;
    }

    public void openFor(Player player, MahjongTable table) {
        ensureMainThread();
        close(player.getUniqueId());
        GuiSession session = new GuiSession(player.getUniqueId(), table.id());
        sessions.put(player.getUniqueId(), session);
        renderSession(player, table, session);
    }

    public void refreshTable(String tableId) {
        ensureMainThread();
        if (tableId == null || tableId.isBlank()) {
            return;
        }
        pendingTableRefresh.add(tableId.toUpperCase());
        if (refreshQueued) {
            return;
        }
        refreshQueued = true;
        tableManager.plugin().getServer().getScheduler().runTask(tableManager.plugin(), this::processRefreshQueue);
    }

    private void processRefreshQueue() {
        ensureMainThread();
        refreshQueued = false;
        if (pendingTableRefresh.isEmpty()) {
            return;
        }
        List<String> tableIds = new ArrayList<>(pendingTableRefresh);
        pendingTableRefresh.clear();
        for (String tableId : tableIds) {
            refreshTableNow(tableId);
        }
    }

    private void refreshTableNow(String tableId) {
        MahjongTable table = tableManager.getTableById(tableId);
        List<UUID> viewers = new ArrayList<>();
        for (GuiSession session : sessions.values()) {
            if (session.tableId.equalsIgnoreCase(tableId)) {
                viewers.add(session.viewer);
            }
        }
        for (UUID viewer : viewers) {
            Player player = Bukkit.getPlayer(viewer);
            if (player == null || !player.isOnline()) {
                close(viewer);
                continue;
            }
            if (table == null || table.players().get(viewer) == null) {
                close(viewer);
                continue;
            }
            GuiSession session = sessions.get(viewer);
            if (session != null) {
                renderSession(player, table, session);
            }
        }
    }

    public void close(UUID playerId) {
        ensureMainThread();
        GuiSession session = sessions.remove(playerId);
        if (session == null) {
            return;
        }
        clearSessionEntities(session);
        clickThrottle.remove(playerId);
        selectedHandIndex.remove(playerId);
    }

    public void closeByTable(String tableId) {
        ensureMainThread();
        List<UUID> viewers = new ArrayList<>();
        for (GuiSession session : sessions.values()) {
            if (session.tableId.equalsIgnoreCase(tableId)) {
                viewers.add(session.viewer);
            }
        }
        for (UUID viewer : viewers) {
            close(viewer);
        }
    }

    public void shutdown() {
        ensureMainThread();
        List<UUID> all = new ArrayList<>(sessions.keySet());
        for (UUID uuid : all) {
            close(uuid);
        }
        bindings.clear();
        clickThrottle.clear();
        selectedHandIndex.clear();
        pendingTableRefresh.clear();
        refreshQueued = false;
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        ClickBinding binding = bindings.get(event.getRightClicked().getUniqueId());
        if (binding == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!binding.owner.equals(player.getUniqueId())) {
            return;
        }
        long now = System.nanoTime();
        long last = clickThrottle.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 100_000_000L) {
            return;
        }
        clickThrottle.put(player.getUniqueId(), now);
        binding.action.run();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        close(event.getPlayer().getUniqueId());
    }

    private void renderSession(Player player, MahjongTable table, GuiSession session) {
        if (!player.isOnline()) {
            sessions.remove(player.getUniqueId());
            return;
        }
        World world = table.center().getWorld();
        if (world == null || !world.equals(player.getWorld())) {
            clearSessionEntities(session);
            session.lastRenderHash = Integer.MIN_VALUE;
            return;
        }
        int renderHash = computeRenderHash(table, player.getUniqueId(), world.getUID());
        if (renderHash == session.lastRenderHash && entitiesValid(session, world)) {
            return;
        }
        UUID viewer = player.getUniqueId();
        if (!table.canDiscardNow(viewer)) {
            selectedHandIndex.remove(viewer);
        } else {
            int selected = selectedHandIndex.getOrDefault(viewer, -1);
            int handSize = table.handSnapshot(viewer).size();
            if (selected < 1 || selected > handSize) {
                selectedHandIndex.remove(viewer);
            }
        }

        float yaw = table.seatYaw(player.getUniqueId());
        Location titleLoc = table.seatAnchor(player.getUniqueId(), 0, SKIN.infoZ, SKIN.titleY);
        Location statusLoc = table.seatAnchor(player.getUniqueId(), 0, SKIN.infoZ, SKIN.statusY);
        Location helpLoc = table.seatAnchor(player.getUniqueId(), 0, SKIN.infoZ, SKIN.helpY);

        Set<String> activeTextKeys = new HashSet<>();
        Set<String> activeItemKeys = new HashSet<>();
        Set<String> activeInteractionKeys = new HashSet<>();

        upsertText(session, activeTextKeys, "hud:title", world, titleLoc, titleComponent(table), SKIN.titleScale);
        upsertText(session, activeTextKeys, "hud:status", world, statusLoc, statusComponent(table), SKIN.statusScale);
        upsertText(session, activeTextKeys, "hud:help", world, helpLoc,
                Component.text("Select tile, click same tile again to discard", NamedTextColor.DARK_GRAY),
                SKIN.helpScale);

        spawnActionButtons(player, table, session, activeTextKeys, activeInteractionKeys);
        spawnHandTiles(player, table, session, yaw, activeItemKeys, activeInteractionKeys);
        pruneStaleEntities(session, activeTextKeys, activeItemKeys, activeInteractionKeys);
        session.lastRenderHash = renderHash;
    }

    private void spawnActionButtons(
            Player player,
            MahjongTable table,
            GuiSession session,
            Set<String> activeTextKeys,
            Set<String> activeInteractionKeys
    ) {
        UUID uuid = player.getUniqueId();
        Set<ClaimAction> options = table.claimOptions(uuid);
        if (!options.isEmpty()) {
            double x = SKIN.claimStartX;
            double z = SKIN.actionZ;
            int buttonIndex = 0;
            EnumSet<ClaimAction> claims = EnumSet.copyOf(options);
            if (claims.contains(ClaimAction.RON)) {
                x = spawnButton(session, table, uuid, x, z, SKIN.actionY, "RON", "claim:" + buttonIndex++,
                        activeTextKeys, activeInteractionKeys,
                        () -> runTableAction(player, table, () -> table.ron(uuid)));
            }
            if (claims.contains(ClaimAction.PON)) {
                x = spawnButton(session, table, uuid, x, z, SKIN.actionY, "PON", "claim:" + buttonIndex++,
                        activeTextKeys, activeInteractionKeys,
                        () -> runTableAction(player, table, () -> table.pon(uuid)));
            }
            if (claims.contains(ClaimAction.KAN)) {
                x = spawnButton(session, table, uuid, x, z, SKIN.actionY, "MINKAN", "claim:" + buttonIndex++,
                        activeTextKeys, activeInteractionKeys,
                        () -> runTableAction(player, table, () -> table.kan(uuid)));
            }
            if (claims.contains(ClaimAction.CHII)) {
                for (List<MahjongTile> choice : table.chiiChoices(uuid)) {
                    if (choice.size() < 2) {
                        continue;
                    }
                    MahjongTile a = choice.get(0);
                    MahjongTile b = choice.get(1);
                    x = spawnButton(session, table, uuid, x, z, SKIN.actionY, "CHII " + a.shortName() + "+" + b.shortName(), "claim:" + buttonIndex++,
                            activeTextKeys, activeInteractionKeys,
                            () -> runTableAction(player, table, () -> table.chii(uuid, a.shortName(), b.shortName())));
                }
            }
            if (claims.contains(ClaimAction.PASS)) {
                spawnButton(session, table, uuid, x, z, SKIN.actionY, "PASS", "claim:" + buttonIndex,
                        activeTextKeys, activeInteractionKeys,
                        () -> runTableAction(player, table, () -> table.pass(uuid)));
            }
            return;
        }

        double x = SKIN.selfStartX;
        double z = SKIN.actionZ;
        int buttonIndex = 0;
        if (table.canRiichiNow(uuid)) {
            x = spawnButton(session, table, uuid, x, z, SKIN.actionY, "RIICHI", "self:" + buttonIndex++,
                    activeTextKeys, activeInteractionKeys,
                    () -> runTableAction(player, table, () -> table.riichi(uuid)));
        }
        if (table.canTsumoNow(uuid)) {
            x = spawnButton(session, table, uuid, x, z, SKIN.actionY, "TSUMO", "self:" + buttonIndex++,
                    activeTextKeys, activeInteractionKeys,
                    () -> runTableAction(player, table, () -> table.tsumo(uuid)));
        }
        if (table.canSelfKanNow(uuid)) {
            x = spawnButton(session, table, uuid, x, z, SKIN.actionY, "KAN", "self:" + buttonIndex++,
                    activeTextKeys, activeInteractionKeys,
                    () -> runTableAction(player, table, () -> table.kan(uuid)));
        }
        if (table.canKyuushuNow(uuid)) {
            spawnButton(session, table, uuid, x, z, SKIN.actionY, "KYUUSHU", "self:" + buttonIndex,
                    activeTextKeys, activeInteractionKeys,
                    () -> runTableAction(player, table, () -> table.kyuushuKyuuhai(uuid)));
        }
    }

    private double spawnButton(
            GuiSession session,
            MahjongTable table,
            UUID owner,
            double x,
            double z,
            double y,
            String text,
            String keySuffix,
            Set<String> activeTextKeys,
            Set<String> activeInteractionKeys,
            Runnable action
    ) {
        World world = table.center().getWorld();
        if (world == null) {
            return x + 0.46;
        }
        Location textLoc = table.seatAnchor(owner, x, z, y);
        Location hitLoc = table.seatAnchor(owner, x, z, y - 0.035);
        String textKey = "button:text:" + keySuffix;
        String hitKey = "button:hit:" + keySuffix;
        upsertText(session, activeTextKeys, textKey, world, textLoc, buttonComponent(text), SKIN.buttonScale);
        upsertInteraction(session, activeInteractionKeys, hitKey, world, hitLoc, SKIN.buttonHitWidth, SKIN.buttonHitHeight, owner, action);
        return x + SKIN.buttonStep;
    }

    private void spawnHandTiles(
            Player player,
            MahjongTable table,
            GuiSession session,
            float yaw,
            Set<String> activeItemKeys,
            Set<String> activeInteractionKeys
    ) {
        List<MahjongTile> hand = table.handSnapshot(player.getUniqueId());
        if (hand.isEmpty()) {
            return;
        }
        int selectedIndex = selectedHandIndex.getOrDefault(player.getUniqueId(), -1);
        int count = hand.size();
        double step = SKIN.handStep;
        double start = -((count - 1) * step) / 2.0;
        boolean hasTakenTileGap = table.canDiscardNow(player.getUniqueId()) && count % 3 == 2;
        World world = table.center().getWorld();
        if (world == null) {
            return;
        }
        for (int i = 0; i < hand.size(); i++) {
            MahjongTile tile = hand.get(i);
            int handIndex = i + 1;
            double x = start + i * step + (hasTakenTileGap && i == hand.size() - 1 ? SKIN.takenTileExtraGap : 0.0);
            boolean selected = handIndex == selectedIndex;
            float scale = selected ? SKIN.handScaleSelected : SKIN.handScale;
            Location tileLoc = table.seatAnchor(player.getUniqueId(), x, SKIN.handZ, selected ? SKIN.handYSelected : SKIN.handY);
            String itemKey = "hand:item:" + i;
            String itemSignature = tile.shortName() + "|" + handIndex + "|" + selected;
            ItemStack item = TileVisuals.createTileItem(tile, String.valueOf(handIndex));
            upsertItem(session, activeItemKeys, itemKey, world, tileLoc, yaw, scale, item, itemSignature);
            Location hitLoc = table.seatAnchor(player.getUniqueId(), x, SKIN.handZ, SKIN.handHitY);
            String hitKey = "hand:hit:" + i;
            upsertInteraction(session, activeInteractionKeys, hitKey, world, hitLoc, SKIN.tileHitWidth, SKIN.tileHitHeight, player.getUniqueId(),
                    () -> onHandTileClick(player, table, handIndex));
        }
    }

    private Component buttonComponent(String text) {
        String capsule = "[" + text + "]";
        String upper = text.toUpperCase();
        if (upper.startsWith("RON")) {
            return Component.text(capsule, NamedTextColor.RED).decorate(TextDecoration.BOLD);
        }
        if (upper.startsWith("TSUMO")) {
            return Component.text(capsule, NamedTextColor.AQUA).decorate(TextDecoration.BOLD);
        }
        if (upper.startsWith("RIICHI")) {
            return Component.text(capsule, NamedTextColor.GREEN).decorate(TextDecoration.BOLD);
        }
        if (upper.startsWith("PASS")) {
            return Component.text(capsule, NamedTextColor.GRAY);
        }
        if (upper.startsWith("CHII")) {
            return Component.text(capsule, NamedTextColor.BLUE);
        }
        return Component.text(capsule, NamedTextColor.YELLOW);
    }

    private Component titleComponent(MahjongTable table) {
        return Component.text()
                .append(Component.text("Mahjong Soul Style", NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
                .append(Component.text("  ", NamedTextColor.GRAY))
                .append(Component.text("#" + table.id(), NamedTextColor.WHITE))
                .build();
    }

    private Component statusComponent(MahjongTable table) {
        return Component.text()
                .append(Component.text("TABLE ", NamedTextColor.DARK_GRAY))
                .append(Component.text(table.id(), NamedTextColor.YELLOW))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text(table.status(), NamedTextColor.WHITE))
                .build();
    }

    private void onHandTileClick(Player player, MahjongTable table, int handIndex) {
        UUID uuid = player.getUniqueId();
        if (!table.canDiscardNow(uuid)) {
            selectedHandIndex.remove(uuid);
            refreshTable(table.id());
            return;
        }
        int current = selectedHandIndex.getOrDefault(uuid, -1);
        if (current != handIndex) {
            selectedHandIndex.put(uuid, handIndex);
            refreshTable(table.id());
            return;
        }
        selectedHandIndex.remove(uuid);
        runTableAction(player, table, () -> table.discard(uuid, handIndex));
    }

    private void runTableAction(Player player, MahjongTable table, Supplier<String> action) {
        if (!player.isOnline()) {
            close(player.getUniqueId());
            return;
        }
        String result = action.get();
        table.broadcast(result);
        if (table.started()) {
            String botResult = table.runBots();
            if (!botResult.isBlank()) {
                table.broadcast(botResult);
            }
            table.broadcast(table.status());
            refreshTable(table.id());
        } else {
            close(player.getUniqueId());
        }
    }

    private void upsertText(
            GuiSession session,
            Set<String> activeTextKeys,
            String key,
            World world,
            Location location,
            Component text,
            float scale
    ) {
        activeTextKeys.add(key);
        TextDisplay display = session.textDisplays.get(key);
        if (display == null || !display.isValid() || !world.equals(display.getWorld())) {
            removeText(session, key);
            display = world.spawn(location, TextDisplay.class);
            configureBaseEntity(display);
            display.setBillboard(Display.Billboard.CENTER);
            display.setShadowed(true);
            display.setSeeThrough(true);
            display.setDefaultBackground(false);
            session.textDisplays.put(key, display);
            session.textStates.remove(key);
        } else if (hasMeaningfulMove(display.getLocation(), location)) {
            display.teleport(location);
        }

        TextState previous = session.textStates.get(key);
        if (previous == null || !previous.text.equals(text)) {
            display.text(text);
        }
        if (previous == null || Math.abs(previous.scale - scale) > 0.0001f) {
            display.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(),
                    new Vector3f(scale, scale, scale),
                    new AxisAngle4f()
            ));
        }
        session.textStates.put(key, new TextState(text, scale));
    }

    private void upsertItem(
            GuiSession session,
            Set<String> activeItemKeys,
            String key,
            World world,
            Location location,
            float yaw,
            float scale,
            ItemStack itemStack,
            String signature
    ) {
        activeItemKeys.add(key);
        ItemDisplay display = session.itemDisplays.get(key);
        if (display == null || !display.isValid() || !world.equals(display.getWorld())) {
            removeItem(session, key);
            display = world.spawn(location, ItemDisplay.class);
            configureBaseEntity(display);
            session.itemDisplays.put(key, display);
            session.itemStates.remove(key);
        } else if (hasMeaningfulMove(display.getLocation(), location)) {
            display.teleport(location);
        }

        ItemState previous = session.itemStates.get(key);
        if (previous == null || !previous.signature.equals(signature)) {
            display.setItemStack(itemStack);
        }
        if (previous == null || Math.abs(previous.yaw - yaw) > 0.01f) {
            display.setRotation(yaw, 0);
        }
        if (previous == null || Math.abs(previous.scale - scale) > 0.0001f) {
            display.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(),
                    new Vector3f(scale, scale, scale),
                    new AxisAngle4f()
            ));
        }
        session.itemStates.put(key, new ItemState(signature, yaw, scale));
    }

    private void upsertInteraction(
            GuiSession session,
            Set<String> activeInteractionKeys,
            String key,
            World world,
            Location location,
            float width,
            float height,
            UUID owner,
            Runnable action
    ) {
        activeInteractionKeys.add(key);
        Interaction interaction = session.interactions.get(key);
        if (interaction == null || !interaction.isValid() || !world.equals(interaction.getWorld())) {
            removeInteraction(session, key);
            interaction = world.spawn(location, Interaction.class);
            configureBaseEntity(interaction);
            session.interactions.put(key, interaction);
            session.interactionStates.remove(key);
        } else if (hasMeaningfulMove(interaction.getLocation(), location)) {
            interaction.teleport(location);
        }

        InteractionState previous = session.interactionStates.get(key);
        if (previous == null || Math.abs(previous.width - width) > 0.0001f) {
            interaction.setInteractionWidth(width);
        }
        if (previous == null || Math.abs(previous.height - height) > 0.0001f) {
            interaction.setInteractionHeight(height);
        }
        session.interactionStates.put(key, new InteractionState(width, height));
        bindings.put(interaction.getUniqueId(), new ClickBinding(owner, action));
    }

    private void pruneStaleEntities(
            GuiSession session,
            Set<String> activeTextKeys,
            Set<String> activeItemKeys,
            Set<String> activeInteractionKeys
    ) {
        pruneTexts(session, activeTextKeys);
        pruneItems(session, activeItemKeys);
        pruneInteractions(session, activeInteractionKeys);
    }

    private void pruneTexts(GuiSession session, Set<String> activeKeys) {
        List<String> keys = new ArrayList<>(session.textDisplays.keySet());
        for (String key : keys) {
            if (!activeKeys.contains(key)) {
                removeText(session, key);
            }
        }
    }

    private void pruneItems(GuiSession session, Set<String> activeKeys) {
        List<String> keys = new ArrayList<>(session.itemDisplays.keySet());
        for (String key : keys) {
            if (!activeKeys.contains(key)) {
                removeItem(session, key);
            }
        }
    }

    private void pruneInteractions(GuiSession session, Set<String> activeKeys) {
        List<String> keys = new ArrayList<>(session.interactions.keySet());
        for (String key : keys) {
            if (!activeKeys.contains(key)) {
                removeInteraction(session, key);
            }
        }
    }

    private boolean hasMeaningfulMove(Location from, Location to) {
        if (from == null || to == null) {
            return true;
        }
        if (from.getWorld() == null || to.getWorld() == null) {
            return true;
        }
        if (!from.getWorld().equals(to.getWorld())) {
            return true;
        }
        return from.distanceSquared(to) > 0.0001;
    }

    private void removeText(GuiSession session, String key) {
        TextDisplay display = session.textDisplays.remove(key);
        if (display != null && display.isValid()) {
            display.remove();
        }
        session.textStates.remove(key);
    }

    private void removeItem(GuiSession session, String key) {
        ItemDisplay display = session.itemDisplays.remove(key);
        if (display != null && display.isValid()) {
            display.remove();
        }
        session.itemStates.remove(key);
    }

    private void removeInteraction(GuiSession session, String key) {
        Interaction interaction = session.interactions.remove(key);
        if (interaction != null) {
            bindings.remove(interaction.getUniqueId());
            if (interaction.isValid()) {
                interaction.remove();
            }
        }
        session.interactionStates.remove(key);
    }

    private void clearSessionEntities(GuiSession session) {
        for (String key : new ArrayList<>(session.textDisplays.keySet())) {
            removeText(session, key);
        }
        for (String key : new ArrayList<>(session.itemDisplays.keySet())) {
            removeItem(session, key);
        }
        for (String key : new ArrayList<>(session.interactions.keySet())) {
            removeInteraction(session, key);
        }
        session.lastRenderHash = Integer.MIN_VALUE;
    }

    private int computeRenderHash(MahjongTable table, UUID viewer, UUID worldId) {
        int hash = 1;
        hash = 31 * hash + table.id().hashCode();
        hash = 31 * hash + worldId.hashCode();
        hash = 31 * hash + table.status().hashCode();
        hash = 31 * hash + table.claimView(viewer).hashCode();
        hash = 31 * hash + selectedHandIndex.getOrDefault(viewer, -1);
        List<MahjongTile> hand = table.handSnapshot(viewer);
        hash = 31 * hash + hand.size();
        for (MahjongTile tile : hand) {
            hash = 31 * hash + tile.sortOrder();
        }
        return hash;
    }

    private boolean entitiesValid(GuiSession session, World world) {
        for (TextDisplay display : session.textDisplays.values()) {
            if (display == null || !display.isValid() || !world.equals(display.getWorld())) {
                return false;
            }
        }
        for (ItemDisplay display : session.itemDisplays.values()) {
            if (display == null || !display.isValid() || !world.equals(display.getWorld())) {
                return false;
            }
        }
        for (Interaction interaction : session.interactions.values()) {
            if (interaction == null || !interaction.isValid() || !world.equals(interaction.getWorld())) {
                return false;
            }
        }
        return true;
    }

    private void configureBaseEntity(Entity entity) {
        entity.setPersistent(false);
        entity.setInvulnerable(true);
        entity.setGravity(false);
        entity.setSilent(true);
    }

    private void ensureMainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("EntityTableGuiManager must run on main server thread");
        }
    }

    private static final class GuiSession {
        private final UUID viewer;
        private final String tableId;
        private final Map<String, TextDisplay> textDisplays = new HashMap<>();
        private final Map<String, ItemDisplay> itemDisplays = new HashMap<>();
        private final Map<String, Interaction> interactions = new HashMap<>();
        private final Map<String, TextState> textStates = new HashMap<>();
        private final Map<String, ItemState> itemStates = new HashMap<>();
        private final Map<String, InteractionState> interactionStates = new HashMap<>();
        private int lastRenderHash = Integer.MIN_VALUE;

        private GuiSession(UUID viewer, String tableId) {
            this.viewer = viewer;
            this.tableId = tableId;
        }
    }

    private record TextState(Component text, float scale) {
    }

    private record ItemState(String signature, float yaw, float scale) {
    }

    private record InteractionState(float width, float height) {
    }

    private record ClickBinding(UUID owner, Runnable action) {
    }

    private record Skin(
            double infoZ,
            double actionZ,
            double handZ,
            double titleY,
            double statusY,
            double helpY,
            double actionY,
            double handY,
            double handYSelected,
            double handHitY,
            double selfStartX,
            double claimStartX,
            double buttonStep,
            double handStep,
            double takenTileExtraGap,
            float titleScale,
            float statusScale,
            float helpScale,
            float buttonScale,
            float handScale,
            float handScaleSelected,
            float buttonHitWidth,
            float buttonHitHeight,
            float tileHitWidth,
            float tileHitHeight
    ) {
        private static Skin defaultSkin() {
            return new Skin(
                    1.36,
                    1.68,
                    1.98,
                    1.42,
                    1.24,
                    1.10,
                    1.04,
                    0.92,
                    1.00,
                    0.86,
                    -0.94,
                    -1.10,
                    0.50,
                    0.205,
                    0.13,
                    0.90f,
                    0.62f,
                    0.56f,
                    0.72f,
                    0.40f,
                    0.44f,
                    0.46f,
                    0.29f,
                    0.20f,
                    0.30f
            );
        }
    }
}
