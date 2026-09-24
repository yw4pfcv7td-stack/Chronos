package dev.chronos.storage;

import dev.chronos.model.ActionType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** Filtri di una ricerca (lookup / rollback / restore / GUI). */
public final class QuerySpec {
    public final List<String> users = new ArrayList<>(), xUsers = new ArrayList<>();
    public final List<String> blocks = new ArrayList<>(), xBlocks = new ArrayList<>();
    public final EnumSet<ActionType> types = EnumSet.noneOf(ActionType.class);
    public long since = -1, until = -1;
    public String world;
    public boolean box;
    public int minX, minY, minZ, maxX, maxY, maxZ;
    public long incident = -1;
    public int rolledBack = -1;   // -1 = qualsiasi, 0 = no, 1 = si
    public boolean now;           // salta l'anteprima
    public int page = 1;

    public QuerySpec copy() {
        QuerySpec c = new QuerySpec();
        c.users.addAll(users);
        c.xUsers.addAll(xUsers);
        c.blocks.addAll(blocks);
        c.xBlocks.addAll(xBlocks);
        c.types.addAll(types);
        c.since = since;
        c.until = until;
        c.world = world;
        c.box = box;
        c.minX = minX; c.minY = minY; c.minZ = minZ;
        c.maxX = maxX; c.maxY = maxY; c.maxZ = maxZ;
        c.incident = incident;
        c.rolledBack = rolledBack;
        c.now = now;
        c.page = page;
        return c;
    }

    /** Un rollback deve avere sempre un ambito ben definito. */
    public boolean hasScope() { return !users.isEmpty() || box || incident >= 0; }
}
