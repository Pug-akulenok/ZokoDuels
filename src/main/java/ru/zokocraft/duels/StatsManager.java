package ru.zokocraft.duels;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class StatsManager {
    public static final class Stats {
        public String name = "?";
        public int rating, kills, deaths, games, wins, losses, draws;
        public final LinkedList<String> history = new LinkedList<>();
    }

    public record TopEntry(String name, int rating) {}

    private final DuelsPlugin plugin;
    private final File file;
    private final Map<UUID, Stats> map = new HashMap<>();

    public StatsManager(DuelsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stats.yml");
        load();
    }

    private void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = y.getConfigurationSection("players");
        if (sec == null) return;
        for (String k : sec.getKeys(false)) {
            try {
                UUID id = UUID.fromString(k);
                ConfigurationSection p = sec.getConfigurationSection(k);
                if (p == null) continue;
                Stats s = new Stats();
                s.name = p.getString("name", "?");
                s.rating = p.getInt("rating", plugin.getConfig().getInt("rating.start", 100));
                s.kills = p.getInt("kills");
                s.deaths = p.getInt("deaths");
                s.games = p.getInt("games");
                s.wins = p.getInt("wins");
                s.losses = p.getInt("losses");
                s.draws = p.getInt("draws");
                s.history.addAll(p.getStringList("history"));
                map.put(id, s);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public Stats get(UUID id) {
        return map.computeIfAbsent(id, k -> {
            Stats s = new Stats();
            s.rating = plugin.getConfig().getInt("rating.start", 100);
            return s;
        });
    }

    public void touch(UUID id, String name) {
        get(id).name = name;
    }

    /** Топ по рейтингу (только те, кто сыграл хотя бы одну дуэль). */
    public List<TopEntry> top(int limit) {
        List<Stats> list = new ArrayList<>();
        for (Stats s : map.values()) {
            if (s.games > 0) list.add(s);
        }
        list.sort(Comparator.<Stats>comparingInt(s -> s.rating).reversed()
                .thenComparing(Comparator.<Stats>comparingInt(s -> s.wins).reversed()));
        List<TopEntry> out = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, list.size()); i++) {
            out.add(new TopEntry(list.get(i).name, list.get(i).rating));
        }
        return out;
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Stats> e : map.entrySet()) {
            Stats s = e.getValue();
            String b = "players." + e.getKey();
            y.set(b + ".name", s.name);
            y.set(b + ".rating", s.rating);
            y.set(b + ".kills", s.kills);
            y.set(b + ".deaths", s.deaths);
            y.set(b + ".games", s.games);
            y.set(b + ".wins", s.wins);
            y.set(b + ".losses", s.losses);
            y.set(b + ".draws", s.draws);
            y.set(b + ".history", new ArrayList<>(s.history));
        }
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Не удалось сохранить stats.yml: " + ex.getMessage());
        }
    }
}
