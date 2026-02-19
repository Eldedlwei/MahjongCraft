package doublemoon.mahjongcraft.paper.integration;

import java.util.UUID;

public interface MoneyGateway {
    boolean enabled();

    String name();

    String format(double amount);

    boolean has(UUID uuid, double amount);

    boolean withdraw(UUID uuid, double amount);

    boolean deposit(UUID uuid, double amount);
}

