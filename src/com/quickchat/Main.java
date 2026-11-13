package com.quickchat;

import com.quickchat.config.AppSettings;
import com.quickchat.hotkey.GlobalHotkeyListener;
import com.quickchat.i18n.I18n;
import com.quickchat.model.ChatGroup;
import com.quickchat.model.ChatMessage;
import com.quickchat.model.Peer;
import com.quickchat.net.PeerNetwork;
import com.quickchat.ui.ChatPopupWindow;
import com.quickchat.ui.NotificationPopup;
import com.quickchat.ui.Theme;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.AWTException;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Entry point for Aero.
 * Runs in the background (System Tray) and opens a compact floating chat
 * window via global hotkey.
 */
public class Main {
    public static void main(String[] args) {
        String profileName = parseNameArg(args);
        AppSettings settings = AppSettings.load(profileName);
        I18n.setLanguage(settings.getLanguage());
        String initialName = settings.getDisplayName();

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }

            AtomicReference<ChatPopupWindow> windowRef = new AtomicReference<>();
            AtomicReference<NotificationPopup> notifRef = new AtomicReference<>();
            AtomicReference<TrayIcon> trayRef = new AtomicReference<>();
            AtomicBoolean startupNoticeShown = new AtomicBoolean(false);

            PeerNetwork network = new PeerNetwork(settings.getPeerId(), initialName, new PeerNetwork.Listener() {
                @Override
                public void onPeersUpdated(List<Peer> peers) {
                    SwingUtilities.invokeLater(() -> {
                        ChatPopupWindow win = windowRef.get();
                        if (win != null) {
                            win.updatePeers(peers);
                        }
                    });
                }

                @Override
                public void onMessageReceived(Peer sender, ChatMessage message) {
                    SwingUtilities.invokeLater(() -> {
                        ChatPopupWindow win = windowRef.get();
                        NotificationPopup notif = notifRef.get();
                        if (win == null) return;

                        win.addIncomingMessage(sender, message);
                        if (notif != null && settings.isNotificationsEnabled() && !win.isChattingWith(sender)) {
                            notif.showNotification(
                                    I18n.t(I18n.Key.NEW_MESSAGE_TITLE, sender.getName()),
                                    message.getText(),
                                    () -> win.openChatWith(sender)
                            );
                        }
                    });
                }

                @Override
                public void onGroupMessageReceived(Peer sender, ChatGroup group, ChatMessage message) {
                    SwingUtilities.invokeLater(() -> {
                        ChatPopupWindow win = windowRef.get();
                        NotificationPopup notif = notifRef.get();
                        if (win == null) return;

                        ChatGroup local = win.addIncomingGroupMessage(sender, group, message);
                        if (local == null) return;
                        if (notif != null && settings.isNotificationsEnabled() && !win.isChattingWithGroup(local)) {
                            boolean locked = local.isPasswordProtected() && !local.hasPasswordKey();
                            String title = locked
                                    ? I18n.t(I18n.Key.GROUP_LOCKED_NOTIFICATION_TITLE, local.getName())
                                    : local.getName() + " • " + sender.getName();
                            String preview = locked
                                    ? I18n.t(I18n.Key.GROUP_LOCKED_NOTIFICATION_PREVIEW)
                                    : message.getText();
                            notif.showNotification(title, preview, () -> win.openGroupChat(local));
                        }
                    });
                }

                @Override
                public void onGroupDeleted(Peer sender, String groupId) {
                    SwingUtilities.invokeLater(() -> {
                        ChatPopupWindow win = windowRef.get();
                        NotificationPopup notif = notifRef.get();
                        if (win == null) return;

                        ChatGroup deleted = win.handleRemoteGroupDeletion(sender, groupId);
                        if (deleted != null && notif != null && settings.isNotificationsEnabled()) {
                            notif.showNotification(
                                    I18n.t(I18n.Key.GROUP_DELETED_TITLE, deleted.getName()),
                                    I18n.t(I18n.Key.GROUP_DELETED_PREVIEW, sender.getName()),
                                    null
                            );
                        }
                    });
                }
            });

            ChatPopupWindow chatWindow = new ChatPopupWindow(network, settings);
            windowRef.set(chatWindow);
            chatWindow.setNameChangeListener(name -> {
                TrayIcon icon = trayRef.get();
                if (icon != null) {
                    icon.setToolTip("Aero – " + name);
                }
            });

