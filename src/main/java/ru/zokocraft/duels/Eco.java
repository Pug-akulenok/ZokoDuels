package ru.zokocraft.duels;

import org.bukkit.OfflinePlayer;

/** Обёртка над экономикой, чтобы плагин работал и без Vault. */
public interface Eco {
    boolean has(OfflinePlayer p, double amount);

    void withdraw(OfflinePlayer p, double amount);

    void deposit(OfflinePlayer p, double amount);

    String format(double amount);
}
