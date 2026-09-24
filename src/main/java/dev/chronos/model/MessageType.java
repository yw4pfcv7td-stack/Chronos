package dev.chronos.model;

public enum MessageType {
    CHAT(0), COMMAND(1), LOGIN(2), LOGOUT(3);

    public final int id;

    MessageType(int id) { this.id = id; }
}
