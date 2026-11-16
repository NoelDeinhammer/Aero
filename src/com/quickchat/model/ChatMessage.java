package com.quickchat.model;

import com.quickchat.i18n.I18n;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Represents a single chat message exchanged with a peer.
 */
public class ChatMessage {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Hard limit for a single message. It keeps the network protocol small
     * and prevents very large texts from freezing the Swing UI.
     */
    public static final int MAX_TEXT_LENGTH = 2000;

    private final String id;
    private final String peerId;
    private final String senderId;
    private final String senderName;
    private String text;
    private final boolean incoming;
    private final String formattedTime;
    private String iv;
    private String cipherText;
    private final FileAttachment attachment;
    private boolean edited;
    private boolean notDelivered;

    public ChatMessage(String peerId, String senderName, String text, boolean incoming) {
        this(null, peerId, null, senderName, text, incoming, null, null, null, false, false);
    }

    public ChatMessage(String peerId, String senderName, String text, boolean incoming,
                       String iv, String cipherText) {
        this(null, peerId, null, senderName, text, incoming, iv, cipherText, null, false, false);
    }

    public ChatMessage(String peerId, String senderName, String text, boolean incoming,
                       FileAttachment attachment) {
        this(null, peerId, null, senderName, text, incoming, null, null, attachment, false, false);
    }

    public ChatMessage(String peerId, String senderName, String text, boolean incoming,
                       String iv, String cipherText, FileAttachment attachment) {
        this(null, peerId, null, senderName, text, incoming, iv, cipherText, attachment, false, false);
    }

    private ChatMessage(String id, String peerId, String senderId, String senderName, String text,
                        boolean incoming, String iv, String cipherText, FileAttachment attachment,
                        boolean edited, boolean notDelivered) {
        this(id, peerId, senderId, senderName, text, incoming,
                LocalTime.now().format(TIME_FORMAT), iv, cipherText, attachment, edited, notDelivered);
    }

    private ChatMessage(String id, String peerId, String senderId, String senderName, String text,
                        boolean incoming, String formattedTime, String iv, String cipherText,
                        FileAttachment attachment, boolean edited, boolean notDelivered) {
        this.id = normalizeId(id, attachment);
        this.peerId = peerId == null ? "" : peerId;
        this.senderId = senderId == null ? "" : senderId;
        this.senderName = senderName == null ? "" : senderName;
        this.text = text == null ? "" : text;
        this.incoming = incoming;
        this.formattedTime = formattedTime;
        this.iv = iv == null || iv.isEmpty() ? null : iv;
        this.cipherText = cipherText == null || cipherText.isEmpty() ? null : cipherText;
        this.attachment = attachment;
        this.edited = edited;
        this.notDelivered = notDelivered;
    }

    public static ChatMessage outgoing(String peerId, String senderId, String senderName,
                                       String text, boolean notDelivered) {
        return new ChatMessage(null, peerId, senderId, senderName, text, false,
                null, null, null, false, notDelivered);
    }

    public static ChatMessage outgoing(String peerId, String senderId, String senderName,
                                       String text, FileAttachment attachment) {
        return new ChatMessage(null, peerId, senderId, senderName, text, false,
                null, null, attachment, false, false);
    }

    public static ChatMessage incoming(String id, String peerId, String senderId,
                                       String senderName, String text) {
        return new ChatMessage(id, peerId, senderId, senderName, text, true,
                null, null, null, false, false);
    }

    public static ChatMessage incomingWithAttachment(String id, String peerId, String senderId,
                                                     String senderName, String caption,
                                                     FileAttachment attachment) {
        return new ChatMessage(id, peerId, senderId, senderName, caption, true,
                null, null, attachment, false, false);
    }

    /**
     * Recreates a message from the local history file, including its original
     * time. Encrypted group messages keep their IV and cipher text so they can
     * be decrypted again once the group password is known.
     */
    public static ChatMessage restored(String peerId, String senderName, String text,
                                       boolean incoming, String formattedTime,
                                       String iv, String cipherText) {
        return restored(null, peerId, null, senderName, text, incoming, formattedTime,
                iv, cipherText, null, false, false);
    }

    /**
     * Recreates a message with an attached file from the local history file.
     * The attachment keeps its metadata so the file card survives a restart.
     */
    public static ChatMessage restored(String peerId, String senderName, String text,
                                       boolean incoming, String formattedTime,
                                       String iv, String cipherText,
                                       FileAttachment attachment) {
        return restored(null, peerId, null, senderName, text, incoming, formattedTime,
                iv, cipherText, attachment, false, false);
    }

