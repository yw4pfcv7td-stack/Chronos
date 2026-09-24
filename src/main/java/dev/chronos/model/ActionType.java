package dev.chronos.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public enum ActionType {
    BLOCK_BREAK(0, "break", "ha rotto"),
    BLOCK_PLACE(1, "place", "ha piazzato"),
    CONTAINER_ADD(2, "add", "ha inserito"),
    CONTAINER_REMOVE(3, "remove", "ha prelevato"),
    KILL(4, "kill", "ha ucciso");

    public final int id;
    public final String key, verb;

    ActionType(int id, String key, String verb) { this.id = id; this.key = key; this.verb = verb; }

    public boolean isBlock() { return this == BLOCK_BREAK || this == BLOCK_PLACE; }

    public boolean isContainer() { return this == CONTAINER_ADD || this == CONTAINER_REMOVE; }

    public static ActionType byId(int id) {
        for (ActionType t : values()) if (t.id == id) return t;
        return null;
    }

    /** Accetta break, place, add, remove, kill e i gruppi block / container. */
    public static List<ActionType> parse(String s) {
        List<ActionType> out = new ArrayList<>();
        switch (s.toLowerCase(Locale.ROOT)) {
            case "break" -> out.add(BLOCK_BREAK);
            case "place" -> out.add(BLOCK_PLACE);
            case "add" -> out.add(CONTAINER_ADD);
            case "remove" -> out.add(CONTAINER_REMOVE);
            case "kill" -> out.add(KILL);
            case "block" -> { out.add(BLOCK_BREAK); out.add(BLOCK_PLACE); }
            case "container" -> { out.add(CONTAINER_ADD); out.add(CONTAINER_REMOVE); }
            default -> { }
        }
        return out;
    }
}
