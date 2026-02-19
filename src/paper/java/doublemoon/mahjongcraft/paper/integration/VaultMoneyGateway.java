package doublemoon.mahjongcraft.paper.integration;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public final class VaultMoneyGateway implements MoneyGateway {
    private final Economy economy;

    private VaultMoneyGateway(Economy economy) {
        this.economy = economy;
    }

    public static MoneyGateway create(JavaPlugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("Vault not found. Economy features disabled.");
            return new NoopMoneyGateway();
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null || rsp.getProvider() == null) {
            plugin.getLogger().warning("No Vault economy provider found. Economy features disabled.");
            return new NoopMoneyGateway();
        }
        plugin.getLogger().info("Vault economy hooked: " + rsp.getProvider().getName());
        return new VaultMoneyGateway(rsp.getProvider());
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public String name() {
        return economy.getName();
    }

    @Override
    public String format(double amount) {
        return economy.format(amount);
    }

    @Override
    public boolean has(UUID uuid, double amount) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        return economy.has(player, amount);
    }

    @Override
    public boolean withdraw(UUID uuid, double amount) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response.transactionSuccess();
    }

    @Override
    public boolean deposit(UUID uuid, double amount) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        EconomyResponse response = economy.depositPlayer(player, amount);
        return response.transactionSuccess();
    }
}

