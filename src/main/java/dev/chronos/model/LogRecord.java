package dev.chronos.model;

/** Record accodati dai listener e scritti in batch dal writer asincrono. */
public sealed interface LogRecord {

    record Action(long time, String userKey, String userName, String world, int x, int y, int z,
                  ActionType type, String material, String oldData, String newData, byte[] extra,
                  long incident, int amount) implements LogRecord { }

    record Message(long time, String userKey, String userName, String world, int x, int y, int z,
                   MessageType type, String text) implements LogRecord { }

    record Incident(long id, long time, String userKey, String userName, String kind) implements LogRecord { }

    record Snapshot(long time, String userKey, String userName, String world, int x, int y, int z, int reason,
                    double health, int food, int level, float exp, byte[] inventory, byte[] ender) implements LogRecord { }
}
