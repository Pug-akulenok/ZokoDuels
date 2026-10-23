package ru.zokocraft.duels;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public record Spawn(String world, double x, double y, double z, float yaw, float pitch) {

    public static Spawn of(Location l) {
        return new Spawn(l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    public Location toLocation() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z, yaw, pitch);
    }

    public String encode() {
        return world + ";" + x + ";" + y + ";" + z + ";" + yaw + ";" + pitch;
    }

    public static Spawn decode(String s) {
        String[] p = s.split(";");
        if (p.length < 6) return null;
        try {
            return new Spawn(p[0], Double.parseDouble(p[1]), Double.parseDouble(p[2]),
                    Double.parseDouble(p[3]), Float.parseFloat(p[4]), Float.parseFloat(p[5]));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
