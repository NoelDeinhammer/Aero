package com.quickchat.model;

import com.quickchat.i18n.I18n;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Represents a single chat message exchanged with a peer.
 */
public class ChatMessage {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final String peerId;
    private final String senderName;
    private String text;
    private final boolean incoming;
    private final String formattedTime;
    private final String iv;
    private final String cipherText;

    public ChatMessage(String peerId, String senderName, String text, boolean incoming) {
        this(peerId, senderName, text, incoming, null, null);
    }

    public ChatMessage(String peerId, String senderName, String text, boolean incoming,
                       String iv, String cipherText) {
        this.peerId = peerId;
        this.senderName = senderName;
        this.text = text;
        this.incoming = incoming;
        this.formattedTime = LocalTime.now().format(TIME_FORMAT);
        this.iv = iv;
        this.cipherText = cipherText;
    }

    /**
     * Creates a message whose payload is still encrypted. The placeholder is
     * replaced with the plaintext once the group password has been entered.
     */
    public static ChatMessage encryptedIncoming(String peerId, String senderName,
                                                String iv, String cipherText) {
        return new ChatMessage(peerId, senderName,
                I18n.t(I18n.Key.ENCRYPTED_MESSAGE), true, iv, cipherText);
    }

    public String getPeerId() {
        return peerId;
    }

    public String getSenderName() {
        return senderName;
    }

    public synchronized String getText() {
        return text;
    }

    public synchronized void setDecryptedText(String text) {
        this.text = text;
    }

    public boolean isIncoming() {
        return incoming;
    }

    public String getFormattedTime() {
        return formattedTime;
    }

    public boolean isEncrypted() {
        return cipherText != null && !cipherText.isEmpty()
                && iv != null && !iv.isEmpty();
    }

    public String getIv() {
        return iv;
    }

    public String getCipherText() {
        return cipherText;
    }
}
