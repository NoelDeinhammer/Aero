package com.quickchat.model;

import java.util.Objects;

/**
 * Represents a discovered chat peer on the local network or localhost.
 */
public class Peer {
    private final String id;
    private String name;
    private final String host;
    private final int port;
    private volatile long lastSeenMs;

    public Peer(String id, String name, String host, int port) {
        this.id = id;
        this.name = name;
        this.host = host;
        this.port = port;
        this.lastSeenMs = System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public long getLastSeenMs() {
        return lastSeenMs;
    }

    public void touch() {
        this.lastSeenMs = System.currentTimeMillis();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Peer)) return false;
        Peer peer = (Peer) o;
        return Objects.equals(id, peer.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return name + " (" + host + ":" + port + ")";
    }
}
