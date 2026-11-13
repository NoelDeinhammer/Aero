package com.quickchat.i18n;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Tiny built-in localization layer. Keeping the translations in Java avoids
 * external resource files and works in both IDE runs and packaged builds.
 */
public final class I18n {
    public enum Language {
        GERMAN("de", "Deutsch"),
        ENGLISH("en", "English");

        private final String code;
        private final String displayName;

        Language(String code, String displayName) {
            this.code = code;
            this.displayName = displayName;
        }

        public String getCode() {
            return code;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum Key {
        APP_SUBTITLE,
        SETTINGS_TITLE,
        SETTINGS_SUBTITLE,
        SETTINGS_TOOLTIP,
        YOUR_IP,
        YOU,
        ONLINE,
        OFFLINE,
        GROUP_BUTTON,
        RENAME_BUTTON,
        IP_TOOLTIP,
        IP_CONNECT,
        IP_CONNECT_FAILED_TITLE,
        IP_CONNECT_FAILED,
        GROUPS_SECTION,
        ONLINE_SECTION,
        EMPTY_PEERS,
        NO_MESSAGES_GROUP,
        NO_MESSAGES,
        DELETE_GROUP_MENU,
        NOT_DELIVERED,
        UNREAD,
        MEMBER_STATUS_ONE,
        MEMBER_STATUS_MANY,
        RENAME_PROMPT,
        RENAME_TITLE,
        NAME_SAVED,
        NOTIFICATIONS_SECTION,
        NOTIFICATIONS_LABEL,
        ALWAYS_ON_TOP_LABEL,
        DISPLAY_NAME_SECTION,
        SAVE,
        SETTINGS_AUTOSAVE,
        LANGUAGE_SECTION,
        NEW_GROUP_TITLE,
        GROUP_NAME_SECTION,
        MEMBERS_SECTION,
        PASSWORD_SECTION,
        PASSWORD_HINT,
        CANCEL,
        CREATE,
        ERROR_GROUP_NAME_REQUIRED,
        ERROR_SELECT_MEMBER,
        ERROR_PASSWORD_SET,
        NO_PEERS_TITLE,
        NO_PEERS_MESSAGE,
        DELETE_GROUP_TITLE,
        DELETE_GROUP_MESSAGE,
        PASSWORD_REQUIRED_TITLE,
        PASSWORD_REQUIRED_SUBTITLE,
        UNLOCK,
        WRONG_PASSWORD,
        PASSWORD_PROTECTED,
        ENCRYPTED_MESSAGE,
        PASSWORD_FIELD,
        UNLOCK_FAILED,
        NEW_MESSAGE_TITLE,
        GROUP_LOCKED_NOTIFICATION_TITLE,
        GROUP_LOCKED_NOTIFICATION_PREVIEW,
        AERO_READY_TITLE,
        AERO_READY_BODY,
        AERO_ERROR_TITLE,
        NETWORK_ERROR,
        TRAY_OPEN,
        TRAY_SETTINGS,
        TRAY_RENAME,
        TRAY_EXIT,
        HOTKEY_TRAY_ONLY
    }

    private static volatile Language language = Language.GERMAN;
    private static final Map<Language, Map<Key, String>> TEXTS = new EnumMap<>(Language.class);

    static {
        Map<Key, String> de = new HashMap<>();
        de.put(Key.APP_SUBTITLE, "Leichtgewichtiger LAN-Chat");
        de.put(Key.SETTINGS_TITLE, "Einstellungen");
        de.put(Key.SETTINGS_SUBTITLE, "Aero anpassen");
        de.put(Key.SETTINGS_TOOLTIP, "Einstellungen");
        de.put(Key.YOUR_IP, "Deine IP: {0}");
        de.put(Key.YOU, "Du: {0}");
        de.put(Key.ONLINE, "Online");
        de.put(Key.OFFLINE, "Offline");
        de.put(Key.GROUP_BUTTON, "+ Gruppe");
        de.put(Key.RENAME_BUTTON, "Name ändern");
        de.put(Key.IP_TOOLTIP, "Freund-IP eingeben (z. B. 192.168.178.42)");
        de.put(Key.IP_CONNECT, "IP verbinden");
        de.put(Key.IP_CONNECT_FAILED_TITLE, "Verbindung fehlgeschlagen");
        de.put(Key.IP_CONNECT_FAILED, "Keine Antwort von {0}.\nPrüfe, ob Aero dort läuft und die Windows-Firewall Java erlaubt.");
        de.put(Key.GROUPS_SECTION, "GRUPPEN");
        de.put(Key.ONLINE_SECTION, "ONLINE");
        de.put(Key.EMPTY_PEERS, "Noch niemand online.\n\n" +
                "Tipp zum Testen:\n" +
                "Starte Aero ein zweites Mal mit einem anderen Namen " +
                "(z. B. --name Anna), oder öffne es auf einem zweiten PC im selben WLAN.\n\n" +
                "Mit „+ Gruppe“ kannst du mehrere Personen gleichzeitig anschreiben.");
        de.put(Key.NO_MESSAGES_GROUP, "Noch keine Nachrichten in dieser Gruppe.");
        de.put(Key.NO_MESSAGES, "Noch keine Nachrichten.\nSchreib die erste!");
        de.put(Key.DELETE_GROUP_MENU, "Gruppe löschen");
        de.put(Key.NOT_DELIVERED, "{0} (nicht zugestellt)");
        de.put(Key.UNREAD, " {0} neu ");
        de.put(Key.MEMBER_STATUS_ONE, "1 Mitglied · {0} online");
        de.put(Key.MEMBER_STATUS_MANY, "{0} Mitglieder · {1} online");
        de.put(Key.RENAME_PROMPT, "Dein Anzeigename:");
        de.put(Key.RENAME_TITLE, "Name ändern");
        de.put(Key.NAME_SAVED, "Name gespeichert.");
        de.put(Key.NOTIFICATIONS_SECTION, "BENACHRICHTIGUNGEN");
        de.put(Key.NOTIFICATIONS_LABEL, "Benachrichtigungen anzeigen");
        de.put(Key.ALWAYS_ON_TOP_LABEL, "Chat-Fenster immer im Vordergrund");
        de.put(Key.DISPLAY_NAME_SECTION, "ANZEIGENAME");
        de.put(Key.SAVE, "Speichern");
        de.put(Key.SETTINGS_AUTOSAVE, "Einstellungen werden automatisch gespeichert.<br>Speicherort: %APPDATA%\\Aero");
        de.put(Key.LANGUAGE_SECTION, "SPRACHE");
        de.put(Key.NEW_GROUP_TITLE, "Neue Gruppe");
        de.put(Key.GROUP_NAME_SECTION, "GRUPPENNAME");
        de.put(Key.MEMBERS_SECTION, "MITGLIEDER");
        de.put(Key.PASSWORD_SECTION, "PASSWORT (OPTIONAL)");
        de.put(Key.PASSWORD_HINT, "Leer lassen, um kein Passwort zu verwenden.");
        de.put(Key.CANCEL, "Abbrechen");
        de.put(Key.CREATE, "Erstellen");
        de.put(Key.ERROR_GROUP_NAME_REQUIRED, "Bitte gib einen Gruppennamen ein.");
        de.put(Key.ERROR_SELECT_MEMBER, "Wähle mindestens eine Person aus.");
        de.put(Key.ERROR_PASSWORD_SET, "Das Passwort konnte nicht gesetzt werden.");
        de.put(Key.NO_PEERS_TITLE, "Keine Personen online");
        de.put(Key.NO_PEERS_MESSAGE, "Für eine Gruppe muss mindestens eine Person online sein.");
        de.put(Key.DELETE_GROUP_TITLE, "Gruppe löschen");
        de.put(Key.DELETE_GROUP_MESSAGE, "Gruppe \"{0}\" wirklich löschen?");
        de.put(Key.PASSWORD_REQUIRED_TITLE, "Passwort erforderlich");
        de.put(Key.PASSWORD_REQUIRED_SUBTITLE, "Gib das Passwort für „{0}“ ein.");
        de.put(Key.UNLOCK, "Entsperren");
        de.put(Key.WRONG_PASSWORD, "Falsches Passwort.");
        de.put(Key.PASSWORD_PROTECTED, "passwortgeschützt");
        de.put(Key.ENCRYPTED_MESSAGE, "Verschlüsselte Nachricht");
        de.put(Key.PASSWORD_FIELD, "Passwort");
        de.put(Key.UNLOCK_FAILED, "Das Passwort konnte nicht geprüft werden.");
        de.put(Key.NEW_MESSAGE_TITLE, "{0} – Neue Nachricht");
        de.put(Key.GROUP_LOCKED_NOTIFICATION_TITLE, "{0} – Passwort erforderlich");
        de.put(Key.GROUP_LOCKED_NOTIFICATION_PREVIEW, "Neue verschlüsselte Nachricht");
        de.put(Key.AERO_READY_TITLE, "Aero bereit");
        de.put(Key.AERO_READY_BODY, "Läuft im Hintergrund – Shortcut: {0}");
        de.put(Key.AERO_ERROR_TITLE, "Aero Fehler");
        de.put(Key.NETWORK_ERROR, "Netzwerk konnte nicht gestartet werden: {0}");
        de.put(Key.TRAY_OPEN, "Chat öffnen");
        de.put(Key.TRAY_SETTINGS, "Einstellungen...");
        de.put(Key.TRAY_RENAME, "Name ändern...");
        de.put(Key.TRAY_EXIT, "Beenden");
        de.put(Key.HOTKEY_TRAY_ONLY, "Tray-Klick (nur Windows)");
        TEXTS.put(Language.GERMAN, Map.copyOf(de));

        Map<Key, String> en = new HashMap<>();
        en.put(Key.APP_SUBTITLE, "Lightweight LAN chat");
        en.put(Key.SETTINGS_TITLE, "Settings");
        en.put(Key.SETTINGS_SUBTITLE, "Customize Aero");
        en.put(Key.SETTINGS_TOOLTIP, "Settings");
        en.put(Key.YOUR_IP, "Your IP: {0}");
        en.put(Key.YOU, "You: {0}");
        en.put(Key.ONLINE, "Online");
        en.put(Key.OFFLINE, "Offline");
        en.put(Key.GROUP_BUTTON, "+ Group");
        en.put(Key.RENAME_BUTTON, "Change name");
        en.put(Key.IP_TOOLTIP, "Enter friend IP (e.g. 192.168.178.42)");
        en.put(Key.IP_CONNECT, "Connect IP");
        en.put(Key.IP_CONNECT_FAILED_TITLE, "Connection failed");
        en.put(Key.IP_CONNECT_FAILED, "No response from {0}.\nCheck that Aero is running there and that Windows Firewall allows Java.");
        en.put(Key.GROUPS_SECTION, "GROUPS");
        en.put(Key.ONLINE_SECTION, "ONLINE");
        en.put(Key.EMPTY_PEERS, "No one is online yet.\n\n" +
                "Testing tip:\n" +
                "Start Aero a second time with another name " +
                "(e.g. --name Anna), or open it on a second PC on the same Wi-Fi.\n\n" +
                "Use \u201c+ Group\u201d to message several people at once.");
        en.put(Key.NO_MESSAGES_GROUP, "No messages in this group yet.");
        en.put(Key.NO_MESSAGES, "No messages yet.\nSend the first one!");
        en.put(Key.DELETE_GROUP_MENU, "Delete group");
        en.put(Key.NOT_DELIVERED, "{0} (not delivered)");
        en.put(Key.UNREAD, " {0} new ");
        en.put(Key.MEMBER_STATUS_ONE, "1 member · {0} online");
        en.put(Key.MEMBER_STATUS_MANY, "{0} members · {1} online");
        en.put(Key.RENAME_PROMPT, "Your display name:");
        en.put(Key.RENAME_TITLE, "Change name");
        en.put(Key.NAME_SAVED, "Name saved.");
        en.put(Key.NOTIFICATIONS_SECTION, "NOTIFICATIONS");
        en.put(Key.NOTIFICATIONS_LABEL, "Show notifications");
        en.put(Key.ALWAYS_ON_TOP_LABEL, "Keep chat window always on top");
        en.put(Key.DISPLAY_NAME_SECTION, "DISPLAY NAME");
        en.put(Key.SAVE, "Save");
        en.put(Key.SETTINGS_AUTOSAVE, "Settings are saved automatically.<br>Location: %APPDATA%\\Aero");
        en.put(Key.LANGUAGE_SECTION, "LANGUAGE");
        en.put(Key.NEW_GROUP_TITLE, "New group");
        en.put(Key.GROUP_NAME_SECTION, "GROUP NAME");
        en.put(Key.MEMBERS_SECTION, "MEMBERS");
        en.put(Key.PASSWORD_SECTION, "PASSWORD (OPTIONAL)");
        en.put(Key.PASSWORD_HINT, "Leave empty to use no password.");
        en.put(Key.CANCEL, "Cancel");
        en.put(Key.CREATE, "Create");
        en.put(Key.ERROR_GROUP_NAME_REQUIRED, "Please enter a group name.");
        en.put(Key.ERROR_SELECT_MEMBER, "Select at least one person.");
        en.put(Key.ERROR_PASSWORD_SET, "The password could not be set.");
        en.put(Key.NO_PEERS_TITLE, "No one online");
        en.put(Key.NO_PEERS_MESSAGE, "At least one person must be online to create a group.");
        en.put(Key.DELETE_GROUP_TITLE, "Delete group");
        en.put(Key.DELETE_GROUP_MESSAGE, "Really delete group \"{0}\"?");
        en.put(Key.PASSWORD_REQUIRED_TITLE, "Password required");
        en.put(Key.PASSWORD_REQUIRED_SUBTITLE, "Enter the password for \u201c{0}\u201d.");
        en.put(Key.UNLOCK, "Unlock");
        en.put(Key.WRONG_PASSWORD, "Wrong password.");
        en.put(Key.PASSWORD_PROTECTED, "password protected");
        en.put(Key.ENCRYPTED_MESSAGE, "Encrypted message");
        en.put(Key.PASSWORD_FIELD, "Password");
        en.put(Key.UNLOCK_FAILED, "The password could not be verified.");
        en.put(Key.NEW_MESSAGE_TITLE, "{0} – New message");
        en.put(Key.GROUP_LOCKED_NOTIFICATION_TITLE, "{0} – Password required");
        en.put(Key.GROUP_LOCKED_NOTIFICATION_PREVIEW, "New encrypted message");
        en.put(Key.AERO_READY_TITLE, "Aero ready");
        en.put(Key.AERO_READY_BODY, "Running in the background – shortcut: {0}");
        en.put(Key.AERO_ERROR_TITLE, "Aero error");
        en.put(Key.NETWORK_ERROR, "Could not start network: {0}");
        en.put(Key.TRAY_OPEN, "Open chat");
        en.put(Key.TRAY_SETTINGS, "Settings...");
        en.put(Key.TRAY_RENAME, "Change name...");
        en.put(Key.TRAY_EXIT, "Quit");
        en.put(Key.HOTKEY_TRAY_ONLY, "Tray click (Windows only)");
        TEXTS.put(Language.ENGLISH, Map.copyOf(en));
    }

    private I18n() {
    }

    public static Language getLanguage() {
        return language;
    }

    public static void setLanguage(Language value) {
        if (value != null) {
            language = value;
        }
    }

    public static Language fromCode(String code) {
        if (code != null && code.trim().toLowerCase().startsWith("en")) {
            return Language.ENGLISH;
        }
        return Language.GERMAN;
    }

    public static String t(Key key, Object... args) {
        Map<Key, String> map = TEXTS.get(language);
        String template = map == null ? null : map.get(key);
        if (template == null) {
            map = TEXTS.get(Language.GERMAN);
            template = map == null ? key.name() : map.getOrDefault(key, key.name());
        }
        for (int i = 0; i < args.length; i++) {
            template = template.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return template;
    }
}
