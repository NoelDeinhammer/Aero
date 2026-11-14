package com.quickchat.model;

import com.quickchat.security.GroupCrypto;

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
    private String passwordSalt = "";
    private String passwordCheck = "";
    private String passwordKey = "";

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

    public synchronized boolean isPasswordProtected() {
        return !passwordSalt.isEmpty() && !passwordCheck.isEmpty();
    }

    public synchronized boolean hasPasswordKey() {
        return !passwordKey.isEmpty();
    }

    public synchronized String getPasswordSalt() {
        return passwordSalt;
    }

    public synchronized String getPasswordCheck() {
        return passwordCheck;
    }

    public synchronized String getPasswordKey() {
        return passwordKey;
    }

    public synchronized byte[] getPasswordKeyBytes() {
        return GroupCrypto.decodeKey(passwordKey);
    }

    /**
     * Stores the full password metadata. Only the group creator knows the
     * derived key; recipients receive salt and verifier over the network and
     * start with a locked group.
     */
    public synchronized void setPasswordData(String salt, String check, String keyBase64) {
        this.passwordSalt = salt == null ? "" : salt;
        this.passwordCheck = check == null ? "" : check;
        this.passwordKey = keyBase64 == null ? "" : keyBase64;
    }

    public synchronized void setPasswordKey(String keyBase64) {
        this.passwordKey = keyBase64 == null ? "" : keyBase64;
    }

    /**
     * Adopts password metadata from a remote group description. An existing
     * local key is dropped when the remote group uses different metadata, so
     * a recreated group can never be read with a stale key.
     *
     * @return true when the local metadata changed
     */
    public synchronized boolean applyPasswordMetadata(String salt, String check) {
        String newSalt = salt == null ? "" : salt;
        String newCheck = check == null ? "" : check;
        if (newSalt.isEmpty() || newCheck.isEmpty()) {
            return false;
        }
        if (newSalt.equals(passwordSalt) && newCheck.equals(passwordCheck)) {
            return false;
        }
        passwordSalt = newSalt;
        passwordCheck = newCheck;
        passwordKey = "";
        return true;
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
        if (applyPasswordMetadata(other.passwordSalt, other.passwordCheck)) {
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
