package dev.chronos.rollback;

/** Singola operazione su un blocco: imposta stato, ripristina extra, oppure aggiunge/rimuove un oggetto. */
public final class StateOp {
    public final String world;
    public final int x, y, z;
    public final String data;     // null = non cambiare il blocco
    public final byte[] extra;    // contenuto contenitore / testo cartello
    public final byte[] item;     // per delta oggetti
    public final int amount;      // > 0 aggiunge, < 0 rimuove

    private StateOp(String world, int x, int y, int z, String data, byte[] extra, byte[] item, int amount) {
        this.world = world; this.x = x; this.y = y; this.z = z;
        this.data = data; this.extra = extra; this.item = item; this.amount = amount;
    }

    public static StateOp state(String w, int x, int y, int z, String data, byte[] extra) { return new StateOp(w, x, y, z, data, extra, null, 0); }
    public static StateOp extraOnly(String w, int x, int y, int z, byte[] extra) { return new StateOp(w, x, y, z, null, extra, null, 0); }
    public static StateOp delta(String w, int x, int y, int z, byte[] item, int amount) { return new StateOp(w, x, y, z, null, null, item, amount); }

    public int cx() { return x >> 4; }
    public int cz() { return z >> 4; }
    public String key() { return world + ':' + x + ':' + y + ':' + z; }
}
