package ru.zokocraft.duels;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

public final class DuelsPlugin extends JavaPlugin {
    private Messages messages;
    private ArenaManager arenas;
    private StatsManager stats;
    private PartyManager parties;
    private MatchManager matches;
    private Menus menus;
    private Eco eco;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new Messages(this);
        arenas = new ArenaManager(this);
        stats = new StatsManager(this);
        parties = new PartyManager(this);
        matches = new MatchManager(this);
        menus = new Menus(this);

        DuelCommand duel = new DuelCommand(this);
        bind("duel", duel, duel);
        ArenaCommand arena = new ArenaCommand(this);
        bind("duelarena", arena, arena);

        getServer().getPluginManager().registerEvents(menus, this);
        getServer().getPluginManager().registerEvents(new MatchListener(this), this);
    }

    @Override
    public void onDisable() {
        if (matches != null) matches.shutdown();
        if (stats != null) stats.save();
        if (arenas != null) arenas.save();
    }

    private void bind(String name, CommandExecutor ex, TabCompleter tc) {
        PluginCommand c = getCommand(name);
        if (c != null) {
            c.setExecutor(ex);
            c.setTabCompleter(tc);
        }
    }

    /** Экономика (Vault) или null, если её нет. */
    public Eco eco() {
        if (eco == null && getServer().getPluginManager().getPlugin("Vault") != null) {
            eco = VaultEco.create(this);
        }
        return eco;
    }

    public String money(double amount) {
        Eco e = eco();
        return e != null ? e.format(amount) : String.valueOf(amount);
    }

    public Messages messages() { return messages; }
    public ArenaManager arenas() { return arenas; }
    public StatsManager stats() { return stats; }
    public PartyManager parties() { return parties; }
    public MatchManager matches() { return matches; }
    public Menus menus() { return menus; }
}
