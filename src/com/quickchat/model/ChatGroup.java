package com.quickchat.model;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * A locally managed chat group. Groups are just a named set of peers:
 * messages are delivered directly to every member that is currently online.
 */
public class ChatGroup {
    private final String id;
    private String name;
    private final Map<String, String> members = new LinkedHashMap<>();

    public ChatGroup(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public synchronized String getName() {
        return name;
    }

    public synchronized void setName(String name) {
        if (name != null && !name.trim().isEmpty()) {
            this.name = name.trim();
        }
    }

    public synchronized void addMember(String peerId, String peerName) {
        if (peerId == null || peerId.trim().isEmpty()) return;
        members.put(peerId.trim(), peerName == null ? "" : peerName);
    }

    public synchronized boolean hasMember(String peerId) {
        return members.containsKey(peerId);
    }

    public synchronized void removeMember(String peerId) {
        members.remove(peerId);
    }

    public synchronized Set<String> getMemberIds() {
        return new LinkedHashSet<>(members.keySet());
    }

    public synchronized Map<String, String> getMembers() {
        return new LinkedHashMap<>(members);
    }

    public synchronized int getMemberCount() {
        return members.size();
    }

    public synchronized String getMemberName(String peerId) {
        return members.get(peerId);
    }

    /**
     * Merges another (remote) version of the group into this one.
     * Returns true when something changed and the settings should be saved.
     */
    public synchronized boolean mergeFrom(ChatGroup other) {
        boolean changed = false;
        if (other.name != null && !other.name.trim().isEmpty() && !other.name.equals(name)) {
            name = other.name.trim();
            changed = true;
        }
        for (Map.Entry<String, String> entry : other.members.entrySet()) {
            String previous = members.put(entry.getKey(), entry.getValue());
            if (previous == null || !previous.equals(entry.getValue())) {
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Format: id:base64(name),id:base64(name) - safe for names containing commas or colons.
     */
    public synchronized String serializeMembers() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : members.entrySet()) {
            if (sb.length() > 0) sb.append(',');
            sb.append(entry.getKey()).append(':').append(encode(entry.getValue()));
        }
        return sb.toString();
    }

    public static ChatGroup fromStored(String id, String name, String serializedMembers) {
        ChatGroup group = new ChatGroup(id, name);
        if (serializedMembers == null || serializedMembers.trim().isEmpty()) {
            return group;
        }
        for (String part : serializedMembers.split(",")) {
            int separator = part.indexOf(':');
            if (separator <= 0) continue;
            String peerId = part.substring(0, separator).trim();
            if (peerId.isEmpty()) continue;
            String peerName = "";
            try {
                peerName = decode(part.substring(separator + 1));
            } catch (IllegalArgumentException ignored) {
            }
            group.addMember(peerId, peerName);
        }
        return group;
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(
                (value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
