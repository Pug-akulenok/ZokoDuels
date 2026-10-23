package ru.zokocraft.duels;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

final class VaultEco implements Eco {
    private final Economy economy;

    private VaultEco(Economy economy) {
        this.economy = economy;
    }

    static Eco create(JavaPlugin plugin) {
        RegisteredServiceProvider<Economy> rsp =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);
        return rsp == null ? null : new VaultEco(rsp.getProvider());
    }

    @Override
    public boolean has(OfflinePlayer p, double amount) {
        return economy.has(p, amount);
    }

    @Override
    public void withdraw(OfflinePlayer p, double amount) {
        economy.withdrawPlayer(p, amount);
    }

    @Override
    public void deposit(OfflinePlayer p, double amount) {
        economy.depositPlayer(p, amount);
    }

    @Override
    public String format(double amount) {
        return economy.format(amount);
    }
}
