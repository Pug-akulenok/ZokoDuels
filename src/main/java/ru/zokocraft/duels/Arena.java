package ru.zokocraft.duels;

import java.util.ArrayList;
import java.util.List;

public final class Arena {
    private final String name;
    private final List<Spawn> team1 = new ArrayList<>();
    private final List<Spawn> team2 = new ArrayList<>();

    public Arena(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public List<Spawn> spawns(int team) {
        return team == 1 ? team1 : team2;
    }

    public boolean ready() {
        return !team1.isEmpty() && !team2.isEmpty();
    }
}
