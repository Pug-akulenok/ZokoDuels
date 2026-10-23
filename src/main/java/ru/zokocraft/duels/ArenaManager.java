package ru.zokocraft.duels;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ArenaManager {
    private final DuelsPlugin plugin;
    private final File file;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();

    public ArenaManager(DuelsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "arenas.yml");
        load();
    }

    private void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = y.getConfigurationSection("arenas");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection a = sec.getConfigurationSection(key);
            if (a == null) continue;
            Arena arena = new Arena(a.getString("name", key));
            for (String s : a.getStringList("team1")) {
                Spawn sp = Spawn.decode(s);
                if (sp != null) arena.spawns(1).add(sp);
            }
            for (String s : a.getStringList("team2")) {
                Spawn sp = Spawn.decode(s);
                if (sp != null) arena.spawns(2).add(sp);
            }
            arenas.put(key.toLowerCase(), arena);
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<String, Arena> e : arenas.entrySet()) {
            Arena a = e.getValue();
            String base = "arenas." + e.getKey();
            y.set(base + ".name", a.getName());
            List<String> t1 = new ArrayList<>();
            for (Spawn s : a.spawns(1)) t1.add(s.encode());
            List<String> t2 = new ArrayList<>();
            for (Spawn s : a.spawns(2)) t2.add(s.encode());
            y.set(base + ".team1", t1);
            y.set(base + ".team2", t2);
        }
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Не удалось сохранить arenas.yml: " + ex.getMessage());
        }
    }

    public Arena get(String name) {
        return arenas.get(name.toLowerCase());
    }

    public Collection<Arena> all() {
        return arenas.values();
    }

    public Arena create(String name) {
        if (arenas.containsKey(name.toLowerCase())) return null;
        Arena a = new Arena(name);
        arenas.put(name.toLowerCase(), a);
        save();
        return a;
    }

    public boolean delete(String name) {
        boolean removed = arenas.remove(name.toLowerCase()) != null;
        if (removed) save();
        return removed;
    }
}