    public static ChatMessage restored(String id, String peerId, String senderId, String senderName,
                                       String text, boolean incoming, String formattedTime,
                                       String iv, String cipherText, FileAttachment attachment,
                                       boolean edited, boolean notDelivered) {
        String time = formattedTime != null
                && formattedTime.matches("\\d{2}:\\d{2}")
                ? formattedTime
                : LocalTime.now().format(TIME_FORMAT);
        return new ChatMessage(
                id,
                peerId,
                senderId,
                senderName,
                text,
                incoming,
                time,
                iv,
                cipherText,
                attachment,
                edited,
                notDelivered);
    }

    /**
     * Creates a message whose payload is still encrypted. The placeholder is
     * replaced with the plaintext once the group password has been entered.
     */
    public static ChatMessage encryptedIncoming(String peerId, String senderName,
                                                String iv, String cipherText) {
        return encryptedIncoming(null, peerId, null, senderName, iv, cipherText);
    }

    public static ChatMessage encryptedIncoming(String id, String peerId, String senderId,
                                                String senderName, String iv, String cipherText) {
        return new ChatMessage(id, peerId, senderId, senderName,
                I18n.t(I18n.Key.ENCRYPTED_MESSAGE), true, iv, cipherText, null, false, false);
    }

    /**
     * Creates an incoming group file message whose metadata and file contents are encrypted.
     */
    public static ChatMessage encryptedFileIncoming(String peerId, String senderName,
                                                    FileAttachment attachment) {
        return encryptedFileIncoming(null, peerId, null, senderName, attachment);
    }

    public static ChatMessage encryptedFileIncoming(String id, String peerId, String senderId,
                                                    String senderName, FileAttachment attachment) {
        return new ChatMessage(id, peerId, senderId, senderName,
                I18n.t(I18n.Key.ENCRYPTED_FILE), true, null, null, attachment, false, false);
    }

    /**
     * Shortens oversized text, for example from an older peer that does not
     * enforce the limit yet. The ellipsis makes the truncation visible.
     */
    public static String truncate(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() <= MAX_TEXT_LENGTH) {
            return text;
        }
        int end = MAX_TEXT_LENGTH;
        // Do not split an emoji (surrogate pair) at the boundary.
        if (Character.isHighSurrogate(text.charAt(end - 1))
                && Character.isLowSurrogate(text.charAt(end))) {
            end--;
        }
        return text.substring(0, end) + "\u2026";
    }

    private static String normalizeId(String id, FileAttachment attachment) {
        if (id != null && id.matches("[A-Za-z0-9_-]{1,64}")) {
            return id;
        }
        if (attachment != null && attachment.getTransferId() != null
                && attachment.getTransferId().matches("[A-Za-z0-9_-]{1,64}")) {
            return attachment.getTransferId();
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public String getId() {
        return id;
    }

    public String getPeerId() {
        return peerId;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getSenderName() {
        return senderName;
    }

    public synchronized String getText() {
        return text;
    }

    public synchronized void setDecryptedText(String text) {
        this.text = text == null ? "" : text;
    }

    public synchronized void editText(String newText) {
        this.text = truncate(newText);
        this.edited = true;
    }

    public synchronized void updateEncryptedPayload(String iv, String cipherText,
                                                    String decryptedText, boolean markEdited) {
        this.iv = (iv == null || iv.isEmpty()) ? null : iv;
        this.cipherText = (cipherText == null || cipherText.isEmpty()) ? null : cipherText;
        if (decryptedText != null) {
            this.text = truncate(decryptedText);
        }
        if (markEdited) {
            this.edited = true;
        }
    }

    public synchronized boolean isEdited() {
        return edited;
    }

    public synchronized void setEdited(boolean edited) {
        this.edited = edited;
    }

    public synchronized boolean isNotDelivered() {
        return notDelivered;
    }

    public synchronized void setNotDelivered(boolean notDelivered) {
        this.notDelivered = notDelivered;
    }

    public boolean isIncoming() {
        return incoming;
    }

    public String getFormattedTime() {
        return formattedTime;
    }

    public synchronized boolean isEncrypted() {
        return cipherText != null && !cipherText.isEmpty()
                && iv != null && !iv.isEmpty();
    }

    public synchronized String getIv() {
        return iv;
    }

    public synchronized String getCipherText() {
        return cipherText;
    }

    public FileAttachment getAttachment() {
        return attachment;
    }

    public boolean hasAttachment() {
        return attachment != null;
    }
}
