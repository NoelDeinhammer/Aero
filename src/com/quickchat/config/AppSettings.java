package com.quickchat.config;

import com.quickchat.model.ChatGroup;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/**
 * Small properties-file based settings store. Keeps Aero zero-dependency:
 * no database, no external library. The file lives in %APPDATA%\Aero.
 */
public final class AppSettings {
    private static final String APP_FOLDER = "Aero";

    private final Path file;
    private String displayName;
    private boolean notificationsEnabled = true;
    private boolean alwaysOnTop = false;
    private String peerId;
    private final Map<String, ChatGroup> groups = new LinkedHashMap<>();

    private AppSettings(Path file) {
        this.file = file;
    }

    /**
     * Loads the default profile when profileHint is null, otherwise a
     * name-specific profile (used for multiple instances on one PC).
     */
    public static AppSettings load(String profileHint) {
        Path file = resolveFile(profileHint);
        AppSettings settings = new AppSettings(file);
        Properties properties = new Properties();

        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                properties.load(reader);
            } catch (IOException e) {
                System.err.println("[Settings] Konnte Einstellungen nicht laden: " + e.getMessage());
            }
        }

        settings.displayName = trimToNull(properties.getProperty("displayName"));
        if (settings.displayName == null) {
            String hint = trimToNull(profileHint);
            settings.displayName = hint != null ? hint : System.getProperty("user.name", "User");
        }

        settings.notificationsEnabled =
                !"false".equalsIgnoreCase(properties.getProperty("notifications", "true"));
        settings.alwaysOnTop =
                "true".equalsIgnoreCase(properties.getProperty("alwaysOnTop", "false"));

        settings.peerId = trimToNull(properties.getProperty("peerId"));
        if (settings.peerId == null) {
            settings.peerId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        }

        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith("group.") || !key.endsWith(".name")) continue;
            String id = key.substring("group.".length(), key.length() - ".name".length());
            if (id.isEmpty()) continue;
            String name = properties.getProperty(key, "Gruppe");
            String members = properties.getProperty("group." + id + ".members", "");
            settings.groups.put(id, ChatGroup.fromStored(id, name, members));
        }

        // Make sure the file exists and contains the generated stable peer ID.
        settings.save();
        return settings;
    }

    public synchronized String getDisplayName() {
        return displayName;
    }

    public synchronized void setDisplayName(String displayName) {
        String value = trimToNull(displayName);
        if (value == null) return;
        this.displayName = value;
        save();
    }

    public synchronized boolean isNotificationsEnabled() {
        return notificationsEnabled;
    }

    public synchronized void setNotificationsEnabled(boolean enabled) {
        if (this.notificationsEnabled == enabled) return;
        this.notificationsEnabled = enabled;
        save();
    }

    public synchronized boolean isAlwaysOnTop() {
        return alwaysOnTop;
    }

    public synchronized void setAlwaysOnTop(boolean enabled) {
        if (this.alwaysOnTop == enabled) return;
        this.alwaysOnTop = enabled;
        save();
    }

    public String getPeerId() {
        return peerId;
    }

    public synchronized List<ChatGroup> getGroups() {
        return new ArrayList<>(groups.values());
    }

    public synchronized ChatGroup getGroup(String id) {
        return id == null ? null : groups.get(id);
    }

    public synchronized void addGroup(ChatGroup group) {
        if (group == null || group.getId() == null) return;
        groups.put(group.getId(), group);
        save();
    }

    public synchronized void removeGroup(String id) {
        if (id != null && groups.remove(id) != null) {
            save();
        }
    }

    public synchronized void save() {
        Properties properties = new Properties();
        properties.setProperty("displayName", displayName);
        properties.setProperty("notifications", Boolean.toString(notificationsEnabled));
        properties.setProperty("alwaysOnTop", Boolean.toString(alwaysOnTop));
        properties.setProperty("peerId", peerId);

        for (ChatGroup group : groups.values()) {
            properties.setProperty("group." + group.getId() + ".name", group.getName());
            properties.setProperty("group." + group.getId() + ".members", group.serializeMembers());
        }

        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                properties.store(writer, "Aero settings");
            }
            try {
                Files.move(temporary, file,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("[Settings] Konnte Einstellungen nicht speichern: " + e.getMessage());
        }
    }

    public Path getFile() {
        return file;
    }

    private static Path resolveFile(String profileHint) {
        Path directory = configDirectory();
        String profile = trimToNull(profileHint);
        if (profile == null) {
            return directory.resolve("settings.properties");
        }
        String safe = profile.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.length() > 40) {
            safe = safe.substring(0, 40);
        }
        return directory.resolve("instance-" + safe + ".properties");
    }

    public static Path configDirectory() {
        String appData = System.getenv("APPDATA");
        Path base;
        if (appData != null && !appData.trim().isEmpty()) {
            base = Paths.get(appData);
        } else {
            base = Paths.get(System.getProperty("user.home", "."), ".config");
        }
        return base.resolve(APP_FOLDER);
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
