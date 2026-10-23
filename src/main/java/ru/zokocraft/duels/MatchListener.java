package ru.zokocraft.duels;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;

public final class MatchListener implements Listener {
    private final DuelsPlugin plugin;

    public MatchListener(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        MatchManager mm = plugin.matches();
        Match vm = mm.of(victim);

        Player attacker = null;
        if (e instanceof EntityDamageByEntityEvent by) {
            Entity d = by.getDamager();
            if (d instanceof Projectile pr && pr.getShooter() instanceof Entity s) d = s;
            if (d instanceof Player pl) attacker = pl;
        }
        if (attacker != null && mm.of(attacker) != vm) {
            e.setCancelled(true); // бить посторонних / посторонние бьют участников
            return;
        }
        if (vm == null) return;

        if (vm.phase() != Match.Phase.FIGHT || !vm.isAlive(victim)) {
            e.setCancelled(true);
            return;
        }
        if (attacker != null) {
            if (!attacker.equals(victim) && vm.sameTeam(attacker, victim)) {
                e.setCancelled(true);
                return;
            }
            vm.setLastHit(victim.getUniqueId(), attacker.getUniqueId());
        }
        if (victim.getHealth() - e.getFinalDamage() <= 0 && !hasTotem(victim)) {
            e.setCancelled(true);
            vm.eliminate(victim, attacker != null ? attacker : vm.lastHitter(victim), Match.Reason.DAMAGE);
        }
    }

    private boolean hasTotem(Player p) {
        ItemStack main = p.getInventory().getItemInMainHand();
        ItemStack off = p.getInventory().getItemInOffHand();
        return main.getType() == Material.TOTEM_OF_UNDYING || off.getType() == Material.TOTEM_OF_UNDYING;
    }

    /** Запасной вариант: игрок умер в обход урона (например, /kill). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        Match m = plugin.matches().of(p);
        if (m == null) return;
        e.deathMessage(null);
        e.setKeepLevel(true);
        e.setDroppedExp(0);
        boolean keep = !m.dropItems();
        e.setKeepInventory(keep);
        if (keep) e.getDrops().clear();
        m.eliminate(p, m.lastHitter(p), Match.Reason.DEATH);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        Match m = plugin.matches().of(p);
        plugin.matches().handleQuit(p);
        plugin.parties().onQuit(p);
        if (m != null) m.onQuit(p);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (plugin.matches().of(p) == null || p.hasPermission("zokoduels.bypass")) return;
        e.setCancelled(true);
        plugin.messages().send(p, "match.command-blocked");
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (plugin.getConfig().getBoolean("protect-arena", true) && plugin.matches().of(e.getPlayer()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (plugin.getConfig().getBoolean("protect-arena", true) && plugin.matches().of(e.getPlayer()) != null) {
            e.setCancelled(true);
        }
    }
}
