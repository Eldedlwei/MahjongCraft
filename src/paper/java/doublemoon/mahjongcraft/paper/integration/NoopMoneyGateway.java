package doublemoon.mahjongcraft.paper.integration;

import java.util.UUID;

public final class NoopMoneyGateway implements MoneyGateway {
    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public String name() {
        return "none";
    }

    @Override
    public String format(double amount) {
        return String.format("%.2f", amount);
    }

    @Override
    public boolean has(UUID uuid, double amount) {
        return false;
    }

    @Override
    public boolean withdraw(UUID uuid, double amount) {
        return false;
    }

    @Override
    public boolean deposit(UUID uuid, double amount) {
        return false;
    }
}

