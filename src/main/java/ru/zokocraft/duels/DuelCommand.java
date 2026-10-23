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

public final class DuelCommand implements CommandExecutor, TabCompleter {
    private final DuelsPlugin plugin;

    public DuelCommand(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Messages m = plugin.messages();
        if (!(sender instanceof Player p)) {
            m.send(sender, "player-only");
            return true;
        }
        if (args.length == 0) {
            if (plugin.matches().inMatch(p)) { m.send(p, "in-match"); return true; }
            plugin.menus().openMain(p);
            return true;
        }
        String first = args[0].toLowerCase(Locale.ROOT);
        if (first.equals("accept")) { plugin.matches().accept(p); return true; }
        if (first.equals("deny")) { plugin.matches().deny(p); return true; }

        if (plugin.matches().inMatch(p)) { m.send(p, "in-match"); return true; }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) { m.send(p, "request.not-found"); return true; }
        if (target.equals(p)) { m.send(p, "request.self"); return true; }
        if (plugin.matches().inMatch(target)) { m.send(p, "request.target-busy"); return true; }

        double bet = 0;
        if (args.length >= 2) {
            try {
                bet = Double.parseDouble(args[1].replace(',', '.'));
            } catch (NumberFormatException ex) {
                m.send(p, "request.usage");
                return true;
            }
            if (Double.isNaN(bet) || Double.isInfinite(bet) || bet < 0) {
                m.send(p, "request.usage");
                return true;
            }
            bet = Math.floor(bet * 100) / 100;
        }
        if (bet > 0) {
            Eco eco = plugin.eco();
            if (eco == null) { m.send(p, "request.no-eco"); return true; }
            double min = plugin.getConfig().getDouble("bet.min", 10);
            double max = plugin.getConfig().getDouble("bet.max", 100000);
            if (bet < min || bet > max) {
                m.send(p, "request.bet-limits", "min", plugin.money(min), "max", plugin.money(max));
                return true;
            }
            if (!eco.has(p, bet)) { m.send(p, "request.no-money"); return true; }
        }
        plugin.menus().openRequest(p, target, bet);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String pre = args[0].toLowerCase(Locale.ROOT);
            for (String s : List.of("accept", "deny")) {
                if (s.startsWith(pre)) out.add(s);
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(pre)) out.add(p.getName());
            }
        } else if (args.length == 2) {
            out.add("100");
        }
        return out;
    }
}
