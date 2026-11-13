package com.quickchat.model;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Represents a single chat message exchanged with a peer.
 */
public class ChatMessage {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final String peerId;
    private final String senderName;
    private final String text;
    private final boolean incoming;
    private final String formattedTime;

    public ChatMessage(String peerId, String senderName, String text, boolean incoming) {
        this.peerId = peerId;
        this.senderName = senderName;
        this.text = text;
        this.incoming = incoming;
        this.formattedTime = LocalTime.now().format(TIME_FORMAT);
    }

    public String getPeerId() {
        return peerId;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getText() {
        return text;
    }

    public boolean isIncoming() {
        return incoming;
    }

    public String getFormattedTime() {
        return formattedTime;
    }
}
