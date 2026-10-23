package ru.zokocraft.duels;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ArenaCommand implements CommandExecutor, TabCompleter {
    private final DuelsPlugin plugin;

    public ArenaCommand(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    private void usage(CommandSender s) {
        for (String line : plugin.messages().list("arena.usage")) {
            s.sendMessage(Text.c(line));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Messages m = plugin.messages();
        if (args.length == 0) { usage(sender); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("party")) {
            if (!(sender instanceof Player p)) { m.send(sender, "player-only"); return true; }
            party(p, args);
            return true;
        }

        if (!sender.hasPermission("zokoduels.admin")) {
            m.send(sender, "no-permission");
            return true;
        }
        ArenaManager am = plugin.arenas();

        switch (sub) {
            case "create" -> {
                if (args.length < 2) { usage(sender); return true; }
                String name = args[1];
                if (!name.matches("[A-Za-z0-9_-]+")) { m.send(sender, "arena.bad-name"); return true; }
                if (am.create(name) == null) { m.send(sender, "arena.exists"); return true; }
                m.send(sender, "arena.created", "arena", name);
            }
            case "delete" -> {
                if (args.length < 2) { usage(sender); return true; }
                Arena a = am.get(args[1]);
                if (a == null) { m.send(sender, "arena.not-found"); return true; }
                if (plugin.matches().arenaBusy(a)) { m.send(sender, "arena.in-use"); return true; }
                am.delete(args[1]);
                m.send(sender, "arena.deleted", "arena", a.getName());
            }
            case "setspawn" -> {
                if (!(sender instanceof Player p)) { m.send(sender, "player-only"); return true; }
                if (args.length < 3) { usage(sender); return true; }
                Arena a = am.get(args[1]);
                if (a == null) { m.send(sender, "arena.not-found"); return true; }
                int team = parseTeam(args[2]);
                if (team == 0) { m.send(sender, "arena.bad-team"); return true; }
                List<Spawn> list = a.spawns(team);
                if (list.size() >= 2) {
                    m.send(sender, "arena.spawn-full", "arena", a.getName(), "team", team);
                    return true;
                }
                list.add(Spawn.of(p.getLocation()));
                am.save();
                m.send(sender, "arena.spawn-set", "arena", a.getName(), "team", team, "count", list.size());
            }
            case "delspawn" -> {
                if (args.length < 3) { usage(sender); return true; }
                Arena a = am.get(args[1]);
                if (a == null) { m.send(sender, "arena.not-found"); return true; }
                int team = parseTeam(args[2]);
                if (team == 0) { m.send(sender, "arena.bad-team"); return true; }
                if (plugin.matches().arenaBusy(a)) { m.send(sender, "arena.in-use"); return true; }
                a.spawns(team).clear();
                am.save();
                m.send(sender, "arena.spawns-cleared", "arena", a.getName(), "team", team);
            }
            case "list" -> {
                if (am.all().isEmpty()) { m.send(sender, "arena.empty"); return true; }
                sender.sendMessage(Text.c(m.str("arena.list-header")));
                for (Arena a : am.all()) {
                    sender.sendMessage(Text.c(m.str("arena.list-entry", "arena", a.getName(),
                            "state", m.str(a.ready() ? "arena.ready" : "arena.not-ready"))));
                }
            }
            default -> usage(sender);
        }
        return true;
    }

    private int parseTeam(String s) {
        if (s.equals("1")) return 1;
        if (s.equals("2")) return 2;
        return 0;
    }

    private void party(Player p, String[] args) {
        Messages m = plugin.messages();
        if (args.length < 2) { m.send(p, "party.usage"); return; }
        PartyManager pm = plugin.parties();
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                if (args.length < 3) { m.send(p, "party.usage"); return; }
                pm.add(p, args[2]);
            }
            case "kick" -> {
                if (args.length < 3) { m.send(p, "party.usage"); return; }
                pm.kick(p, args[2]);
            }
            case "accept" -> pm.accept(p);
            case "leave" -> pm.leave(p);
            default -> m.send(p, "party.usage");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        boolean admin = sender.hasPermission("zokoduels.admin");
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("party"));
            if (admin) subs.addAll(List.of("create", "delete", "setspawn", "delspawn", "list"));
            add(out, subs, args[0]);
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("party")) {
                add(out, List.of("add", "kick", "accept", "leave"), args[1]);
            } else if (admin && List.of("delete", "setspawn", "delspawn").contains(sub)) {
                List<String> names = new ArrayList<>();
                for (Arena a : plugin.arenas().all()) names.add(a.getName());
                add(out, names, args[1]);
            }
        } else if (args.length == 3) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (admin && (sub.equals("setspawn") || sub.equals("delspawn"))) {
                add(out, List.of("1", "2"), args[2]);
            } else if (sub.equals("party") && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("kick"))) {
                List<String> names = new ArrayList<>();
                for (Player pl : Bukkit.getOnlinePlayers()) names.add(pl.getName());
                add(out, names, args[2]);
            }
        }
        return out;
    }

    private void add(List<String> out, List<String> options, String prefix) {
        String pre = prefix.toLowerCase(Locale.ROOT);
        for (String s : options) {
            if (s.toLowerCase(Locale.ROOT).startsWith(pre)) out.add(s);
        }
    }
}
