package com.quickchat.config;

import com.quickchat.model.ChatMessage;
import com.quickchat.model.FileAttachment;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Stores chat histories in a small properties file next to settings.properties.
 *
 * <p>Only sent and received messages are stored, no unread counters or window
 * state. Each conversation is capped so the file stays small even after long
 * use. Aero remains dependency-free: no database and no external library.</p>
 */
public final class ChatHistoryStore {
    private static final String PREFIX = "message.";
    private static final int MAX_MESSAGES_PER_CONVERSATION = 200;

    private final Path file;

    public ChatHistoryStore(Path file) {
        this.file = file;
    }

    public Path getFile() {
        return file;
    }

    /**
     * Loads every stored conversation. Missing or unreadable files simply
     * produce an empty history; a broken file must never prevent startup.
     */
    public Map<String, List<ChatMessage>> load() {
        Map<String, List<ChatMessage>> histories = new LinkedHashMap<>();
        if (!Files.isRegularFile(file)) {
            return histories;
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("[History] Konnte Nachrichten nicht laden: " + e.getMessage());
            return histories;
        }

        for (String property : properties.stringPropertyNames()) {
            if (!property.startsWith(PREFIX) || !property.endsWith(".count")) {
                continue;
            }

            String conversation = property.substring(PREFIX.length(),
                    property.length() - ".count".length());
            int separator = conversation.indexOf('.');
            if (separator <= 0 || separator == conversation.length() - 1) {
                continue;
            }

            String type = conversation.substring(0, separator);
            String id = conversation.substring(separator + 1);
            if (!("peer".equals(type) || "group".equals(type))) {
                continue;
            }

            int count;
            try {
                count = Integer.parseInt(properties.getProperty(property, "0").trim());
            } catch (NumberFormatException e) {
                continue;
            }

            count = Math.min(Math.max(count, 0), MAX_MESSAGES_PER_CONVERSATION);
            List<ChatMessage> messages = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                String base = messageKey(type, id, index);
                String text = properties.getProperty(base + "text");
                boolean incoming = "true".equalsIgnoreCase(
                        properties.getProperty(base + "incoming", "false"));
                FileAttachment attachment = readAttachment(properties, base, incoming);
                if (text == null && attachment == null) {
                    continue;
                }
                boolean edited = "true".equalsIgnoreCase(
                        properties.getProperty(base + "edited", "false"));
                boolean notDelivered = "true".equalsIgnoreCase(
                        properties.getProperty(base + "notDelivered", "false"));
                messages.add(ChatMessage.restored(
                        properties.getProperty(base + "id"),
                        id,
                        properties.getProperty(base + "senderId", ""),
                        properties.getProperty(base + "sender", ""),
                        ChatMessage.truncate(text == null ? "" : text),
                        incoming,
                        properties.getProperty(base + "time", ""),
                        properties.getProperty(base + "iv", ""),
                        properties.getProperty(base + "cipher", ""),
                        attachment,
                        edited,
                        notDelivered
                ));
            }

            if (!messages.isEmpty()) {
                histories.put(type + ":" + id, messages);
            }
        }
        return histories;
    }

    /**
     * Saves a snapshot of all conversations. Only the newest messages of each
     * conversation are kept.
     */
    public synchronized void save(Map<String, List<ChatMessage>> histories) {
        Properties properties = new Properties();

        for (Map.Entry<String, List<ChatMessage>> entry : histories.entrySet()) {
            String key = entry.getKey();
            int separator = key == null ? -1 : key.indexOf(':');
            if (separator <= 0 || separator == key.length() - 1) {
                continue;
            }

            String type = key.substring(0, separator);
            String id = key.substring(separator + 1);
            if (!("peer".equals(type) || "group".equals(type))) {
                continue;
            }

            List<ChatMessage> messages = entry.getValue();
            if (messages == null || messages.isEmpty()) {
                continue;
            }

            int first = Math.max(0, messages.size() - MAX_MESSAGES_PER_CONVERSATION);
            int count = messages.size() - first;
            properties.setProperty(conversationKey(type, id) + ".count", Integer.toString(count));

            for (int index = 0; index < count; index++) {
                ChatMessage message = messages.get(first + index);
                String base = messageKey(type, id, index);
                properties.setProperty(base + "id", safe(message.getId()));
                setIfNotEmpty(properties, base + "senderId", message.getSenderId());
                properties.setProperty(base + "sender", safe(message.getSenderName()));
                properties.setProperty(base + "text",
                        ChatMessage.truncate(safe(message.getText())));
                properties.setProperty(base + "incoming", Boolean.toString(message.isIncoming()));
                properties.setProperty(base + "time", safe(message.getFormattedTime()));
                if (message.isEdited()) {
                    properties.setProperty(base + "edited", "true");
                }
                if (message.isNotDelivered()) {
                    properties.setProperty(base + "notDelivered", "true");
                }
                if (message.isEncrypted()) {
                    properties.setProperty(base + "iv", message.getIv());
                    properties.setProperty(base + "cipher", message.getCipherText());
                }
                if (message.hasAttachment()) {
                    writeAttachment(properties, base, message.getAttachment());
                }
            }
        }

        if (properties.isEmpty()) {
            clear();
        } else {
            write(properties);
        }
    }

    private static void writeAttachment(Properties properties, String base,
                                        FileAttachment attachment) {
        properties.setProperty(base + "att", "true");
        properties.setProperty(base + "att.id", safe(attachment.getTransferId()));
        properties.setProperty(base + "att.name", safe(attachment.getFileName()));
        properties.setProperty(base + "att.size", Long.toString(attachment.getFileSize()));
        properties.setProperty(base + "att.image", Boolean.toString(attachment.isImage()));
        properties.setProperty(base + "att.status",
                attachment.getStatus() == null ? "" : attachment.getStatus().name());
        if (attachment.getLocalPath() != null) {
            properties.setProperty(base + "att.path", attachment.getLocalPath().toString());
        }
        setIfNotEmpty(properties, base + "att.metaIv", attachment.getMetaIv());
        setIfNotEmpty(properties, base + "att.metaCipher", attachment.getMetaCipherText());
        properties.setProperty(base + "att.pending",
                Boolean.toString(attachment.isEncryptedPendingUnlock()));
        properties.setProperty(base + "att.encrypted",
                Boolean.toString(attachment.isEncryptedOffer()));
        setIfNotEmpty(properties, base + "att.offerPeer", attachment.getOfferPeerId());
        setIfNotEmpty(properties, base + "att.offerHost", attachment.getOfferHost());
        properties.setProperty(base + "att.offerPort",
                Integer.toString(Math.max(0, attachment.getOfferPort())));
        setIfNotEmpty(properties, base + "att.offerGroup", attachment.getOfferGroupId());
    }

    private static FileAttachment readAttachment(Properties properties, String base,
                                                 boolean incoming) {
        if (!"true".equalsIgnoreCase(properties.getProperty(base + "att", "false"))) {
            return null;
        }

        String transferId = properties.getProperty(base + "att.id", "");
        String fileName = properties.getProperty(base + "att.name", "");
        long fileSize = parseLong(properties.getProperty(base + "att.size"), 0L);
        boolean image = Boolean.parseBoolean(properties.getProperty(base + "att.image", "false"))
                || FileAttachment.isImageFileName(fileName);
        Path localPath = parsePath(properties.getProperty(base + "att.path"));
        FileAttachment.Status storedStatus = parseStatus(
                properties.getProperty(base + "att.status"));
        boolean pending = Boolean.parseBoolean(
                properties.getProperty(base + "att.pending", "false"));
        boolean encryptedOffer = Boolean.parseBoolean(
                properties.getProperty(base + "att.encrypted", "false"));
        String metaIv = emptyToNull(properties.getProperty(base + "att.metaIv"));
        String metaCipher = emptyToNull(properties.getProperty(base + "att.metaCipher"));

        FileAttachment attachment;
        if (encryptedOffer || (metaIv != null && metaCipher != null)) {
            attachment = FileAttachment.encryptedIncomingOffer(
                    transferId, fileSize, metaIv, metaCipher);
            attachment.setFileName(fileName);
            attachment.setImage(image);
        } else {
            attachment = new FileAttachment(transferId, fileName, fileSize, image,
                    localPath, storedStatus);
        }
        attachment.setLocalPath(localPath);
        attachment.setEncryptedPendingUnlock(pending);
        attachment.setStatus(statusForRestore(storedStatus, localPath, incoming));
        attachment.setOfferSource(
                emptyToNull(properties.getProperty(base + "att.offerPeer")),
                emptyToNull(properties.getProperty(base + "att.offerHost")),
                (int) parseLong(properties.getProperty(base + "att.offerPort"), 0L),
                emptyToNull(properties.getProperty(base + "att.offerGroup")),
                encryptedOffer
        );
        if (attachment.getStatus() == FileAttachment.Status.COMPLETED) {
            attachment.setBytesTransferred(fileSize);
        }
        return attachment;
    }

    /**
     * Transfers cannot survive a restart. Local files that are still present
     * count as completed; incoming offers can be downloaded again while the
     * sender is online.
     */
    private static FileAttachment.Status statusForRestore(FileAttachment.Status stored,
                                                          Path localPath,
                                                          boolean incoming) {
        boolean localFileExists = localPath != null && Files.isRegularFile(localPath);
        if (stored == FileAttachment.Status.TRANSFERRING) {
            if (localFileExists) {
                return FileAttachment.Status.COMPLETED;
            }
            return incoming ? FileAttachment.Status.AVAILABLE : FileAttachment.Status.FAILED;
        }
        if (stored == FileAttachment.Status.COMPLETED && !localFileExists) {
            // The transfer itself finished; only the local copy is gone.
            // Incoming files can be fetched again while the sender is online.
            return incoming ? FileAttachment.Status.AVAILABLE
                    : FileAttachment.Status.COMPLETED;
        }
        return stored == null ? FileAttachment.Status.AVAILABLE : stored;
    }

    private static FileAttachment.Status parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return FileAttachment.Status.AVAILABLE;
        }
        try {
            return FileAttachment.Status.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return FileAttachment.Status.AVAILABLE;
        }
    }

    private static Path parsePath(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Paths.get(value);
        } catch (InvalidPathException e) {
            return null;
        }
    }

    private static long parseLong(String value, long fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private static void setIfNotEmpty(Properties properties, String key, String value) {
        if (value != null && !value.isEmpty()) {
            properties.setProperty(key, value);
        }
    }

    /**
     * Deletes the saved history, for example when the user turns the setting off.
     */
    public synchronized void clear() {
        try {
            Files.deleteIfExists(file);
            Files.deleteIfExists(file.resolveSibling(file.getFileName() + ".tmp"));
        } catch (IOException e) {
            System.err.println("[History] Konnte Nachrichten nicht löschen: " + e.getMessage());
        }
    }

    private void write(Properties properties) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                properties.store(writer, "Aero chat history");
            }
            try {
                Files.move(temporary, file,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("[History] Konnte Nachrichten nicht speichern: " + e.getMessage());
        }
    }

    private static String conversationKey(String type, String id) {
        return PREFIX + type + "." + id;
    }

    private static String messageKey(String type, String id, int index) {
        return conversationKey(type, id) + "." + index + ".";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
