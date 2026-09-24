package dev.chronos.model;

import org.bukkit.entity.Player;

import java.util.Locale;

/** Chi ha compiuto l'azione: un giocatore (key = UUID) oppure una causa ambientale (key = "#nome"). */
public record Actor(String key, String name) {
    public static Actor of(Player p) { return new Actor(p.getUniqueId().toString(), p.getName()); }

    public static Actor env(String n) {
        String s = "#" + n.toLowerCase(Locale.ROOT);
        return new Actor(s, s);
    }
}
