package dev.chronos.model;

public record ActionRow(long id, long ts, String user, String world, int x, int y, int z, ActionType type,
                        String material, String oldData, String newData, long incident, int amount,
                        boolean rolledBack, byte[] extra) { }
