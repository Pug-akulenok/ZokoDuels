package ru.zokocraft.duels;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class Menus implements Listener {

    public static final class Holder implements InventoryHolder {
        public enum Type { MAIN, STATS, REQUEST }

        final Type type;
        Inventory inv;
        UUID targetId;
        String targetName;
        double bet;
        boolean drop = true;

        Holder(Type type) {
            this.type = type;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final DuelsPlugin plugin;

    public Menus(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------- главное меню ----------

    public void openMain(Player p) {
        Holder h = new Holder(Holder.Type.MAIN);
        Inventory inv = Bukkit.createInventory(h, 45, Text.c("&6&lДуэли"));
        h.inv = inv;
        border(inv);

        int perWin = plugin.getConfig().getInt("rating.per-win", 5);
        boolean dropN = plugin.getConfig().getBoolean("normal.drop-items", false);
        boolean dropT = plugin.getConfig().getBoolean("team.drop-items", false);

        inv.setItem(4, head(p, "&6Ваш профиль",
                "&7Нажмите, чтобы открыть статистику"));

        inv.setItem(20, item(Material.COMPASS, "&6Обычная дуэль &71 на 1",
                "&7Бой со случайным соперником.",
                "",
                "&e[ЛКМ] &7Начать поиск",
                "&e[Shift+ЛКМ] &7Отменить поиск",
                "",
                "&6• Параметры",
                "&7 ▸ Случайный соперник",
                "&7 ▸ Вещи выпадают: &f" + (dropN ? "да" : "нет"),
                "&7 ▸ Победа: &a+" + perWin + " &7к рейтингу"));

        inv.setItem(22, item(Material.SHIELD, "&6Командная дуэль &72 на 2",
                "&7Соберите команду из 2 игроков",
                "&7и сразитесь против другой пары!",
                "",
                "&e[ЛКМ] &7Начать поиск",
                "&e[Shift+ЛКМ] &7Отменить поиск",
                "",
                "&6• Команда",
                "&7 ▸ Пригласить: &e/duelarena party add <ник>",
                "&7 ▸ Исключить: &e/duelarena party kick <ник>",
                "&7 ▸ Вещи выпадают: &f" + (dropT ? "да" : "нет")));

        inv.setItem(24, item(Material.GOLDEN_SWORD, "&6Личная дуэль",
                "&7Бой с выбранным игроком на ваших условиях.",
                "",
                "&7Команда: &e/duel <ник> [ставка]",
                "",
                "&7Вы выберете, выпадают ли вещи,",
                "&7и можете поставить деньги (Vault).",
                "&7Ставка &6необязательна&7."));

        p.openInventory(inv);
    }

    // ---------- статистика ----------

    public void openStats(Player p) {
        Holder h = new Holder(Holder.Type.STATS);
        Inventory inv = Bukkit.createInventory(h, 45, Text.c("&6&lВаша статистика"));
        h.inv = inv;
        border(inv);

        StatsManager.Stats s = plugin.stats().get(p.getUniqueId());
        inv.setItem(13, head(p, "&6Ваша статистика",
                "&6• &7Рейтинг: &e" + s.rating,
                "&6• &7Убийств: &f" + s.kills,
                "&6• &7Смертей: &f" + s.deaths,
                "&6• &7Сыграно дуэлей: &f" + s.games,
                "&6• &7Побед: &a" + s.wins,
                "&6• &7Поражений: &c" + s.losses,
                "&6• &7Ничьих: &e" + s.draws));

        List<String> hist = new ArrayList<>();
        if (s.history.isEmpty()) {
            hist.add("&cПусто");
        } else {
            hist.addAll(s.history);
        }
        inv.setItem(21, item(Material.DARK_OAK_SIGN, "&6История сражений", hist.toArray(new String[0])));

        List<String> top = new ArrayList<>();
        top.add("&7Ваш рейтинг: &e" + s.rating);
        top.add("");
        top.add("&6Топ игроков:");
        List<StatsManager.TopEntry> entries = plugin.stats().top(10);
        if (entries.isEmpty()) {
            top.add("&7Пока никого нет");
        } else {
            for (int i = 0; i < entries.size(); i++) {
                StatsManager.TopEntry e = entries.get(i);
                top.add("&6(" + (i + 1) + ") &a" + e.name() + " &e" + e.rating());
            }
        }
        inv.setItem(23, item(Material.TOTEM_OF_UNDYING, "&6Топ-10 по рейтингу", top.toArray(new String[0])));

        inv.setItem(40, item(Material.ARROW, "&eНазад", "&7Вернуться в меню дуэлей"));
        p.openInventory(inv);
    }

    // ---------- настройка личной дуэли ----------

    public void openRequest(Player p, Player target, double bet) {
        Holder h = new Holder(Holder.Type.REQUEST);
        h.targetId = target.getUniqueId();
        h.targetName = target.getName();
        h.bet = bet;
        Inventory inv = Bukkit.createInventory(h, 27, Text.c("&6Вызов: &f" + target.getName()));
        h.inv = inv;
        border(inv);
        inv.setItem(11, dropItem(h.drop));
        inv.setItem(13, item(Material.GOLD_INGOT,
                "&6Ставка: &e" + (bet > 0 ? plugin.money(bet) : "нет"),
                "&7Задаётся командой:",
                "&e/duel " + target.getName() + " <сумма>"));
        inv.setItem(15, item(Material.EMERALD, "&a&lОтправить вызов",
                "&7Игроку &e" + target.getName() + " &7придёт запрос."));
        inv.setItem(22, item(Material.BARRIER, "&cОтмена"));
        p.openInventory(inv);
    }

    private ItemStack dropItem(boolean drop) {
        if (drop) {
            return item(Material.LIME_DYE, "&aВещи выпадают: да",
                    "&7Проигравший отдаёт вещи победителю.",
                    "", "&eНажмите, чтобы переключить");
        }
        return item(Material.RED_DYE, "&cВещи выпадают: нет",
                "&7Каждый оставит свои вещи.",
                "", "&eНажмите, чтобы переключить");
    }

    // ---------- клики ----------

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;

        int slot = e.getSlot();
        ClickType click = e.getClick();
        MatchManager mm = plugin.matches();

        switch (h.type) {
            case MAIN -> {
                switch (slot) {
                    case 4 -> openStats(p);
                    case 20 -> {
                        if (click == ClickType.SHIFT_LEFT) mm.leaveQueue(p);
                        else if (click == ClickType.LEFT) { p.closeInventory(); mm.joinNormal(p); }
                    }
                    case 22 -> {
                        if (click == ClickType.SHIFT_LEFT) mm.leaveQueue(p);
                        else if (click == ClickType.LEFT) { p.closeInventory(); mm.joinTeam(p); }
                    }
                    case 24 -> {
                        p.closeInventory();
                        plugin.messages().send(p, "menu.personal-help");
                    }
                    default -> { }
                }
            }
            case STATS -> {
                if (slot == 40) openMain(p);
            }
            case REQUEST -> {
                switch (slot) {
                    case 11 -> {
                        h.drop = !h.drop;
                        h.inv.setItem(11, dropItem(h.drop));
                    }
                    case 15 -> {
                        Player target = Bukkit.getPlayer(h.targetId);
                        p.closeInventory();
                        if (target == null) plugin.messages().send(p, "request.not-found");
                        else mm.sendRequest(p, target, h.drop, h.bet);
                    }
                    case 22 -> p.closeInventory();
                    default -> { }
                }
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    // ---------- предметы ----------

    private void border(Inventory inv) {
        int size = inv.getSize();
        int lastRow = size / 9 - 1;
        for (int i = 0; i < size; i++) {
            int row = i / 9, col = i % 9;
            boolean edge = row == 0 || row == lastRow || col == 0 || col == 8;
            inv.setItem(i, pane(edge ? Material.ORANGE_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE));
        }
    }

    private ItemStack pane(Material m) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(" "));
        meta.setHideTooltip(true);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack item(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        decorate(meta, name, lore);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack head(Player owner, String name, String... lore) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) it.getItemMeta();
        meta.setOwningPlayer(owner);
        decorate(meta, name, lore);
        it.setItemMeta(meta);
        return it;
    }

    private void decorate(ItemMeta meta, String name, String... lore) {
        meta.displayName(Text.item(name));
        if (lore.length > 0) {
            List<Component> lines = new ArrayList<>();
            for (String l : lore) lines.add(Text.item(l));
            meta.lore(lines);
        }
        meta.addItemFlags(ItemFlag.values());
    }
}
