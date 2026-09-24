package dev.chronos.model;

public record IncidentInfo(long id, long ts, String user, String kind, int actions) { }
