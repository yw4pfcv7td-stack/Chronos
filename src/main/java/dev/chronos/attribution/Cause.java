package dev.chronos.attribution;

import dev.chronos.model.Actor;

/** Causa risalita di un evento (es. "Steve ha acceso il TNT"). Condivide un unico incidente. */
public final class Cause {
    private final Actor actor;
    private final String kind;
    private final boolean tracksIncident;
    private volatile long expires;
    private volatile long incidentId;

    public Cause(Actor actor, String kind, boolean tracksIncident, long ttlMs) {
        this.actor = actor;
        this.kind = kind;
        this.tracksIncident = tracksIncident;
        this.expires = System.currentTimeMillis() + ttlMs;
    }

    public Actor actor() { return actor; }
    public String kind() { return kind; }
    public boolean tracksIncident() { return tracksIncident; }
    public long incidentId() { return incidentId; }
    void setIncidentId(long id) { this.incidentId = id; }

    public void touch(long ttlMs) {
        long e = System.currentTimeMillis() + ttlMs;
        if (e > expires) expires = e;
    }

    public boolean expired() { return System.currentTimeMillis() > expires; }
}
