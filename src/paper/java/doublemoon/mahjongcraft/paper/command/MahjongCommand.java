package doublemoon.mahjongcraft.paper.command;

import doublemoon.mahjongcraft.paper.MahjongCraftPaperPlugin;
import doublemoon.mahjongcraft.paper.game.MahjongTable;
import doublemoon.mahjongcraft.paper.game.MahjongTableManager;
import doublemoon.mahjongcraft.paper.game.MahjongTile;
import doublemoon.mahjongcraft.paper.game.TileVisuals;
import doublemoon.mahjongcraft.paper.message.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class MahjongCommand implements TabExecutor {
    private static final List<String> SUBCOMMANDS = List.of(
            "create", "join", "leave", "mode", "start", "hand", "claim", "gui", "discard", "pay", "item",
            "riichi", "tsumo", "ron", "kyuushu", "pon", "kan", "chii", "pass", "status"
    );
    private static final List<String> MODE_OPTIONS = List.of("bot", "human");
    private static final List<String> CHII_TILES = List.of(
            "1m", "2m", "3m", "4m", "5m", "5mr", "6m", "7m", "8m", "9m",
            "1p", "2p", "3p", "4p", "5p", "5pr", "6p", "7p", "8p", "9p",
            "1s", "2s", "3s", "4s", "5s", "5sr", "6s", "7s", "8s", "9s"
    );
    private static final List<String> KAN_TILES = List.of(
            "1m", "2m", "3m", "4m", "5m", "5mr", "6m", "7m", "8m", "9m",
            "1p", "2p", "3p", "4p", "5p", "5pr", "6p", "7p", "8p", "9p",
            "1s", "2s", "3s", "4s", "5s", "5sr", "6s", "7s", "8s", "9s",
            "east", "south", "west", "north", "white", "green", "red"
    );
    private static final List<String> TILE_OPTIONS = buildTileOptions();

    private final MahjongCraftPaperPlugin plugin;
    private final MahjongTableManager manager;

    public MahjongCommand(MahjongCraftPaperPlugin plugin, MahjongTableManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, "cmd.only_player");
            return true;
        }
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create", "creat" -> handleCreate(player);
            case "join" -> {
                if (args.length < 2) {
                    MessageUtil.send(player, "cmd.usage_join");
                } else {
                    handleJoin(player, args[1]);
                }
            }
            case "leave" -> handleLeave(player);
            case "mode" -> {
                if (args.length < 2) {
                    MessageUtil.sendRaw(player, "Usage: /mahjong mode <bot|human>");
                } else {
                    handleMode(player, args[1]);
                }
            }
            case "start" -> handleStart(player);
            case "hand" -> handleHand(player);
            case "claim" -> handleClaim(player);
            case "gui" -> handleGui(player);
            case "status" -> handleStatus(player);
            case "discard" -> {
                if (args.length < 2) {
                    MessageUtil.send(player, "cmd.usage_discard");
                } else {
                    handleDiscard(player, args[1]);
                }
            }
            case "pay" -> {
                if (args.length < 2) {
                    MessageUtil.send(player, "cmd.usage_pay");
                } else {
                    handlePay(player, args[1]);
                }
            }
            case "item" -> {
                if (args.length < 2) {
                    MessageUtil.send(player, "cmd.usage_item");
                } else {
                    String amount = args.length >= 3 ? args[2] : "1";
                    handleItem(player, args[1], amount);
                }
            }
            case "riichi" -> handleSimpleResult(player, table -> table.riichi(player.getUniqueId()));
            case "tsumo" -> handleSimpleResult(player, table -> table.tsumo(player.getUniqueId()));
            case "ron" -> handleSimpleResult(player, table -> table.ron(player.getUniqueId()));
            case "kyuushu" -> handleSimpleResult(player, table -> table.kyuushuKyuuhai(player.getUniqueId()));
            case "pon" -> handleSimpleResult(player, table -> table.pon(player.getUniqueId()));
            case "kan" -> {
                if (args.length >= 2) {
                    handleSimpleResult(player, table -> table.kan(player.getUniqueId(), args[1]));
                } else {
                    handleSimpleResult(player, table -> table.kan(player.getUniqueId()));
                }
            }
            case "chii" -> {
                if (args.length < 3) {
                    MessageUtil.send(player, "cmd.usage_chii");
                } else {
                    handleSimpleResult(player, table -> table.chii(player.getUniqueId(), args[1], args[2]));
                }
            }
            case "pass" -> handleSimpleResult(player, table -> table.pass(player.getUniqueId()));
            default -> sendHelp(player);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return switch (args.length) {
            case 1 -> filterByPrefix(SUBCOMMANDS, args[0]);
            case 2 -> switch (args[0].toLowerCase(Locale.ROOT)) {
                case "mode" -> filterByPrefix(MODE_OPTIONS, args[1]);
                case "discard" -> filterByPrefix(discardOptions(sender), args[1]);
                case "kan" -> filterByPrefix(KAN_TILES, args[1]);
                case "chii" -> filterByPrefix(CHII_TILES, args[1]);
                case "item" -> filterByPrefix(TILE_OPTIONS, args[1]);
                default -> List.of();
            };
            default -> switch (args[0].toLowerCase(Locale.ROOT)) {
                case "chii" -> filterByPrefix(CHII_TILES, args[args.length - 1]);
                case "item" -> List.of("1", "16", "64");
                default -> List.of();
            };
        };
    }

    private List<String> discardOptions(CommandSender sender) {
        if (sender instanceof Player player) {
            MahjongTable table = manager.getTableByPlayer(player.getUniqueId());
            if (table != null) {
                int size = Objects.requireNonNull(table.players().get(player.getUniqueId())).hand().size();
                List<String> list = new ArrayList<>(size);
                for (int i = 1; i <= size; i++) {
                    list.add(String.valueOf(i));
                }
                return list;
            }
        }
        return List.of();
    }

    private List<String> filterByPrefix(List<String> options, String prefix) {
        if (options.isEmpty()) {
            return options;
        }
        if (prefix == null || prefix.isBlank()) {
            return options;
        }
        String needle = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(needle)) {
                out.add(option);
            }
        }
        return out;
    }

    private void handleCreate(Player player) {
        MahjongTable table = manager.createTable(player);
        if (table == null) {
            MessageUtil.send(player, "cmd.err_already_in_table");
            return;
        }
        MessageUtil.send(player, "cmd.ok_table_created", MessageUtil.args("id", table.id()));
        plugin.entityGuiManager().openFor(player, table);
        plugin.entityGuiManager().refreshTable(table.id());
        MessageUtil.send(player, "cmd.tip_gui_retry");
    }

    private void handleJoin(Player player, String id) {
        boolean ok = manager.join(player, id);
        if (!ok) {
            MessageUtil.send(player, "cmd.err_join_failed");
            return;
        }
        MahjongTable table = manager.getTableByPlayer(player.getUniqueId());
        if (table != null) {
            table.broadcastKey("table.player_joined", MessageUtil.args("player", player.getName()));
            table.broadcast(table.status());
            plugin.entityGuiManager().openFor(player, table);
            plugin.entityGuiManager().refreshTable(table.id());
        }
    }

    private void handleLeave(Player player) {
        MahjongTable table = manager.getTableByPlayer(player.getUniqueId());
        if (!manager.leave(player.getUniqueId())) {
            MessageUtil.send(player, "cmd.err_not_in_table");
        } else {
            MessageUtil.send(player, "cmd.ok_left_table");
            plugin.entityGuiManager().close(player.getUniqueId());
            if (table != null) {
                plugin.entityGuiManager().closeByTable(table.id());
            }
        }
    }

    private void handleStart(Player player) {
        MahjongTable table = manager.getTableByPlayer(player.getUniqueId());
        if (table == null) {
            MessageUtil.send(player, "cmd.err_not_in_table");
            return;
        }
        if (!table.host().equals(player.getUniqueId())) {
            MessageUtil.send(player, "cmd.err_only_host");
            return;
        }
        String result = table.start();
        table.broadcast(result);
        table.players().keySet().forEach(uuid -> {
            Player member = Bukkit.getPlayer(uuid);
            if (member != null && member.isOnline()) {
                plugin.entityGuiManager().openFor(member, table);
            }
        });
        runBotsAndSync(table);
    }

    private void handleMode(Player player, String rawMode) {
        MahjongTable table = manager.getTableByPlayer(player.getUniqueId());
        if (table == null) {
            MessageUtil.send(player, "cmd.err_not_in_table");
            return;
        }
        MahjongTable.MatchMode mode = switch (rawMode.toLowerCase(Locale.ROOT)) {
            case "bot", "bots", "ai" -> MahjongTable.MatchMode.BOT_FILL;
            case "human", "pvp" -> MahjongTable.MatchMode.HUMAN_ONLY;
            default -> null;
        };
        if (mode == null) {
            MessageUtil.sendRaw(player, "Unknown mode. Use: bot or human.");
            return;
        }
        String result = table.setMatchMode(player.getUniqueId(), mode);
        table.broadcast(result);
        table.broadcast(table.status());
        plugin.entityGuiManager().refreshTable(table.id());
    }

    private void handleHand(Player player) {
        MahjongTable table = manager.getTableByPlayer(player.getUniqueId());
        if (table == null) {
            MessageUtil.send(player, "cmd.err_not_in_table");
            return;
        }
        MessageUtil.sendRaw(player, table.handView(player.getUniqueId()));
    }

    private void handleGui(Player player) {
        MahjongTable table = manager.getTableByPlayer(player.getUniqueId());
        if (table == null) {
            MessageUtil.send(player, "cmd.err_not_in_table");
            return;
        }
        plugin.entityGuiManager().openFor(player, table);
    }

    private void handleClaim(Player player) {
        MahjongTable table = manager.getTableByPlayer(player.getUniqueId());
        if (table == null) {
            MessageUtil.send(player, "cmd.err_not_in_table");
            return;
        }
        MessageUtil.sendRaw(player, table.claimView(player.getUniqueId()));
    }

    private void handleDiscard(Player player, String rawIndex) {
        MahjongTable table = manager.getTableByPlayer(player.getUniqueId());
        if (table == null) {
            MessageUtil.send(player, "cmd.err_not_in_table");
            return;
        }
        int index;
        try {
            index = Integer.parseInt(rawIndex);
        } catch (NumberFormatException e) {
            MessageUtil.send(player, "cmd.err_number_index");
            return;
        }
        String result = table.discard(player.getUniqueId(), index);
        table.broadcast(result);
        runBotsAndSync(table);
    }

    private void handleStatus(Player player) {
        MahjongTable table = manager.getTableByPlayer(player.getUniqueId());
        if (table == null) {
            MessageUtil.send(player, "cmd.err_not_in_table");
            return;
        }
        MessageUtil.sendRaw(player, table.status());
    }

    private void handlePay(Player player, String rawAmount) {
        MahjongTable table = manager.getTableByPlayer(player.getUniqueId());
        if (table == null) {
            MessageUtil.send(player, "cmd.err_not_in_table");
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(rawAmount);
        } catch (NumberFormatException e) {
            MessageUtil.send(player, "cmd.err_number_amount");
            return;
        }
        String result = table.pay(player.getUniqueId(), amount);
        table.broadcast(result);
        table.broadcast(table.status());
        plugin.entityGuiManager().refreshTable(table.id());
    }

    private void handleItem(Player player, String rawTile, String rawAmount) {
        MahjongTile tile = MahjongTile.parse(rawTile);
        if (tile == null) {
            MessageUtil.send(player, "cmd.err_unknown_tile");
            return;
        }
        int amount;
        try {
            amount = Integer.parseInt(rawAmount);
        } catch (NumberFormatException e) {
            MessageUtil.send(player, "cmd.err_number_amount");
            return;
        }
        if (amount < 1) {
            MessageUtil.send(player, "cmd.err_number_amount");
            return;
        }
        int remaining = amount;
        while (remaining > 0) {
            int batch = Math.min(remaining, 64);
            var stack = TileVisuals.createTileItem(tile, "");
            stack.setAmount(batch);
            player.getInventory().addItem(stack);
            remaining -= batch;
        }
        MessageUtil.send(player, "cmd.ok_item_given");
    }

    private void handleSimpleResult(Player player, TableAction action) {
        MahjongTable table = manager.getTableByPlayer(player.getUniqueId());
        if (table == null) {
            MessageUtil.send(player, "cmd.err_not_in_table");
            return;
        }
        String result = action.apply(table);
        table.broadcast(result);
        runBotsAndSync(table);
    }

    private void runBotsAndSync(MahjongTable table) {
        if (table.started()) {
            String botResult = table.runBots();
            if (!botResult.isBlank()) {
                table.broadcast(botResult);
            }
            table.broadcast(table.status());
            plugin.entityGuiManager().refreshTable(table.id());
        }
    }

    private void sendHelp(Player player) {
        for (int i = 1; i <= 19; i++) {
            MessageUtil.send(player, "cmd.help." + i);
        }
    }

    @FunctionalInterface
    private interface TableAction {
        String apply(MahjongTable table);
    }

    private static List<String> buildTileOptions() {
        List<String> options = new ArrayList<>();
        for (MahjongTile tile : MahjongTile.values()) {
            options.add(tile.shortName());
        }
        return options;
    }
}
