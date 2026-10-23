package ru.zokocraft.duels;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class MatchManager {
    public record Request(UUID from, UUID to, boolean drop, double bet) {}

    private final DuelsPlugin plugin;
    private final Set<UUID> normalQueue = new LinkedHashSet<>();
    private final List<List<UUID>> teamQueue = new ArrayList<>();
    private final Map<UUID, Match> byPlayer = new HashMap<>();
    private final Set<Match> active = new HashSet<>();
    private final Set<String> busyArenas = new HashSet<>();
    private final Map<UUID, Request> requests = new HashMap<>(); // кому -> вызов
    private boolean shuttingDown;

    public MatchManager(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------- состояние ----------

    public Match of(Player p) { return byPlayer.get(p.getUniqueId()); }

    public boolean inMatch(Player p) { return byPlayer.containsKey(p.getUniqueId()); }

    public boolean isQueued(UUID id) {
        if (normalQueue.contains(id)) return true;
        for (List<UUID> t : teamQueue) {
            if (t.contains(id)) return true;
        }
        return false;
    }

    // ---------- очереди ----------

    public void joinNormal(Player p) {
        Messages m = plugin.messages();
        if (inMatch(p)) { m.send(p, "in-match"); return; }
        if (isQueued(p.getUniqueId())) { m.send(p, "queue.already"); return; }
        normalQueue.add(p.getUniqueId());
        m.send(p, "queue.joined-normal");
        tryStart();
    }

    public void joinTeam(Player p) {
        Messages m = plugin.messages();
        if (inMatch(p)) { m.send(p, "in-match"); return; }
        PartyManager.Party party = plugin.parties().get(p.getUniqueId());
        if (party == null || party.member == null) { m.send(p, "queue.need-party"); return; }
        if (!party.leader.equals(p.getUniqueId())) { m.send(p, "queue.party-leader"); return; }
        if (isQueued(party.leader) || isQueued(party.member)) { m.send(p, "queue.already"); return; }
        Player a = Bukkit.getPlayer(party.leader);
        Player b = Bukkit.getPlayer(party.member);
        if (a == null || b == null || inMatch(a) || inMatch(b)) { m.send(p, "queue.party-busy"); return; }
        teamQueue.add(List.of(party.leader, party.member));
        m.send(a, "queue.joined-team");
        m.send(b, "queue.joined-team");
        tryStart();
    }

    public void leaveQueue(Player p) {
        Messages m = plugin.messages();
        UUID id = p.getUniqueId();
        boolean removed = normalQueue.remove(id);
        Iterator<List<UUID>> it = teamQueue.iterator();
        while (it.hasNext()) {
            List<UUID> t = it.next();
            if (t.contains(id)) {
                it.remove();
                removed = true;
                for (UUID other : t) {
                    Player o = Bukkit.getPlayer(other);
                    if (o != null && !o.equals(p)) m.send(o, "queue.team-left");
                }
            }
        }
        m.send(p, removed ? "queue.left" : "queue.not-in");
    }

    public void removeFromQueues(UUID id) {
        normalQueue.remove(id);
        teamQueue.removeIf(t -> t.contains(id));
    }

    private void cleanQueues() {
        normalQueue.removeIf(id -> {
            Player p = Bukkit.getPlayer(id);
            return p == null || inMatch(p);
        });
        teamQueue.removeIf(t -> {
            for (UUID id : t) {
                Player p = Bukkit.getPlayer(id);
                if (p == null || inMatch(p)) return true;
            }
            return false;
        });
    }

    private Arena pickFreeArena() {
        List<Arena> free = new ArrayList<>();
        for (Arena a : plugin.arenas().all()) {
            if (a.ready() && !busyArenas.contains(a.getName().toLowerCase())) free.add(a);
        }
        if (free.isEmpty()) return null;
        return free.get(ThreadLocalRandom.current().nextInt(free.size()));
    }

    public void tryStart() {
        if (shuttingDown) return;
        cleanQueues();
        while (true) {
            Arena arena = pickFreeArena();
            if (arena == null) return;
            if (normalQueue.size() >= 2) {
                Iterator<UUID> it = normalQueue.iterator();
                UUID a = it.next();
                UUID b = it.next();
                normalQueue.remove(a);
                normalQueue.remove(b);
                start(arena, List.of(a), List.of(b),
                        plugin.getConfig().getBoolean("normal.drop-items", false), 0, true, "1v1");
            } else if (teamQueue.size() >= 2) {
                List<UUID> t1 = teamQueue.remove(0);
                List<UUID> t2 = teamQueue.remove(0);
                start(arena, t1, t2,
                        plugin.getConfig().getBoolean("team.drop-items", false), 0, true, "2v2");
            } else {
                return;
            }
        }
    }

    private void start(Arena arena, List<UUID> t1, List<UUID> t2, boolean drop, double bet,
                       boolean ranked, String mode) {
        Match m = new Match(plugin, arena, t1, t2, drop, bet, ranked, mode);
        active.add(m);
        busyArenas.add(arena.getName().toLowerCase());
        for (UUID id : m.participants()) byPlayer.put(id, m);
        m.begin();
    }

    /** Вызывается матчем, когда он полностью завершён. */
    void finished(Match m) {
        active.remove(m);
        for (UUID id : m.participants()) byPlayer.remove(id, m);
        busyArenas.remove(m.arena().getName().toLowerCase());
        tryStart();
    }

    public boolean arenaBusy(Arena a) {
        return busyArenas.contains(a.getName().toLowerCase());
    }

    public void shutdown() {
        shuttingDown = true;
        for (Match m : new ArrayList<>(active)) m.abort();
    }

    // ---------- личные дуэли ----------

    public void sendRequest(Player from, Player to, boolean drop, double bet) {
        Messages m = plugin.messages();
        if (inMatch(from)) { m.send(from, "in-match"); return; }
        if (inMatch(to)) { m.send(from, "request.target-busy"); return; }
        if (requests.containsKey(to.getUniqueId())) { m.send(from, "request.pending-exists"); return; }
        if (bet > 0) {
            Eco eco = plugin.eco();
            if (eco == null) { m.send(from, "request.no-eco"); return; }
            if (!eco.has(from, bet)) { m.send(from, "request.no-money"); return; }
        }
        Request req = new Request(from.getUniqueId(), to.getUniqueId(), drop, bet);
        requests.put(to.getUniqueId(), req);

        m.send(from, "request.sent", "player", to.getName());
        m.send(to, "request.received",
                "player", from.getName(),
                "drop", m.str(drop ? "match.yes" : "match.no"),
                "bet", bet > 0 ? plugin.money(bet) : m.str("match.none"));
        Component buttons = m.get("request.accept-button").clickEvent(ClickEvent.runCommand("/duel accept"))
                .append(Component.space())
                .append(m.get("request.deny-button").clickEvent(ClickEvent.runCommand("/duel deny")));
        to.sendMessage(buttons);

        long ticks = 20L * plugin.getConfig().getInt("request-expire-seconds", 30);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Request cur = requests.get(req.to());
            if (cur != null && cur == req) {
                requests.remove(req.to());
                Player f = Bukkit.getPlayer(req.from());
                if (f != null) m.send(f, "request.expired", "player", to.getName());
            }
        }, ticks);
    }

    public void accept(Player target) {
        Messages m = plugin.messages();
        Request r = requests.remove(target.getUniqueId());
        if (r == null) { m.send(target, "request.none"); return; }
        Player from = Bukkit.getPlayer(r.from());
        if (from == null) { m.send(target, "request.not-found"); return; }
        if (inMatch(from) || inMatch(target)) { m.send(target, "request.target-busy"); return; }
        if (r.bet() > 0) {
            Eco eco = plugin.eco();
            if (eco == null || !eco.has(from, r.bet()) || !eco.has(target, r.bet())) {
                m.send(from, "request.no-money");
                m.send(target, "request.no-money");
                return;
            }
        }
        Arena arena = pickFreeArena();
        if (arena == null) {
            m.send(from, "request.no-arena");
            m.send(target, "request.no-arena");
            return;
        }
        removeFromQueues(from.getUniqueId());
        removeFromQueues(target.getUniqueId());
        start(arena, List.of(from.getUniqueId()), List.of(target.getUniqueId()), r.drop(), r.bet(),
                plugin.getConfig().getBoolean("rating.personal-duel-counts", false), "1v1");
    }

    public void deny(Player target) {
        Messages m = plugin.messages();
        Request r = requests.remove(target.getUniqueId());
        if (r == null) { m.send(target, "request.none"); return; }
        Player from = Bukkit.getPlayer(r.from());
        if (from != null) m.send(from, "request.denied", "player", target.getName());
    }

    public void handleQuit(Player p) {
        UUID id = p.getUniqueId();
        removeFromQueues(id);
        requests.remove(id);
        requests.values().removeIf(r -> r.from().equals(id));
    }
}
