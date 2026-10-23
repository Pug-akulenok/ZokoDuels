package ru.zokocraft.duels;

import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Команда для командной дуэли: лидер + максимум 1 участник (2 игрока). */
public final class PartyManager {
    public static final class Party {
        public final UUID leader;
        public UUID member;

        Party(UUID leader) {
            this.leader = leader;
        }
    }

    private final DuelsPlugin plugin;
    private final Map<UUID, Party> byPlayer = new HashMap<>();
    private final Map<UUID, UUID> invites = new HashMap<>(); // кого пригласили -> лидер

    public PartyManager(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    public Party get(UUID id) {
        return byPlayer.get(id);
    }

    public void add(Player leader, String name) {
        Messages m = plugin.messages();
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) { m.send(leader, "party.not-found"); return; }
        if (target.equals(leader)) { m.send(leader, "party.self"); return; }
        Party party = byPlayer.get(leader.getUniqueId());
        if (party != null && !party.leader.equals(leader.getUniqueId())) { m.send(leader, "party.not-leader"); return; }
        if (party != null && party.member != null) { m.send(leader, "party.full"); return; }
        if (byPlayer.containsKey(target.getUniqueId())) { m.send(leader, "party.target-in-party"); return; }

        UUID lid = leader.getUniqueId();
        UUID tid = target.getUniqueId();
        invites.put(tid, lid);
        m.send(leader, "party.invited", "player", target.getName());
        m.send(target, "party.invite-received", "player", leader.getName());
        target.sendMessage(m.get("party.accept-button").clickEvent(ClickEvent.runCommand("/duelarena party accept")));
        Bukkit.getScheduler().runTaskLater(plugin, () -> invites.remove(tid, lid), 20L * 60);
    }

    public void accept(Player p) {
        Messages m = plugin.messages();
        UUID lid = invites.remove(p.getUniqueId());
        if (lid == null) { m.send(p, "party.invite-none"); return; }
        Player leader = Bukkit.getPlayer(lid);
        if (leader == null) { m.send(p, "party.not-found"); return; }
        if (byPlayer.containsKey(p.getUniqueId())) { m.send(p, "party.target-in-party"); return; }
        Party party = byPlayer.get(lid);
        if (party != null && !party.leader.equals(lid)) { m.send(p, "party.invite-none"); return; }
        if (party == null) {
            party = new Party(lid);
            byPlayer.put(lid, party);
        }
        if (party.member != null) { m.send(p, "party.full"); return; }
        party.member = p.getUniqueId();
        byPlayer.put(p.getUniqueId(), party);
        m.send(p, "party.joined", "player", leader.getName());
        m.send(leader, "party.member-joined", "player", p.getName());
    }

    public void kick(Player leader, String name) {
        Messages m = plugin.messages();
        Party party = byPlayer.get(leader.getUniqueId());
        if (party == null) { m.send(leader, "party.no-party"); return; }
        if (!party.leader.equals(leader.getUniqueId())) { m.send(leader, "party.not-leader"); return; }
        Player target = Bukkit.getPlayerExact(name);
        if (target == null || party.member == null || !party.member.equals(target.getUniqueId())) {
            m.send(leader, "party.not-in-party");
            return;
        }
        disband(party);
        m.send(leader, "party.kicked", "player", target.getName());
        m.send(target, "party.you-kicked");
    }

    public void leave(Player p) {
        Messages m = plugin.messages();
        Party party = byPlayer.get(p.getUniqueId());
        if (party == null) { m.send(p, "party.no-party"); return; }
        boolean leader = party.leader.equals(p.getUniqueId());
        UUID other = leader ? party.member : party.leader;
        disband(party);
        m.send(p, "party.disbanded");
        if (other != null) {
            Player o = Bukkit.getPlayer(other);
            if (o != null) {
                if (leader) m.send(o, "party.disbanded");
                else m.send(o, "party.left", "player", p.getName());
            }
        }
    }

    private void disband(Party party) {
        byPlayer.remove(party.leader);
        plugin.matches().removeFromQueues(party.leader);
        if (party.member != null) {
            byPlayer.remove(party.member);
            plugin.matches().removeFromQueues(party.member);
        }
    }

    public void onQuit(Player p) {
        invites.remove(p.getUniqueId());
        invites.values().remove(p.getUniqueId());
        Party party = byPlayer.get(p.getUniqueId());
        if (party == null) return;
        boolean leader = party.leader.equals(p.getUniqueId());
        UUID other = leader ? party.member : party.leader;
        disband(party);
        if (other != null) {
            Player o = Bukkit.getPlayer(other);
            if (o != null) {
                if (leader) plugin.messages().send(o, "party.disbanded");
                else plugin.messages().send(o, "party.left", "player", p.getName());
            }
        }
    }
}
