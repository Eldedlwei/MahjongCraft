package doublemoon.mahjongcraft.paper;

import doublemoon.mahjongcraft.paper.command.MahjongCommand;
import doublemoon.mahjongcraft.paper.game.GameLength;
import doublemoon.mahjongcraft.paper.game.MahjongTableManager;
import doublemoon.mahjongcraft.paper.game.TableRules;
import doublemoon.mahjongcraft.paper.integration.MoneyGateway;
import doublemoon.mahjongcraft.paper.integration.PacketEventsBootstrap;
import doublemoon.mahjongcraft.paper.integration.VaultMoneyGateway;
import doublemoon.mahjongcraft.paper.listener.HandInventoryListener;
import doublemoon.mahjongcraft.paper.listener.PlayerConnectionListener;
import doublemoon.mahjongcraft.paper.ui.EntityTableGuiManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class MahjongCraftPaperPlugin extends JavaPlugin {

    private MahjongTableManager tableManager;
    private MoneyGateway moneyGateway;
    private boolean packetEventsInitialized;
    private EntityTableGuiManager entityGuiManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.packetEventsInitialized = PacketEventsBootstrap.init(this);
        this.moneyGateway = VaultMoneyGateway.create(this);
        TableRules rules = loadRules();
        this.tableManager = new MahjongTableManager(this, moneyGateway, rules);
        this.entityGuiManager = new EntityTableGuiManager(tableManager);
        MahjongCommand command = new MahjongCommand(this, tableManager);
        PluginCommand mahjong = getCommand("mahjong");
        if (mahjong == null) {
            getLogger().severe("Command mahjong is missing in plugin.yml");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        mahjong.setExecutor(command);
        mahjong.setTabCompleter(command);
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(tableManager), this);
        getServer().getPluginManager().registerEvents(new HandInventoryListener(this, tableManager), this);
        getServer().getPluginManager().registerEvents(entityGuiManager, this);
        getLogger().info(
                "MahjongCraft Paper enabled. Economy=" + moneyGateway.name()
                        + " PacketEvents=" + packetEventsInitialized
                        + " Rules[length=" + rules.gameLength()
                        + ", startingPoints=" + rules.startingPoints()
                        + ", minPointsToWin=" + rules.minPointsToWin()
                        + ", minimumHan=" + rules.minimumHan() + "]"
        );
    }

    @Override
    public void onDisable() {
        if (tableManager != null) {
            tableManager.shutdown();
        }
        if (entityGuiManager != null) {
            entityGuiManager.shutdown();
        }
        PacketEventsBootstrap.shutdown(this);
    }

    public MoneyGateway moneyGateway() {
        return moneyGateway;
    }

    public EntityTableGuiManager entityGuiManager() {
        return entityGuiManager;
    }

    private TableRules loadRules() {
        String rawLength = getConfig().getString("rules.length", "TWO_WIND");
        GameLength length = parseLength(rawLength);
        int startingPoints = clamp(getConfig().getInt("rules.starting_points", 25000), 100, 200000);
        int minPointsToWin = clamp(getConfig().getInt("rules.min_points_to_win", 30000), 100, 200000);
        int minimumHan = clamp(getConfig().getInt("rules.minimum_han", 1), 1, 13);
        if (minPointsToWin < startingPoints) {
            minPointsToWin = startingPoints;
        }
        return new TableRules(length, startingPoints, minPointsToWin, minimumHan);
    }

    private GameLength parseLength(String raw) {
        if (raw == null) {
            return GameLength.TWO_WIND;
        }
        try {
            return GameLength.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            getLogger().warning("Invalid rules.length: " + raw + ", fallback TWO_WIND");
            return GameLength.TWO_WIND;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
