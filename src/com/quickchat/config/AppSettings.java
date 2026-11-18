package com.quickchat.config;

import com.quickchat.i18n.I18n;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/**
 * Small properties-file based settings store. Keeps Aero zero-dependency:
 * no database, no external library. The file lives in %APPDATA%\Aero.
 */
public final class AppSettings {
    private static final String APP_FOLDER = "Aero";
    private static final String DEFAULT_BG_HEX = "#18191E";
    private static final String DEFAULT_ACCENT_HEX = "#5865F2";
    private static final String DEFAULT_BUBBLE_OUT_HEX = "#5865F2";
    private static final String DEFAULT_BUBBLE_IN_HEX = "#2D303A";

    private final Path file;
    private String displayName;
    private boolean notificationsEnabled = true;
    private boolean alwaysOnTop = false;
    private boolean keepMessages = false;
    private boolean autoDownloadImages = true;
    private I18n.Language language = I18n.Language.GERMAN;
    private String peerId;
    private int windowWidth = 0;
    private int windowHeight = 0;
    private String themeBgWindow = DEFAULT_BG_HEX;
    private String themeAccent = DEFAULT_ACCENT_HEX;
    private String themeBubbleOut = DEFAULT_BUBBLE_OUT_HEX;
    private String themeBubbleIn = DEFAULT_BUBBLE_IN_HEX;
    private final Map<String, ChatGroup> groups = new LinkedHashMap<>();
    private final Set<String> deletedGroups = new LinkedHashSet<>();

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
        settings.keepMessages =
                "true".equalsIgnoreCase(properties.getProperty("keepMessages", "false"));
        settings.autoDownloadImages =
                !"false".equalsIgnoreCase(properties.getProperty("autoDownloadImages", "true"));
        settings.language = I18n.fromCode(properties.getProperty("language", "de"));
        settings.windowWidth = parsePositiveInt(properties.getProperty("windowWidth"));
        settings.windowHeight = parsePositiveInt(properties.getProperty("windowHeight"));
        settings.themeBgWindow = normalizeHex(properties.getProperty("theme.bgWindow"), DEFAULT_BG_HEX);
        settings.themeAccent = normalizeHex(properties.getProperty("theme.accent"), DEFAULT_ACCENT_HEX);
        settings.themeBubbleOut = normalizeHex(properties.getProperty("theme.bubbleOut"), DEFAULT_BUBBLE_OUT_HEX);
        settings.themeBubbleIn = normalizeHex(properties.getProperty("theme.bubbleIn"), DEFAULT_BUBBLE_IN_HEX);

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
            ChatGroup group = ChatGroup.fromStored(id, name, members);
            group.setPasswordData(
                    properties.getProperty("group." + id + ".salt", ""),
                    properties.getProperty("group." + id + ".check", ""),
                    properties.getProperty("group." + id + ".key", ""));
            settings.groups.put(id, group);
        }

        String deleted = properties.getProperty("deletedGroups", "");
        for (String id : deleted.split(",")) {
            String trimmed = id.trim();
            if (!trimmed.isEmpty()) {
                settings.deletedGroups.add(trimmed);
            }
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

    public synchronized boolean isKeepMessages() {
        return keepMessages;
    }

    public synchronized void setKeepMessages(boolean enabled) {
        if (this.keepMessages == enabled) return;
        this.keepMessages = enabled;
        save();
    }

    public synchronized boolean isAutoDownloadImages() {
        return autoDownloadImages;
    }

    public synchronized void setAutoDownloadImages(boolean enabled) {
        if (this.autoDownloadImages == enabled) return;
        this.autoDownloadImages = enabled;
        save();
    }

    public synchronized I18n.Language getLanguage() {
        return language;
    }

    public synchronized void setLanguage(I18n.Language language) {
        if (language == null || this.language == language) return;
        this.language = language;
        save();
    }

    public String getPeerId() {
        return peerId;
    }

    public synchronized int getWindowWidth() {
        return windowWidth;
    }

    public synchronized int getWindowHeight() {
        return windowHeight;
    }

    public synchronized void setWindowSize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        if (this.windowWidth == width && this.windowHeight == height) return;
        this.windowWidth = width;
        this.windowHeight = height;
        save();
    }

    public synchronized String getThemeBgWindow() {
        return themeBgWindow;
    }

    public synchronized String getThemeAccent() {
        return themeAccent;
    }

    public synchronized String getThemeBubbleOut() {
        return themeBubbleOut;
    }

    public synchronized String getThemeBubbleIn() {
        return themeBubbleIn;
    }

    public synchronized void setThemeColors(String bgWindow, String accent, String bubbleOut, String bubbleIn) {
        String bg = normalizeHex(bgWindow, DEFAULT_BG_HEX);
        String ac = normalizeHex(accent, DEFAULT_ACCENT_HEX);
        String bOut = normalizeHex(bubbleOut, DEFAULT_BUBBLE_OUT_HEX);
        String bIn = normalizeHex(bubbleIn, DEFAULT_BUBBLE_IN_HEX);
        if (bg.equals(this.themeBgWindow)
                && ac.equals(this.themeAccent)
                && bOut.equals(this.themeBubbleOut)
                && bIn.equals(this.themeBubbleIn)) {
            return;
        }
        this.themeBgWindow = bg;
        this.themeAccent = ac;
        this.themeBubbleOut = bOut;
        this.themeBubbleIn = bIn;
        save();
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

    /**
     * Deletes a group and remembers its ID permanently. Incoming messages for
     * a deleted group are ignored, so another member writing into it can never
     * make it reappear.
     */
    public synchronized void deleteGroup(String id) {
        if (id == null || id.isEmpty()) {
            return;
        }
        boolean removed = groups.remove(id) != null;
        boolean remembered = deletedGroups.add(id);
        if (removed || remembered) {
            save();
        }
    }

    public synchronized boolean isGroupDeleted(String id) {
        return id != null && deletedGroups.contains(id);
    }

    public synchronized void save() {
        Properties properties = new Properties();
        properties.setProperty("displayName", displayName);
        properties.setProperty("notifications", Boolean.toString(notificationsEnabled));
        properties.setProperty("alwaysOnTop", Boolean.toString(alwaysOnTop));
        properties.setProperty("keepMessages", Boolean.toString(keepMessages));
        properties.setProperty("autoDownloadImages", Boolean.toString(autoDownloadImages));
        properties.setProperty("language", language.getCode());
        properties.setProperty("peerId", peerId);
        properties.setProperty("theme.bgWindow", themeBgWindow);
        properties.setProperty("theme.accent", themeAccent);
        properties.setProperty("theme.bubbleOut", themeBubbleOut);
        properties.setProperty("theme.bubbleIn", themeBubbleIn);
        if (windowWidth > 0 && windowHeight > 0) {
            properties.setProperty("windowWidth", Integer.toString(windowWidth));
            properties.setProperty("windowHeight", Integer.toString(windowHeight));
        }
        if (!deletedGroups.isEmpty()) {
            properties.setProperty("deletedGroups", String.join(",", deletedGroups));
        }

        for (ChatGroup group : groups.values()) {
            properties.setProperty("group." + group.getId() + ".name", group.getName());
            properties.setProperty("group." + group.getId() + ".members", group.serializeMembers());
            if (group.isPasswordProtected()) {
                properties.setProperty("group." + group.getId() + ".salt", group.getPasswordSalt());
                properties.setProperty("group." + group.getId() + ".check", group.getPasswordCheck());
                properties.setProperty("group." + group.getId() + ".key", group.getPasswordKey());
            }
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

    /**
     * Saved chat history lives next to the settings file and follows the same
     * profile name, so multiple instances never overwrite each other.
     */
    public Path getHistoryFile() {
        return file.resolveSibling(profileBaseName() + "-messages.properties");
    }

    /**
     * Persistent media cache folder for auto-downloaded images when Keep Messages is enabled.
     */
    public Path getMediaDir() {
        return file.resolveSibling(profileBaseName() + "-media");
    }

    private String profileBaseName() {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
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

    private static int parsePositiveInt(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String normalizeHex(String raw, String fallback) {
        if (raw == null) return fallback;
        String t = raw.trim();
        if (t.startsWith("#")) t = t.substring(1);
        if (!t.matches("[0-9A-Fa-f]{6}")) return fallback;
        return "#" + t.toUpperCase();
    }
}
