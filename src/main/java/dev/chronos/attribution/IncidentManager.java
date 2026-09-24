package dev.chronos.attribution;

import dev.chronos.Chronos;
import dev.chronos.model.LogRecord;

import java.util.concurrent.atomic.AtomicLong;

/** Crea gli incidenti in modo lazy: la riga esiste solo se la causa ha davvero prodotto qualcosa. */
public final class IncidentManager {
    private final Chronos plugin;
    private final AtomicLong next;

    public IncidentManager(Chronos plugin, long start) {
        this.plugin = plugin;
        this.next = new AtomicLong(start);
    }

    /** Restituisce l'ID dell'incidente della causa (0 se la causa non traccia incidenti). */
    public long ensure(Cause c) {
        if (!c.tracksIncident()) return 0;
        long id = c.incidentId();
        if (id != 0) return id;
        synchronized (c) {
            id = c.incidentId();
            if (id == 0) {
                id = next.getAndIncrement();
                c.setIncidentId(id);
                plugin.queue.add(new LogRecord.Incident(id, System.currentTimeMillis(), c.actor().key(), c.actor().name(), c.kind()));
            }
            return id;
        }
    }
}
