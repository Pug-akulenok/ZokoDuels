package ru.zokocraft.duels;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;

public final class Messages {
    private final FileConfiguration cfg;

    public Messages(DuelsPlugin plugin) {
        File f = new File(plugin.getDataFolder(), "messages.yml");
        if (!f.exists()) plugin.saveResource("messages.yml", false);
        this.cfg = YamlConfiguration.loadConfiguration(f);
    }

    public String str(String key, Object... kv) {
        String s = cfg.getString(key);
        if (s == null) s = "&c[" + key + "]";
        for (int i = 0; i + 1 < kv.length; i += 2) {
            s = s.replace("{" + kv[i] + "}", String.valueOf(kv[i + 1]));
        }
        return s;
    }

    public Component get(String key, Object... kv) {
        return Text.c(str(key, kv));
    }

    public List<String> list(String key) {
        return cfg.getStringList(key);
    }

    public void send(CommandSender to, String key, Object... kv) {
        to.sendMessage(Text.c(str("prefix") + str(key, kv)));
    }
}
