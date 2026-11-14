package com.quickchat.config;

import com.quickchat.model.ChatMessage;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
                if (text == null) {
                    continue;
                }
                messages.add(ChatMessage.restored(
                        id,
                        properties.getProperty(base + "sender", ""),
                        ChatMessage.truncate(text),
                        "true".equalsIgnoreCase(properties.getProperty(base + "incoming", "false")),
                        properties.getProperty(base + "time", ""),
                        properties.getProperty(base + "iv", ""),
                        properties.getProperty(base + "cipher", "")
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
                properties.setProperty(base + "sender", safe(message.getSenderName()));
                properties.setProperty(base + "text",
                        ChatMessage.truncate(safe(message.getText())));
                properties.setProperty(base + "incoming", Boolean.toString(message.isIncoming()));
                properties.setProperty(base + "time", safe(message.getFormattedTime()));
                if (message.isEncrypted()) {
                    properties.setProperty(base + "iv", message.getIv());
                    properties.setProperty(base + "cipher", message.getCipherText());
                }
            }
        }

        if (properties.isEmpty()) {
            clear();
        } else {
            write(properties);
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