            NotificationPopup notificationPopup = new NotificationPopup();
            notifRef.set(notificationPopup);

            GlobalHotkeyListener hotkeyListener = new GlobalHotkeyListener(
                    chatWindow::toggleWindow,
                    shortcut -> {
                        chatWindow.setShortcutText(shortcut);
                        if (startupNoticeShown.compareAndSet(false, true)
                                && settings.isNotificationsEnabled()) {
                            notificationPopup.showNotification(
                                    I18n.t(I18n.Key.AERO_READY_TITLE),
                                    I18n.t(I18n.Key.AERO_READY_BODY, shortcut),
                                    null
                            );
                        }
                    }
            );

            chatWindow.setLanguageChangeListener(() -> {
                TrayIcon icon = trayRef.get();
                if (icon != null) {
                    icon.setPopupMenu(buildTrayMenu(network, chatWindow, hotkeyListener, trayRef));
                }
            });

            try {
                network.start();
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null,
                        I18n.t(I18n.Key.NETWORK_ERROR, e.getMessage()),
                        I18n.t(I18n.Key.AERO_ERROR_TITLE), JOptionPane.ERROR_MESSAGE);
                System.exit(1);
                return;
            }

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                hotkeyListener.stop();
                network.stop();
            }, "aero-shutdown"));

            hotkeyListener.start();
            setupSystemTray(network, chatWindow, hotkeyListener, trayRef);
        });
    }

    private static void setupSystemTray(PeerNetwork network,
                                        ChatPopupWindow chatWindow,
                                        GlobalHotkeyListener hotkeyListener,
                                        AtomicReference<TrayIcon> trayRef) {
        if (!SystemTray.isSupported()) {
            chatWindow.showPeerSelector();
            return;
        }

        PopupMenu menu = buildTrayMenu(network, chatWindow, hotkeyListener, trayRef);

        TrayIcon trayIcon = new TrayIcon(
                createTrayImage(),
                "Aero – " + network.getMyName(),
                menu
        );
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(e -> SwingUtilities.invokeLater(chatWindow::toggleWindow));
        trayRef.set(trayIcon);

        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException e) {
            chatWindow.showPeerSelector();
        }
    }

    private static PopupMenu buildTrayMenu(PeerNetwork network,
                                           ChatPopupWindow chatWindow,
                                           GlobalHotkeyListener hotkeyListener,
                                           AtomicReference<TrayIcon> trayRef) {
        PopupMenu menu = new PopupMenu();

        MenuItem openItem = new MenuItem(I18n.t(I18n.Key.TRAY_OPEN));
        openItem.addActionListener(e -> SwingUtilities.invokeLater(chatWindow::showWindow));

        MenuItem settingsItem = new MenuItem(I18n.t(I18n.Key.TRAY_SETTINGS));
        settingsItem.addActionListener(e -> SwingUtilities.invokeLater(chatWindow::showSettings));

        MenuItem nameItem = new MenuItem(I18n.t(I18n.Key.TRAY_RENAME));
        nameItem.addActionListener(e -> SwingUtilities.invokeLater(chatWindow::promptRename));

        MenuItem exitItem = new MenuItem(I18n.t(I18n.Key.TRAY_EXIT));
        exitItem.addActionListener(e -> {
            hotkeyListener.stop();
            network.stop();
            TrayIcon icon = trayRef.get();
            if (icon != null) {
                SystemTray.getSystemTray().remove(icon);
            }
            System.exit(0);
        });

        menu.add(openItem);
        menu.add(settingsItem);
        menu.add(nameItem);
        menu.addSeparator();
        menu.add(exitItem);
        return menu;
    }

    private static Image createTrayImage() {
        int size = 16;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(Theme.ACCENT);
        g.fillRoundRect(1, 1, 14, 12, 5, 5);
        g.fillPolygon(new int[]{4, 8, 4}, new int[]{12, 12, 15}, 3);

        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(4, 5, 11, 5);
        g.drawLine(4, 8, 9, 8);

        g.dispose();
        return img;
    }

    /**
     * Returns null when no --name argument was supplied (normal profile).
     */
    private static String parseNameArg(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--name".equals(args[i]) && !args[i + 1].trim().isEmpty()) {
                return args[i + 1].trim();
            }
        }
        return null;
    }
}
