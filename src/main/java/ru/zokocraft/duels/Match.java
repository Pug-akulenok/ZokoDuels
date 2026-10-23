package ru.zokocraft.duels;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;

public final class Match {
    public enum Phase { PREP, FIGHT, END }

    public enum Reason { DAMAGE, QUIT, DEATH }

    private static final Title.Times TIMES =
            Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(500));

    private final DuelsPlugin plugin;
    private final Arena arena;
    private final List<UUID> team1;
    private final List<UUID> team2;
    private final List<UUID> all = new ArrayList<>();
    private final boolean dropItems;
    private final boolean ranked;
    private final double bet;
    private final String mode;

    private final Set<UUID> alive = new HashSet<>();
    private final Map<UUID, Location> origin = new HashMap<>();
    private final Map<UUID, GameMode> modes = new HashMap<>();
    private final Map<UUID, UUID> lastHit = new HashMap<>();
    private final Set<UUID> restored = new HashSet<>();
    private final List<Item> droppedItems = new ArrayList<>();

    private Phase phase = Phase.PREP;
    private int seconds;
    private BukkitTask task;
    private BossBar bar;
    private List<UUID> winners = new ArrayList<>();
    private boolean draw;
    private boolean cleaned;

    public Match(DuelsPlugin plugin, Arena arena, List<UUID> t1, List<UUID> t2,
                 boolean dropItems, double bet, boolean ranked, String mode) {
        this.plugin = plugin;
        this.arena = arena;
        this.team1 = new ArrayList<>(t1);
        this.team2 = new ArrayList<>(t2);
        this.dropItems = dropItems;
        this.bet = bet;
        this.ranked = ranked;
        this.mode = mode;
        all.addAll(team1);
        all.addAll(team2);
    }

    // ---------- доступ ----------

    public Arena arena() { return arena; }
    public Phase phase() { return phase; }
    public boolean dropItems() { return dropItems; }
    public List<UUID> participants() { return Collections.unmodifiableList(all); }
    public boolean isAlive(Player p) { return alive.contains(p.getUniqueId()); }

    public boolean sameTeam(Player a, Player b) {
        UUID x = a.getUniqueId(), y = b.getUniqueId();
        return (team1.contains(x) && team1.contains(y)) || (team2.contains(x) && team2.contains(y));
    }

    public void setLastHit(UUID victim, UUID attacker) {
        lastHit.put(victim, attacker);
    }

    public Player lastHitter(Player victim) {
        UUID id = lastHit.get(victim.getUniqueId());
        return id == null ? null : Bukkit.getPlayer(id);
    }

    // ---------- старт ----------

    public void begin() {
        placeTeam(team1, arena.spawns(1));
        placeTeam(team2, arena.spawns(2));

        bar = BossBar.bossBar(net.kyori.adventure.text.Component.empty(), 1f,
                BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
        seconds = plugin.getConfig().getInt("prepare-seconds", 30);

        Messages m = plugin.messages();
        for (UUID id : all) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            p.showBossBar(bar);
            m.send(p, "match.start",
                    "mode", mode,
                    "opponents", names(opponentsOf(id)),
                    "drop", m.str(dropItems ? "match.yes" : "match.no"),
                    "bet", bet > 0 ? plugin.money(bet * all.size()) : m.str("match.none"));
            m.send(p, "match.prep", "sec", seconds);
        }
        display();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void placeTeam(List<UUID> team, List<Spawn> spawns) {
        for (int i = 0; i < team.size(); i++) {
            Player p = Bukkit.getPlayer(team.get(i));
            if (p == null) continue;
            UUID id = p.getUniqueId();
            origin.put(id, p.getLocation().clone());
            modes.put(id, p.getGameMode());
            alive.add(id);
            plugin.stats().touch(id, p.getName());
            Eco eco = plugin.eco();
            if (bet > 0 && eco != null) eco.withdraw(p, bet);
            p.closeInventory();
            p.leaveVehicle();
            if (!spawns.isEmpty()) {
                Location loc = spawns.get(i % spawns.size()).toLocation();
                if (loc != null) p.teleport(loc);
            }
            p.setGameMode(GameMode.SURVIVAL);
            resetVitals(p);
            for (PotionEffect e : new ArrayList<>(p.getActivePotionEffects())) {
                p.removePotionEffect(e.getType());
            }
        }
    }

    private void resetVitals(Player p) {
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(max != null ? max.getValue() : 20.0);
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFireTicks(0);
        p.setFallDistance(0f);
    }

    // ---------- таймер ----------

    private void tick() {
        if (cleaned) return;
        seconds--;
        switch (phase) {
            case PREP -> {
                if (seconds <= 0) { startFight(); return; }
                if (seconds <= 5) countdown(seconds);
            }
            case FIGHT -> {
                if (seconds <= 0) { finish(List.of(), true); return; }
            }
            case END -> {
                if (seconds <= 0) { cleanup(); return; }
            }
        }
        display();
    }

    private void countdown(int sec) {
        for (UUID id : all) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            p.showTitle(Title.title(Text.c("&e&l" + sec), Text.c(""),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ZERO)));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
        }
    }

    private void startFight() {
        phase = Phase.FIGHT;
        seconds = plugin.getConfig().getInt("fight-seconds", 60);
        display();
        Messages m = plugin.messages();
        for (UUID id : all) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            p.showTitle(Title.title(m.get("title.go"), m.get("title.go-sub"), TIMES));
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1f);
        }
    }

    private void display() {
        if (bar == null) return;
        Messages m = plugin.messages();
        switch (phase) {
            case PREP -> {
                int total = Math.max(1, plugin.getConfig().getInt("prepare-seconds", 30));
                bar.color(BossBar.Color.YELLOW);
                bar.progress(clamp(seconds / (float) total));
                bar.name(m.get("bar.prepare", "sec", seconds));
            }
            case FIGHT -> {
                int total = Math.max(1, plugin.getConfig().getInt("fight-seconds", 60));
                bar.color(BossBar.Color.RED);
                bar.progress(clamp(seconds / (float) total));
                bar.name(m.get("bar.fight", "sec", seconds));
            }
            case END -> {
                bar.color(BossBar.Color.GREEN);
                bar.progress(1f);
                bar.name(m.get("bar.end"));
            }
        }
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    // ---------- бой ----------

    public void eliminate(Player victim, Player killer, Reason reason) {
        if (phase != Phase.FIGHT || !alive.remove(victim.getUniqueId())) return;
        StatsManager st = plugin.stats();
        st.get(victim.getUniqueId()).deaths++;
        boolean validKiller = killer != null && !killer.equals(victim) && all.contains(killer.getUniqueId());
        if (validKiller) st.get(killer.getUniqueId()).kills++;

        if (reason != Reason.DEATH && dropItems) dropInventory(victim);
        if (reason == Reason.DAMAGE) {
            resetVitals(victim);
            victim.setGameMode(GameMode.SPECTATOR);
        }
        String by = validKiller ? " &7(&e" + killer.getName() + "&7)" : "";
        broadcast("match.eliminated", "victim", victim.getName(), "by", by);
        checkEnd();
    }

    private void dropInventory(Player victim) {
        PlayerInventory inv = victim.getInventory();
        Location loc = victim.getLocation();
        for (ItemStack it : inv.getContents()) {
            if (it == null || it.getType().isAir()) continue;
            droppedItems.add(loc.getWorld().dropItemNaturally(loc, it));
        }
        inv.clear();
    }

    private boolean teamAlive(List<UUID> team) {
        for (UUID id : team) {
            if (alive.contains(id)) return true;
        }
        return false;
    }

    private void checkEnd() {
        boolean a1 = teamAlive(team1);
        boolean a2 = teamAlive(team2);
        if (a1 && a2) return;
        if (a1) finish(team1, false);
        else if (a2) finish(team2, false);
        else finish(List.of(), true);
    }

    public void onQuit(Player p) {
        if (cleaned || !all.contains(p.getUniqueId())) return;
        if (phase == Phase.PREP) {
            for (UUID id : all) {
                Player o = Bukkit.getPlayer(id);
                if (o != null && !o.equals(p)) plugin.messages().send(o, "match.cancelled");
            }
            refund();
            phase = Phase.END;
            winners = new ArrayList<>();
            restore(p);
            cleanup();
            return;
        }
        if (phase == Phase.FIGHT) eliminate(p, lastHitter(p), Reason.QUIT);
        restore(p);
    }

    // ---------- конец ----------

    private void finish(List<UUID> win, boolean isDraw) {
        if (phase == Phase.END) return;
        phase = Phase.END;
        winners = new ArrayList<>(win);
        draw = isDraw;
        seconds = plugin.getConfig().getInt("end-seconds", 8);

        Messages m = plugin.messages();
        StatsManager st = plugin.stats();
        String time = new SimpleDateFormat("dd.MM HH:mm").format(new Date());
        for (UUID id : all) {
            StatsManager.Stats s = st.get(id);
            s.games++;
            String res;
            if (isDraw) {
                s.draws++;
                res = m.str("history.draw");
            } else if (win.contains(id)) {
                s.wins++;
                if (ranked) s.rating += plugin.getConfig().getInt("rating.per-win", 5);
                res = m.str("history.win");
            } else {
                s.losses++;
                res = m.str("history.lose");
            }
            s.history.addFirst(res + " &7vs &f" + names(opponentsOf(id)) + " &8(" + mode + ", " + time + ")");
            while (s.history.size() > 10) s.history.removeLast();
        }
        st.save();
        double each = payout();

        for (UUID id : all) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            boolean won = !isDraw && winners.contains(id);
            String t = isDraw ? "draw" : won ? "win" : "lose";
            p.showTitle(Title.title(m.get("title." + t), m.get("title." + t + "-sub"), TIMES));
            if (isDraw) {
                m.send(p, "match.result-draw");
            } else {
                m.send(p, "match.result-win", "winners", names(winners));
                if (won && each > 0) m.send(p, "match.result-money", "amount", plugin.money(each));
            }
        }
        display();
    }

    private double payout() {
        Eco eco = plugin.eco();
        if (bet <= 0 || eco == null) return 0;
        if (draw) {
            refund();
            return 0;
        }
        double each = Math.floor(bet * all.size() / winners.size() * 100) / 100;
        for (UUID id : winners) eco.deposit(Bukkit.getOfflinePlayer(id), each);
        return each;
    }

    private void refund() {
        Eco eco = plugin.eco();
        if (bet <= 0 || eco == null) return;
        for (UUID id : all) eco.deposit(Bukkit.getOfflinePlayer(id), bet);
    }

    /** Принудительная остановка (выключение сервера): ставки возвращаются, статистика не меняется. */
    public void abort() {
        if (cleaned) return;
        if (phase != Phase.END) {
            refund();
            phase = Phase.END;
        }
        cleanup();
    }

    private void cleanup() {
        if (cleaned) return;
        cleaned = true;
        if (task != null) task.cancel();

        List<Player> receivers = new ArrayList<>();
        for (UUID id : winners) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) receivers.add(p);
        }
        if (!receivers.isEmpty()) {
            int i = 0;
            for (Item item : droppedItems) {
                if (!item.isValid()) continue;
                ItemStack stack = item.getItemStack();
                item.remove();
                Player r = receivers.get(i++ % receivers.size());
                for (ItemStack left : r.getInventory().addItem(stack).values()) {
                    r.getWorld().dropItemNaturally(r.getLocation(), left);
                }
            }
        }
        for (UUID id : all) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) restore(p);
        }
        plugin.matches().finished(this);
    }

    public void restore(Player p) {
        if (!restored.add(p.getUniqueId())) return;
        if (bar != null) p.hideBossBar(bar);
        p.setGameMode(modes.getOrDefault(p.getUniqueId(), GameMode.SURVIVAL));
        Location o = origin.get(p.getUniqueId());
        if (o != null) p.teleport(o);
        p.setFireTicks(0);
        p.setFallDistance(0f);
    }

    // ---------- утилиты ----------

    private void broadcast(String key, Object... kv) {
        for (UUID id : all) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) plugin.messages().send(p, key, kv);
        }
    }

    private List<UUID> opponentsOf(UUID id) {
        return team1.contains(id) ? team2 : team1;
    }

    private String names(List<UUID> team) {
        StringJoiner j = new StringJoiner(", ");
        for (UUID id : team) {
            Player p = Bukkit.getPlayer(id);
            String n = p != null ? p.getName() : Bukkit.getOfflinePlayer(id).getName();
            j.add(n == null ? "?" : n);
        }
        return j.toString();
    }
}
