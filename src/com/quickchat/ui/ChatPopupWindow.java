package com.quickchat.ui;

import com.quickchat.config.AppSettings;
import com.quickchat.i18n.I18n;
import com.quickchat.model.ChatGroup;
import com.quickchat.model.ChatMessage;
import com.quickchat.model.Peer;
import com.quickchat.net.PeerNetwork;
import com.quickchat.security.GroupCrypto;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Small, lightweight floating on-screen chat window.
 * Toggled by the global keyboard shortcut; lets the user pick a person or
 * group and chat immediately.
 */
public class ChatPopupWindow extends JDialog {
    private static final int WIDTH = 350;
    private static final int HEIGHT = 470;
    private static final String CARD_PEERS = "PEERS";
    private static final String CARD_CHAT = "CHAT";
    private static final String CARD_SETTINGS = "SETTINGS";

    private final PeerNetwork network;
    private final AppSettings settings;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel contentCards = new JPanel(cardLayout);

    // Header
    private final JButton backButton = new JButton("←");
    private final JLabel headerTitle = new JLabel("Aero");
    private final JLabel headerSubtitle = new JLabel(I18n.t(I18n.Key.APP_SUBTITLE));
    private final JLabel shortcutLabel = new JLabel("Ctrl+Alt+Space");
    private final JButton membersButton = new JButton("👥");
    private final JButton settingsButton = new JButton("⚙");

    // Peer list view
    private final JPanel peerListContainer = new JPanel();
    private final JLabel myNameLabel = new JLabel();
    private final JLabel myIpLabel = new JLabel();
    private JButton newGroupButton;
    private JButton renameButton;
    private JTextField ipField;
    private JButton connectButton;

    // Chat view
    private final JPanel messagesContainer = new JPanel();
    private final JScrollPane messagesScroll;
    private final JTextField inputField = new JTextField();
    private final JLabel messageCounter = new JLabel();

    // Settings view
    private final JTextField displayNameField = new JTextField();
    private final JCheckBox notificationsCheck = new JCheckBox();
    private final JCheckBox alwaysOnTopCheck = new JCheckBox();
    private final JRadioButton germanRadio = new JRadioButton("Deutsch");
    private final JRadioButton englishRadio = new JRadioButton("English");
    private final JLabel settingsStatus = new JLabel(" ");
    private JLabel notificationsSectionLabel;
    private JLabel nameSectionLabel;
    private JLabel languageSectionLabel;
    private JLabel storageLabel;
    private JButton saveNameButton;
    private Timer settingsStatusTimer;

    // State
    private final Map<String, List<ChatMessage>> histories = new HashMap<>();
    private final Map<String, Integer> unreadCounts = new HashMap<>();
    private List<Peer> latestPeers = new ArrayList<>();
    private Peer activePeer = null;
    private ChatGroup activeGroup = null;
    private Point dragOffset;
    private String currentCard = CARD_PEERS;
    private Consumer<String> nameChangeListener = name -> {};
    private Runnable languageChangeListener = () -> {};

    public ChatPopupWindow(PeerNetwork network, AppSettings settings) {
        this.network = network;
        this.settings = settings;

        setUndecorated(true);
        setAlwaysOnTop(settings.isAlwaysOnTop());
        setSize(WIDTH, HEIGHT);
        setShape(new RoundRectangle2D.Double(0, 0, WIDTH, HEIGHT, 16, 16));
        setDefaultCloseOperation(HIDE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG_WINDOW);
        root.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1));

        root.add(buildHeader(), BorderLayout.NORTH);

        contentCards.setOpaque(false);
        contentCards.add(buildPeersView(), CARD_PEERS);

        JPanel chatView = new JPanel(new BorderLayout());
        chatView.setOpaque(false);

        messagesContainer.setLayout(new BoxLayout(messagesContainer, BoxLayout.Y_AXIS));
        messagesContainer.setBackground(Theme.BG_WINDOW);
        messagesContainer.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        messagesScroll = new JScrollPane(messagesContainer);
        messagesScroll.setBorder(null);
        messagesScroll.getViewport().setBackground(Theme.BG_WINDOW);
        messagesScroll.getVerticalScrollBar().setUnitIncrement(14);
        messagesScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        chatView.add(messagesScroll, BorderLayout.CENTER);
        chatView.add(buildInputBar(), BorderLayout.SOUTH);

        contentCards.add(chatView, CARD_CHAT);
        contentCards.add(buildSettingsView(), CARD_SETTINGS);
        root.add(contentCards, BorderLayout.CENTER);

        setContentPane(root);
        centerOnScreen();
        applyLanguageTexts();

        root.registerKeyboardAction(
                e -> setVisible(false),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JPanel.WHEN_IN_FOCUSED_WINDOW
        );
    }

    public void setNameChangeListener(Consumer<String> listener) {
        this.nameChangeListener = listener == null ? name -> {} : listener;
    }

    public void setLanguageChangeListener(Runnable listener) {
        this.languageChangeListener = listener == null ? () -> {} : listener;
    }

    public void setShortcutText(String shortcut) {
        shortcutLabel.setText(shortcut);
    }

    public void toggleWindow() {
        if (isVisible() && (isFocused() || isActive())) {
            setVisible(false);
        } else {
            showWindow();
        }
    }

    public void showWindow() {
        myIpLabel.setText(I18n.t(I18n.Key.YOUR_IP,
                network.getLocalIpAddress() + ":" + network.getMyPort()));

        boolean wasVisible = isVisible();
        if (!wasVisible) {
            // Keep the position the user dragged the window to. Only recenter
            // when the saved position is no longer on any connected screen.
            ensureOnScreen();
            setVisible(true);
        }

        // Temporarily forcing the window on top is the reliable Windows way
        // to actually raise and focus it. It is restored immediately.
        boolean forceTop = !settings.isAlwaysOnTop();
        if (forceTop) {
            setAlwaysOnTop(true);
        }
        toFront();
        requestFocus();

        SwingUtilities.invokeLater(() -> {
            toFront();
            requestFocusInWindow();
            if (activePeer != null || activeGroup != null) {
                inputField.requestFocusInWindow();
            }
            if (forceTop && !settings.isAlwaysOnTop()) {
                setAlwaysOnTop(false);
            }
        });
    }

    public void openChatWith(Peer peer) {
        this.activePeer = peer;
        this.activeGroup = null;
        this.currentCard = CARD_CHAT;
        unreadCounts.remove(peerKey(peer.getId()));
        backButton.setVisible(true);
        membersButton.setVisible(false);
        settingsButton.setVisible(true);
        headerTitle.setText(peer.getName());
        updateChatSubtitle();
        renderMessages();
        renderPeerList();
        cardLayout.show(contentCards, CARD_CHAT);
        showWindow();
        SwingUtilities.invokeLater(inputField::requestFocusInWindow);
    }

    public void openGroupChat(ChatGroup group) {
        if (group == null || settings.isGroupDeleted(group.getId())) return;
        ChatGroup local = settings.getGroup(group.getId());
        if (local == null) {
            local = group;
            settings.addGroup(local);
        }
        if (local.isPasswordProtected() && !local.hasPasswordKey()
                && !promptForGroupPassword(local)) {
            return;
        }
        this.activeGroup = local;
        this.activePeer = null;
        this.currentCard = CARD_CHAT;
        unreadCounts.remove(groupKey(local.getId()));
        backButton.setVisible(true);
        membersButton.setVisible(true);
        settingsButton.setVisible(true);
        headerTitle.setText(local.getName());
        updateChatSubtitle();
        renderMessages();
        renderPeerList();
        cardLayout.show(contentCards, CARD_CHAT);
        showWindow();
        SwingUtilities.invokeLater(inputField::requestFocusInWindow);
    }

    public void showPeerSelector() {
        showPeerSelector(true);
    }

    private void showPeerSelector(boolean makeVisible) {
        this.activePeer = null;
        this.activeGroup = null;
        this.currentCard = CARD_PEERS;
        backButton.setVisible(false);
        membersButton.setVisible(false);
        settingsButton.setVisible(true);
        headerTitle.setText("Aero");
        headerSubtitle.setText(I18n.t(I18n.Key.APP_SUBTITLE));
        renderPeerList();
        cardLayout.show(contentCards, CARD_PEERS);
        if (makeVisible) {
            showWindow();
        }
    }

    public void showSettings() {
        this.activePeer = null;
        this.activeGroup = null;
        this.currentCard = CARD_SETTINGS;
        backButton.setVisible(true);
        membersButton.setVisible(false);
        settingsButton.setVisible(false);
        headerTitle.setText(I18n.t(I18n.Key.SETTINGS_TITLE));
        headerSubtitle.setText(I18n.t(I18n.Key.SETTINGS_SUBTITLE));
        displayNameField.setText(network.getMyName());
        notificationsCheck.setSelected(settings.isNotificationsEnabled());
        alwaysOnTopCheck.setSelected(settings.isAlwaysOnTop());
        settingsStatus.setText(" ");
        cardLayout.show(contentCards, CARD_SETTINGS);
        showWindow();
    }

    public void updatePeers(List<Peer> peers) {
        this.latestPeers = new ArrayList<>(peers);

        for (ChatGroup group : settings.getGroups()) {
            for (Peer peer : latestPeers) {
                if (group.hasMember(peer.getId())) {
                    group.addMember(peer.getId(), peer.getName());
                }
            }
        }

        if (activePeer != null) {
            for (Peer peer : latestPeers) {
                if (peer.getId().equals(activePeer.getId())) {
                    activePeer = peer;
                    headerTitle.setText(peer.getName());
                    break;
                }
            }
        }

        updateChatSubtitle();
        renderPeerList();
    }

    public boolean isChattingWith(Peer peer) {
        return isVisible() && activePeer != null && activeGroup == null
                && activePeer.getId().equals(peer.getId());
    }

    public boolean isChattingWithGroup(ChatGroup group) {
        return isVisible() && activeGroup != null && group != null
                && activeGroup.getId().equals(group.getId());
    }

    public void addIncomingMessage(Peer sender, ChatMessage message) {
        String key = peerKey(sender.getId());
        histories.computeIfAbsent(key, k -> new ArrayList<>()).add(message);

        if (isChattingWith(sender)) {
            appendMessageBubble(message, false);
            scrollToBottom();
        } else {
            unreadCounts.merge(key, 1, Integer::sum);
            renderPeerList();
        }
    }

    public ChatGroup addIncomingGroupMessage(Peer sender, ChatGroup incoming, ChatMessage message) {
        if (settings.isGroupDeleted(incoming.getId())) {
            // The group was deleted here. Tell the sender so it disappears
            // there too, and never let the old group reappear locally.
            notifySenderThatGroupWasDeleted(sender, incoming.getId());
            return null;
        }

        ChatGroup local = settings.getGroup(incoming.getId());
        if (local == null) {
            local = incoming;
            local.addMember(sender.getId(), sender.getName());
            settings.addGroup(local);
        } else {
            local.addMember(sender.getId(), sender.getName());
            if (local.mergeFrom(incoming)) {
                settings.save();
            }
        }

        if (message.isEncrypted() && local.hasPasswordKey()) {
            byte[] groupKeyBytes = local.getPasswordKeyBytes();
            if (groupKeyBytes != null) {
                try {
                    message.setDecryptedText(ChatMessage.truncate(GroupCrypto.decrypt(
                            groupKeyBytes, message.getIv(), message.getCipherText())));
                } catch (GeneralSecurityException | IllegalArgumentException e) {
                    message.setDecryptedText(I18n.t(I18n.Key.ENCRYPTED_MESSAGE));
                }
            }
        }

        String key = groupKey(local.getId());
        histories.computeIfAbsent(key, k -> new ArrayList<>()).add(message);

        if (isChattingWithGroup(local)) {
            appendMessageBubble(message, true);
            scrollToBottom();
        } else {
            unreadCounts.merge(key, 1, Integer::sum);
            renderPeerList();
        }
        return local;
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setBackground(Theme.BG_HEADER);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(7, 10, 7, 10)
        ));

        JPanel left = new JPanel(new BorderLayout(6, 0));
        left.setOpaque(false);

        backButton.setFont(Theme.FONT_TITLE);
        backButton.setForeground(Theme.TEXT_PRIMARY);
        backButton.setContentAreaFilled(false);
        backButton.setBorderPainted(false);
        backButton.setFocusPainted(false);
        backButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        backButton.setMargin(new Insets(0, 2, 0, 4));
        backButton.setVisible(false);
        backButton.addActionListener(e -> showPeerSelector());

        headerTitle.setFont(Theme.FONT_TITLE);
        headerTitle.setForeground(Theme.TEXT_PRIMARY);

        headerSubtitle.setFont(Theme.FONT_SMALL);
        headerSubtitle.setForeground(Theme.TEXT_MUTED);

        JPanel titles = new JPanel(new BorderLayout(0, 1));
        titles.setOpaque(false);
        titles.add(headerTitle, BorderLayout.NORTH);
        titles.add(headerSubtitle, BorderLayout.SOUTH);

        left.add(backButton, BorderLayout.WEST);
        left.add(titles, BorderLayout.CENTER);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        right.setOpaque(false);

        shortcutLabel.setFont(Theme.FONT_SMALL);
        shortcutLabel.setForeground(Theme.TEXT_MUTED);

        membersButton.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 14));
        membersButton.setForeground(Theme.TEXT_MUTED);
        membersButton.setContentAreaFilled(false);
        membersButton.setBorderPainted(false);
        membersButton.setFocusPainted(false);
        membersButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        membersButton.setMargin(new Insets(0, 2, 0, 2));
        membersButton.setToolTipText(I18n.t(I18n.Key.VIEW_MEMBERS));
        membersButton.setVisible(false);
        membersButton.addActionListener(e -> showGroupMembers(activeGroup));

        settingsButton.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 14));
        settingsButton.setForeground(Theme.TEXT_MUTED);
        settingsButton.setContentAreaFilled(false);
        settingsButton.setBorderPainted(false);
        settingsButton.setFocusPainted(false);
        settingsButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        settingsButton.setMargin(new Insets(0, 2, 0, 2));
        settingsButton.setToolTipText(I18n.t(I18n.Key.SETTINGS_TOOLTIP));
        settingsButton.addActionListener(e -> showSettings());

        JButton closeBtn = new JButton("×");
        closeBtn.setFont(Theme.FONT_TITLE);
        closeBtn.setForeground(Theme.TEXT_MUTED);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setBorderPainted(false);
        closeBtn.setFocusPainted(false);
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.setMargin(new Insets(0, 4, 0, 4));
        closeBtn.addActionListener(e -> setVisible(false));

        right.add(shortcutLabel);
        right.add(membersButton);
        right.add(settingsButton);
        right.add(closeBtn);

        header.add(left, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);

        installDrag(header);
        installDrag(left);
        installDrag(titles);
        installDrag(headerTitle);
        installDrag(headerSubtitle);

        return header;
    }

    private void installDrag(Component component) {
        MouseAdapter dragger = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                Point screen = e.getLocationOnScreen();
                dragOffset = new Point(screen.x - getX(), screen.y - getY());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragOffset != null) {
                    Point screen = e.getLocationOnScreen();
                    setLocation(screen.x - dragOffset.x, screen.y - dragOffset.y);
                }
            }
        };
        component.addMouseListener(dragger);
        component.addMouseMotionListener(dragger);
    }

    private void installDialogDrag(Component component, JDialog dialog) {
        MouseAdapter dragger = new MouseAdapter() {
            private Point offset;

            @Override
            public void mousePressed(MouseEvent e) {
                Point screen = e.getLocationOnScreen();
                offset = new Point(screen.x - dialog.getX(), screen.y - dialog.getY());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (offset != null) {
                    Point screen = e.getLocationOnScreen();
                    dialog.setLocation(screen.x - offset.x, screen.y - offset.y);
                }
            }
        };
        component.addMouseListener(dragger);
        component.addMouseMotionListener(dragger);
    }

    private JPanel buildPeersView() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);

        // Top self bar
        JPanel selfBar = new JPanel(new BorderLayout(4, 0));
        selfBar.setOpaque(false);
        selfBar.setBorder(BorderFactory.createEmptyBorder(9, 12, 5, 12));

        JPanel selfInfo = new JPanel(new BorderLayout(0, 2));
        selfInfo.setOpaque(false);

        myNameLabel.setFont(Theme.FONT_SMALL);
        myNameLabel.setForeground(Theme.TEXT_MUTED);
        updateMyNameLabel();

        myIpLabel.setFont(Theme.FONT_SMALL);
        myIpLabel.setForeground(Theme.TEXT_MUTED);
        myIpLabel.setText(I18n.t(I18n.Key.YOUR_IP,
                network.getLocalIpAddress() + ":" + network.getMyPort()));

        selfInfo.add(myNameLabel, BorderLayout.NORTH);
        selfInfo.add(myIpLabel, BorderLayout.SOUTH);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        actions.setOpaque(false);

        newGroupButton = linkButton(I18n.t(I18n.Key.GROUP_BUTTON), this::createGroupDialog);
        renameButton = linkButton(I18n.t(I18n.Key.RENAME_BUTTON), this::promptRename);
        actions.add(newGroupButton);
        actions.add(renameButton);

        selfBar.add(selfInfo, BorderLayout.WEST);
        selfBar.add(actions, BorderLayout.EAST);

        peerListContainer.setLayout(new BoxLayout(peerListContainer, BoxLayout.Y_AXIS));
        peerListContainer.setBackground(Theme.BG_WINDOW);
        peerListContainer.setBorder(BorderFactory.createEmptyBorder(2, 12, 12, 12));

        JScrollPane scroll = new JScrollPane(peerListContainer);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Theme.BG_WINDOW);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        // Manual IP connect bar at bottom of peer selector
        JPanel connectBar = new JPanel(new BorderLayout(6, 0));
        connectBar.setBackground(Theme.BG_HEADER);
        connectBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));

        ipField = new JTextField();
        styleTextField(ipField);
        ipField.setToolTipText(I18n.t(I18n.Key.IP_TOOLTIP));

        connectButton = accentButton(I18n.t(I18n.Key.IP_CONNECT));
        connectButton.setPreferredSize(new Dimension(92, 30));

        Runnable doConnect = () -> {
            String target = ipField.getText().trim();
            if (target.isEmpty()) return;
            connectButton.setEnabled(false);
            connectButton.setText("...");
            new Thread(() -> {
                boolean ok = network.connectByIp(target);
                SwingUtilities.invokeLater(() -> {
                    connectButton.setEnabled(true);
                    connectButton.setText(I18n.t(I18n.Key.IP_CONNECT));
                    if (ok) {
                        ipField.setText("");
                    } else {
                        JOptionPane.showMessageDialog(this,
                                I18n.t(I18n.Key.IP_CONNECT_FAILED, target),
                                I18n.t(I18n.Key.IP_CONNECT_FAILED_TITLE),
                                JOptionPane.WARNING_MESSAGE);
                    }
                });
            }, "aero-ip-connect").start();
        };

        ipField.addActionListener(e -> doConnect.run());
        connectButton.addActionListener(e -> doConnect.run());

        connectBar.add(ipField, BorderLayout.CENTER);
        connectBar.add(connectButton, BorderLayout.EAST);

        panel.add(selfBar, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(connectBar, BorderLayout.SOUTH);
        renderPeerList();
        return panel;
    }

    private JPanel buildSettingsView() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(Theme.BG_WINDOW);
        panel.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));

        notificationsSectionLabel = sectionLabel("");
        panel.add(notificationsSectionLabel);

        styleCheckBox(notificationsCheck);
        notificationsCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        notificationsCheck.setSelected(settings.isNotificationsEnabled());
        notificationsCheck.addActionListener(e ->
                settings.setNotificationsEnabled(notificationsCheck.isSelected()));
        panel.add(notificationsCheck);

        styleCheckBox(alwaysOnTopCheck);
        alwaysOnTopCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        alwaysOnTopCheck.setSelected(settings.isAlwaysOnTop());
        alwaysOnTopCheck.addActionListener(e -> {
            settings.setAlwaysOnTop(alwaysOnTopCheck.isSelected());
            setAlwaysOnTop(settings.isAlwaysOnTop());
        });
        panel.add(alwaysOnTopCheck);

        panel.add(Box.createVerticalStrut(14));

        languageSectionLabel = sectionLabel("");
        panel.add(languageSectionLabel);

        germanRadio.setText(I18n.Language.GERMAN.getDisplayName());
        englishRadio.setText(I18n.Language.ENGLISH.getDisplayName());
        styleRadioButton(germanRadio);
        styleRadioButton(englishRadio);
        ButtonGroup languageGroup = new ButtonGroup();
        languageGroup.add(germanRadio);
        languageGroup.add(englishRadio);
        germanRadio.setSelected(I18n.getLanguage() == I18n.Language.GERMAN);
        englishRadio.setSelected(I18n.getLanguage() == I18n.Language.ENGLISH);
        germanRadio.addActionListener(e -> changeLanguage(I18n.Language.GERMAN));
        englishRadio.addActionListener(e -> changeLanguage(I18n.Language.ENGLISH));

        JPanel languageRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        languageRow.setOpaque(false);
        languageRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        languageRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        languageRow.add(germanRadio);
        languageRow.add(englishRadio);
        panel.add(languageRow);

        panel.add(Box.createVerticalStrut(14));

        nameSectionLabel = sectionLabel("");
        panel.add(nameSectionLabel);

        styleTextField(displayNameField);
        displayNameField.setText(network.getMyName());
        displayNameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        displayNameField.addActionListener(e -> saveDisplayNameFromSettings());

        saveNameButton = accentButton("");
        saveNameButton.setPreferredSize(new Dimension(84, 30));
        saveNameButton.addActionListener(e -> saveDisplayNameFromSettings());

        JPanel nameRow = new JPanel(new BorderLayout(6, 0));
        nameRow.setOpaque(false);
        nameRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        nameRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        nameRow.add(displayNameField, BorderLayout.CENTER);
        nameRow.add(saveNameButton, BorderLayout.EAST);
        panel.add(nameRow);

        panel.add(Box.createVerticalStrut(6));
        settingsStatus.setFont(Theme.FONT_SMALL);
        settingsStatus.setForeground(Theme.ONLINE_DOT);
        settingsStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(settingsStatus);

        panel.add(Box.createVerticalStrut(16));

        storageLabel = new JLabel();
        storageLabel.setFont(Theme.FONT_SMALL);
        storageLabel.setForeground(Theme.TEXT_MUTED);
        storageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(storageLabel);

        return panel;
    }

    private void styleRadioButton(JRadioButton radio) {
        radio.setOpaque(false);
        radio.setForeground(Theme.TEXT_PRIMARY);
        radio.setFont(Theme.FONT_BODY);
        radio.setFocusPainted(false);
        radio.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private JPanel buildInputBar() {
        JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.setBackground(Theme.BG_HEADER);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));

        styleTextField(inputField);
        inputField.setFont(Theme.FONT_BODY);
        inputField.addActionListener(e -> sendCurrentMessage());
        installLengthLimit(inputField, ChatMessage.MAX_TEXT_LENGTH);
        inputField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateMessageCounter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateMessageCounter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateMessageCounter();
            }
        });

        JButton sendBtn = accentButton("➤");
        sendBtn.setFont(Theme.FONT_TITLE);
        sendBtn.setPreferredSize(new Dimension(42, 32));
        sendBtn.addActionListener(e -> sendCurrentMessage());

        messageCounter.setFont(Theme.FONT_SMALL);
        messageCounter.setForeground(Theme.TEXT_MUTED);
        messageCounter.setHorizontalAlignment(SwingConstants.RIGHT);
        messageCounter.setText(ChatMessage.MAX_TEXT_LENGTH + "/" + ChatMessage.MAX_TEXT_LENGTH);
        Dimension counterSize = messageCounter.getPreferredSize();
        messageCounter.setPreferredSize(new Dimension(counterSize.width, counterSize.height));
        messageCounter.setToolTipText(I18n.t(I18n.Key.MESSAGE_LIMIT_TOOLTIP,
                ChatMessage.MAX_TEXT_LENGTH));
        updateMessageCounter();

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);
        right.add(messageCounter);
        right.add(sendBtn);

        bar.add(inputField, BorderLayout.CENTER);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private void updateMessageCounter() {
        int length = inputField.getText().length();
        messageCounter.setText(length + "/" + ChatMessage.MAX_TEXT_LENGTH);
        messageCounter.setForeground(length >= ChatMessage.MAX_TEXT_LENGTH
                ? Theme.DANGER : Theme.TEXT_MUTED);
    }

    private void sendCurrentMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;
        if (text.length() > ChatMessage.MAX_TEXT_LENGTH) {
            JOptionPane.showMessageDialog(this,
                    I18n.t(I18n.Key.MESSAGE_TOO_LONG,
                            text.length(), ChatMessage.MAX_TEXT_LENGTH),
                    I18n.t(I18n.Key.MESSAGE_TOO_LONG_TITLE),
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (activeGroup != null) {
            inputField.setText("");
            int delivered = network.sendGroupMessage(activeGroup, text);
            String display = delivered > 0 ? text : I18n.t(I18n.Key.NOT_DELIVERED, text);
            ChatMessage msg = new ChatMessage(activeGroup.getId(), network.getMyName(), display, false);
            histories.computeIfAbsent(groupKey(activeGroup.getId()), k -> new ArrayList<>()).add(msg);
            appendMessageBubble(msg, false);
            scrollToBottom();
            return;
        }

        if (activePeer == null) return;
        inputField.setText("");
        boolean sent = network.sendMessage(activePeer, text);
        String display = sent ? text : I18n.t(I18n.Key.NOT_DELIVERED, text);
        ChatMessage msg = new ChatMessage(activePeer.getId(), network.getMyName(), display, false);
        histories.computeIfAbsent(peerKey(activePeer.getId()), k -> new ArrayList<>()).add(msg);
        appendMessageBubble(msg, false);
        scrollToBottom();
    }

    private void renderPeerList() {
        peerListContainer.removeAll();

        List<ChatGroup> groups = settings.getGroups();
        groups.sort(Comparator.comparing(ChatGroup::getName, String.CASE_INSENSITIVE_ORDER));

        if (groups.isEmpty() && latestPeers.isEmpty()) {
            JTextArea empty = new JTextArea(I18n.t(I18n.Key.EMPTY_PEERS));
            empty.setEditable(false);
            empty.setWrapStyleWord(true);
            empty.setLineWrap(true);
            empty.setOpaque(false);
            empty.setFont(Theme.FONT_BODY);
            empty.setForeground(Theme.TEXT_MUTED);
            empty.setBorder(BorderFactory.createEmptyBorder(20, 8, 8, 8));
            peerListContainer.add(empty);
        } else {
            if (!groups.isEmpty()) {
                peerListContainer.add(sectionLabel(I18n.t(I18n.Key.GROUPS_SECTION)));
                for (ChatGroup group : groups) {
                    peerListContainer.add(createGroupCard(group));
                    peerListContainer.add(Box.createVerticalStrut(6));
                }
            }
            if (!latestPeers.isEmpty()) {
                if (!groups.isEmpty()) {
                    peerListContainer.add(Box.createVerticalStrut(4));
                }
                peerListContainer.add(sectionLabel(I18n.t(I18n.Key.ONLINE_SECTION)));
                for (Peer peer : latestPeers) {
                    peerListContainer.add(createPeerCard(peer));
                    peerListContainer.add(Box.createVerticalStrut(6));
                }
            }
        }

        peerListContainer.revalidate();
        peerListContainer.repaint();
    }

    private JPanel createPeerCard(Peer peer) {
        JPanel card = new JPanel(new BorderLayout(8, 0));
        card.setBackground(Theme.BG_CARD);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)
        ));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel dot = new JLabel("●");
        dot.setForeground(Theme.ONLINE_DOT);
        dot.setFont(Theme.FONT_SMALL);

        JLabel name = new JLabel(peer.getName());
        name.setForeground(Theme.TEXT_PRIMARY);
        name.setFont(Theme.FONT_BOLD);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        left.add(dot);
        left.add(name);

        card.add(left, BorderLayout.WEST);

        int unread = unreadCounts.getOrDefault(peerKey(peer.getId()), 0);
        if (unread > 0) {
            card.add(createUnreadBadge(unread), BorderLayout.EAST);
        }

        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    openChatWith(peer);
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                card.setBackground(Theme.BG_CARD_HOVER);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                card.setBackground(Theme.BG_CARD);
            }
        });

        return card;
    }

    private JPanel createGroupCard(ChatGroup group) {
        JPanel card = new JPanel(new BorderLayout(8, 0));
        card.setBackground(Theme.BG_CARD);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel icon = new JLabel("▣");
        icon.setForeground(Theme.ACCENT_HOVER);
        icon.setFont(Theme.FONT_TITLE);

        JLabel name = new JLabel(group.getName());
        name.setForeground(Theme.TEXT_PRIMARY);
        name.setFont(Theme.FONT_BOLD);

        JLabel info = new JLabel(memberStatus(group));
        info.setForeground(Theme.TEXT_MUTED);
        info.setFont(Theme.FONT_SMALL);

        JPanel texts = new JPanel(new BorderLayout(0, 1));
        texts.setOpaque(false);
        texts.add(name, BorderLayout.NORTH);
        texts.add(info, BorderLayout.SOUTH);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        left.add(icon);
        left.add(texts);

        card.add(left, BorderLayout.WEST);

        int unread = unreadCounts.getOrDefault(groupKey(group.getId()), 0);
        if (unread > 0) {
            card.add(createUnreadBadge(unread), BorderLayout.EAST);
        }

        JPopupMenu popup = new JPopupMenu();
        JMenuItem membersItem = new JMenuItem(I18n.t(I18n.Key.VIEW_MEMBERS));
        membersItem.addActionListener(e -> showGroupMembers(group));
        popup.add(membersItem);
        popup.addSeparator();
        JMenuItem deleteItem = new JMenuItem(I18n.t(I18n.Key.DELETE_GROUP_MENU));
        deleteItem.addActionListener(e -> deleteGroup(group));
        popup.add(deleteItem);
        card.setComponentPopupMenu(popup);
        name.setComponentPopupMenu(popup);
        info.setComponentPopupMenu(popup);
        icon.setComponentPopupMenu(popup);

        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    openGroupChat(group);
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                card.setBackground(Theme.BG_CARD_HOVER);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                card.setBackground(Theme.BG_CARD);
            }
        });

        return card;
    }

    private JLabel createUnreadBadge(int unread) {
        JLabel badge = new JLabel(I18n.t(I18n.Key.UNREAD, unread));
        badge.setOpaque(true);
        badge.setBackground(Theme.ACCENT);
        badge.setForeground(Color.WHITE);
        badge.setFont(Theme.FONT_SMALL);
        return badge;
    }

    private void renderMessages() {
        messagesContainer.removeAll();

        String key = null;
        if (activeGroup != null) {
            key = groupKey(activeGroup.getId());
        } else if (activePeer != null) {
            key = peerKey(activePeer.getId());
        }

        List<ChatMessage> messages = key == null
                ? List.of()
                : histories.getOrDefault(key, List.of());

        if (messages.isEmpty()) {
            JTextArea empty = new JTextArea(
                    I18n.t(activeGroup != null
                            ? I18n.Key.NO_MESSAGES_GROUP
                            : I18n.Key.NO_MESSAGES)
            );
            empty.setEditable(false);
            empty.setWrapStyleWord(true);
            empty.setLineWrap(true);
            empty.setOpaque(false);
            empty.setFont(Theme.FONT_BODY);
            empty.setForeground(Theme.TEXT_MUTED);
            empty.setBorder(BorderFactory.createEmptyBorder(22, 8, 8, 8));
            messagesContainer.add(empty);
        } else {
            boolean showSender = activeGroup != null;
            for (ChatMessage message : messages) {
                appendMessageBubble(message, showSender);
            }
        }

        messagesContainer.revalidate();
        messagesContainer.repaint();
        scrollToBottom();
    }

    private void appendMessageBubble(ChatMessage msg, boolean showSender) {
        JPanel row = new JPanel(new FlowLayout(msg.isIncoming() ? FlowLayout.LEFT : FlowLayout.RIGHT, 0, 2));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 500));

        JPanel bubble = new JPanel(new BorderLayout(0, 2));
        bubble.setBackground(msg.isIncoming() ? Theme.BUBBLE_IN : Theme.BUBBLE_OUT);
        bubble.setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 10));

        if (showSender && msg.isIncoming()) {
            JLabel sender = new JLabel(msg.getSenderName());
            sender.setFont(Theme.FONT_BOLD);
            sender.setForeground(Theme.ACCENT_HOVER);
            bubble.add(sender, BorderLayout.NORTH);
        }

        JTextArea text = new JTextArea(msg.getText());
        text.setEditable(false);
        text.setWrapStyleWord(true);
        text.setLineWrap(true);
        text.setOpaque(false);
        text.setForeground(Theme.TEXT_PRIMARY);
        text.setFont(Theme.FONT_BODY);
        text.setColumns(20);

        JLabel time = new JLabel(msg.getFormattedTime());
        time.setFont(Theme.FONT_SMALL);
        time.setForeground(new Color(220, 224, 235, 170));
        time.setHorizontalAlignment(JLabel.RIGHT);

        bubble.add(text, BorderLayout.CENTER);
        bubble.add(time, BorderLayout.SOUTH);
        row.add(bubble);

        messagesContainer.add(row);
        messagesContainer.add(Box.createVerticalStrut(4));
        messagesContainer.revalidate();
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar bar = messagesScroll.getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
        });
    }

    public void promptRename() {
        String updated = (String) JOptionPane.showInputDialog(
                isVisible() ? this : null,
                I18n.t(I18n.Key.RENAME_PROMPT),
                I18n.t(I18n.Key.RENAME_TITLE),
                JOptionPane.PLAIN_MESSAGE,
                null,
                null,
                network.getMyName()
        );
        if (updated != null && !updated.trim().isEmpty()) {
            applyDisplayName(updated.trim());
        }
    }

    private void saveDisplayNameFromSettings() {
        String updated = displayNameField.getText().trim();
        if (updated.isEmpty()) {
            displayNameField.setText(network.getMyName());
            return;
        }
        applyDisplayName(updated);
        showSettingsStatus(I18n.t(I18n.Key.NAME_SAVED));
    }

    private void showSettingsStatus(String text) {
        settingsStatus.setText(text);
        if (settingsStatusTimer != null) {
            settingsStatusTimer.stop();
        }
        settingsStatusTimer = new Timer(2200, e -> settingsStatus.setText(" "));
        settingsStatusTimer.setRepeats(false);
        settingsStatusTimer.start();
    }

    private void applyDisplayName(String name) {
        network.setMyName(name);
        settings.setDisplayName(name);
        displayNameField.setText(name);
        updateMyNameLabel();
        nameChangeListener.accept(name);
    }

    private void updateMyNameLabel() {
        myNameLabel.setText(I18n.t(I18n.Key.YOU, network.getMyName()));
    }

    private void updateChatSubtitle() {
        if (activePeer != null) {
            boolean online = latestPeers.stream()
                    .anyMatch(peer -> peer.getId().equals(activePeer.getId()));
            headerSubtitle.setText(I18n.t(online ? I18n.Key.ONLINE : I18n.Key.OFFLINE));
        } else if (activeGroup != null) {
            headerSubtitle.setText(memberStatus(activeGroup));
        }
    }

    private String memberStatus(ChatGroup group) {
        int members = group.getMemberCount();
        int online = group.hasMember(network.getMyId()) ? 1 : 0;
        for (Peer peer : latestPeers) {
            if (group.hasMember(peer.getId())) {
                online++;
            }
        }
        online = Math.min(online, members);

        String status = members == 1
                ? I18n.t(I18n.Key.MEMBER_STATUS_ONE, online)
                : I18n.t(I18n.Key.MEMBER_STATUS_MANY, members, online);
        if (group.isPasswordProtected()) {
            status += " · \uD83D\uDD12 " + I18n.t(I18n.Key.PASSWORD_PROTECTED);
        }
        return status;
    }

    /**
     * Switches the UI language and refreshes every visible text.
     */
    private void changeLanguage(I18n.Language language) {
        if (I18n.getLanguage() == language) return;
        I18n.setLanguage(language);
        settings.setLanguage(language);
        applyLanguageTexts();
        languageChangeListener.run();
    }

    private void applyLanguageTexts() {
        settingsButton.setToolTipText(I18n.t(I18n.Key.SETTINGS_TOOLTIP));
        membersButton.setToolTipText(I18n.t(I18n.Key.VIEW_MEMBERS));
        newGroupButton.setText(I18n.t(I18n.Key.GROUP_BUTTON));
        renameButton.setText(I18n.t(I18n.Key.RENAME_BUTTON));
        connectButton.setText(I18n.t(I18n.Key.IP_CONNECT));
        ipField.setToolTipText(I18n.t(I18n.Key.IP_TOOLTIP));
        messageCounter.setToolTipText(I18n.t(I18n.Key.MESSAGE_LIMIT_TOOLTIP,
                ChatMessage.MAX_TEXT_LENGTH));

        notificationsSectionLabel.setText(I18n.t(I18n.Key.NOTIFICATIONS_SECTION));
        notificationsCheck.setText(I18n.t(I18n.Key.NOTIFICATIONS_LABEL));
        alwaysOnTopCheck.setText(I18n.t(I18n.Key.ALWAYS_ON_TOP_LABEL));
        nameSectionLabel.setText(I18n.t(I18n.Key.DISPLAY_NAME_SECTION));
        saveNameButton.setText(I18n.t(I18n.Key.SAVE));
        languageSectionLabel.setText(I18n.t(I18n.Key.LANGUAGE_SECTION));
        storageLabel.setText("<html>" + I18n.t(I18n.Key.SETTINGS_AUTOSAVE) + "</html>");

        germanRadio.setSelected(I18n.getLanguage() == I18n.Language.GERMAN);
        englishRadio.setSelected(I18n.getLanguage() == I18n.Language.ENGLISH);

        myIpLabel.setText(I18n.t(I18n.Key.YOUR_IP,
                network.getLocalIpAddress() + ":" + network.getMyPort()));
        updateMyNameLabel();

        // Header texts depend on the card that is currently shown.
        if (CARD_SETTINGS.equals(currentCard)) {
            headerTitle.setText(I18n.t(I18n.Key.SETTINGS_TITLE));
            headerSubtitle.setText(I18n.t(I18n.Key.SETTINGS_SUBTITLE));
        } else if (CARD_PEERS.equals(currentCard)) {
            headerSubtitle.setText(I18n.t(I18n.Key.APP_SUBTITLE));
        } else {
            updateChatSubtitle();
            renderMessages();
        }
        renderPeerList();
    }

    private JDialog createStyledDialog(String titleText, JPanel body, JPanel buttons,
                                       JButton defaultButton, Component focusTarget) {
        JDialog dialog = new JDialog(this, titleText, true);
        dialog.setUndecorated(true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG_WINDOW);
        root.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1));

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.BG_HEADER);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(9, 14, 9, 8)
        ));

        JLabel title = new JLabel(titleText);
        title.setFont(Theme.FONT_TITLE);
        title.setForeground(Theme.TEXT_PRIMARY);

        JButton closeButton = new JButton("×");
        closeButton.setFont(Theme.FONT_TITLE);
        closeButton.setForeground(Theme.TEXT_MUTED);
        closeButton.setContentAreaFilled(false);
        closeButton.setBorderPainted(false);
        closeButton.setFocusPainted(false);
        closeButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeButton.setMargin(new Insets(0, 4, 0, 4));
        closeButton.addActionListener(e -> dialog.dispose());

        header.add(title, BorderLayout.WEST);
        header.add(closeButton, BorderLayout.EAST);

        root.add(header, BorderLayout.NORTH);
        root.add(body, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(root);
        if (defaultButton != null) {
            dialog.getRootPane().setDefaultButton(defaultButton);
        }

        root.registerKeyboardAction(
                e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JPanel.WHEN_IN_FOCUSED_WINDOW
        );

        installDialogDrag(header, dialog);
        installDialogDrag(title, dialog);

        dialog.pack();
        dialog.setSize(Math.max(340, dialog.getWidth()), dialog.getHeight());
        dialog.setShape(new RoundRectangle2D.Double(
                0, 0, dialog.getWidth(), dialog.getHeight(), 14, 14));
        dialog.setLocationRelativeTo(this);
        if (focusTarget != null) {
            SwingUtilities.invokeLater(focusTarget::requestFocusInWindow);
        }
        return dialog;
    }

    private JPanel dialogButtons(JButton cancel, JButton confirm) {
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setBackground(Theme.BG_WINDOW);
        buttons.setBorder(BorderFactory.createEmptyBorder(0, 16, 12, 16));
        buttons.add(cancel);
        buttons.add(confirm);
        return buttons;
    }

    private void createGroupDialog() {
        List<Peer> available = new ArrayList<>(latestPeers);
        if (available.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    I18n.t(I18n.Key.NO_PEERS_MESSAGE),
                    I18n.t(I18n.Key.NO_PEERS_TITLE), JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(Theme.BG_WINDOW);
        body.setBorder(BorderFactory.createEmptyBorder(10, 16, 2, 16));

        body.add(sectionLabel(I18n.t(I18n.Key.GROUP_NAME_SECTION)));

        JTextField nameField = new JTextField();
        styleTextField(nameField);
        nameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        nameField.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(nameField);
        body.add(Box.createVerticalStrut(10));

        body.add(sectionLabel(I18n.t(I18n.Key.MEMBERS_SECTION)));

        JPanel memberList = new JPanel();
        memberList.setLayout(new BoxLayout(memberList, BoxLayout.Y_AXIS));
        memberList.setBackground(Theme.BG_WINDOW);
        memberList.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        Map<JCheckBox, Peer> memberBoxes = new LinkedHashMap<>();
        for (Peer peer : available) {
            JCheckBox box = new JCheckBox(peer.getName());
            styleCheckBox(box);
            box.setSelected(true);
            box.setAlignmentX(Component.LEFT_ALIGNMENT);
            memberBoxes.put(box, peer);
            memberList.add(box);
        }

        JScrollPane memberScroll = new JScrollPane(memberList);
        memberScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1));
        memberScroll.getViewport().setBackground(Theme.BG_WINDOW);
        memberScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        memberScroll.getVerticalScrollBar().setUnitIncrement(12);
        int listHeight = Math.min(130, Math.max(50, available.size() * 28 + 10));
        memberScroll.setPreferredSize(new Dimension(300, listHeight));
        memberScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, listHeight));
        memberScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(memberScroll);
        body.add(Box.createVerticalStrut(10));

        body.add(sectionLabel(I18n.t(I18n.Key.PASSWORD_SECTION)));

        JPasswordField passwordField = new JPasswordField();
        styleTextField(passwordField);
        passwordField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        passwordField.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(passwordField);

        JLabel hint = new JLabel(I18n.t(I18n.Key.PASSWORD_HINT));
        hint.setFont(Theme.FONT_SMALL);
        hint.setForeground(Theme.TEXT_MUTED);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        hint.setBorder(BorderFactory.createEmptyBorder(3, 2, 0, 2));
        body.add(hint);
        body.add(Box.createVerticalStrut(4));

        JLabel errorLabel = new JLabel(" ");
        errorLabel.setFont(Theme.FONT_SMALL);
        errorLabel.setForeground(Theme.DANGER);
        errorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(errorLabel);

        JButton cancelButton = secondaryButton(I18n.t(I18n.Key.CANCEL));
        JButton createButton = accentButton(I18n.t(I18n.Key.CREATE));
        createButton.setPreferredSize(new Dimension(96, 32));

        JDialog dialog = createStyledDialog(I18n.t(I18n.Key.NEW_GROUP_TITLE), body,
                dialogButtons(cancelButton, createButton), createButton, nameField);
        cancelButton.addActionListener(e -> dialog.dispose());

        Runnable attemptCreate = () -> {
            String groupName = nameField.getText().trim();
            if (groupName.isEmpty()) {
                errorLabel.setText(I18n.t(I18n.Key.ERROR_GROUP_NAME_REQUIRED));
                nameField.requestFocusInWindow();
                return;
            }

            List<Peer> selected = new ArrayList<>();
            for (Map.Entry<JCheckBox, Peer> entry : memberBoxes.entrySet()) {
                if (entry.getKey().isSelected()) {
                    selected.add(entry.getValue());
                }
            }
            if (selected.isEmpty()) {
                errorLabel.setText(I18n.t(I18n.Key.ERROR_SELECT_MEMBER));
                return;
            }

            ChatGroup group = new ChatGroup(
                    UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                    groupName
            );
            group.addMember(network.getMyId(), network.getMyName());
            for (Peer peer : selected) {
                group.addMember(peer.getId(), peer.getName());
            }

            char[] passwordChars = passwordField.getPassword();
            if (passwordChars.length > 0) {
                try {
                    GroupCrypto.KeyMaterial material = GroupCrypto.create(new String(passwordChars));
                    group.setPasswordData(material.getSalt(), material.getCheck(), material.getKeyBase64());
                } catch (GeneralSecurityException ex) {
                    errorLabel.setText(I18n.t(I18n.Key.ERROR_PASSWORD_SET));
                    return;
                } finally {
                    java.util.Arrays.fill(passwordChars, '\0');
                }
            }

            settings.addGroup(group);
            dialog.dispose();
            renderPeerList();
            openGroupChat(group);
        };

        nameField.addActionListener(e -> attemptCreate.run());
        passwordField.addActionListener(e -> attemptCreate.run());
        createButton.addActionListener(e -> attemptCreate.run());

        dialog.setVisible(true);
    }

    /**
     * Asks for the password of a protected group. Returns true when the
     * correct password was entered (the derived key is then stored locally).
     */
    private boolean promptForGroupPassword(ChatGroup group) {
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(Theme.BG_WINDOW);
        body.setBorder(BorderFactory.createEmptyBorder(12, 16, 2, 16));

        JLabel subtitle = new JLabel(I18n.t(I18n.Key.PASSWORD_REQUIRED_SUBTITLE, group.getName()));
        subtitle.setFont(Theme.FONT_BODY);
        subtitle.setForeground(Theme.TEXT_PRIMARY);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(subtitle);
        body.add(Box.createVerticalStrut(8));

        body.add(sectionLabel(I18n.t(I18n.Key.PASSWORD_FIELD).toUpperCase()));

        JPasswordField passwordField = new JPasswordField();
        styleTextField(passwordField);
        passwordField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        passwordField.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(passwordField);
        body.add(Box.createVerticalStrut(4));

        JLabel errorLabel = new JLabel(" ");
        errorLabel.setFont(Theme.FONT_SMALL);
        errorLabel.setForeground(Theme.DANGER);
        errorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(errorLabel);

        JButton cancelButton = secondaryButton(I18n.t(I18n.Key.CANCEL));
        JButton unlockButton = accentButton(I18n.t(I18n.Key.UNLOCK));
        unlockButton.setPreferredSize(new Dimension(96, 32));

        JDialog dialog = createStyledDialog(I18n.t(I18n.Key.PASSWORD_REQUIRED_TITLE), body,
                dialogButtons(cancelButton, unlockButton), unlockButton, passwordField);
        cancelButton.addActionListener(e -> dialog.dispose());

        boolean[] unlocked = {false};
        Runnable attemptUnlock = () -> {
            char[] chars = passwordField.getPassword();
            try {
                byte[] key = GroupCrypto.deriveKey(new String(chars), group.getPasswordSalt());
                if (GroupCrypto.verify(key, group.getPasswordCheck())) {
                    group.setPasswordKey(GroupCrypto.encodeKey(key));
                    settings.save();
                    decryptStoredMessages(group, key);
                    unlocked[0] = true;
                    dialog.dispose();
                } else {
                    errorLabel.setText(I18n.t(I18n.Key.WRONG_PASSWORD));
                    passwordField.selectAll();
                }
            } catch (GeneralSecurityException | IllegalArgumentException ex) {
                errorLabel.setText(I18n.t(I18n.Key.UNLOCK_FAILED));
            } finally {
                java.util.Arrays.fill(chars, '\0');
            }
        };
        passwordField.addActionListener(e -> attemptUnlock.run());
        unlockButton.addActionListener(e -> attemptUnlock.run());

        dialog.setVisible(true);
        return unlocked[0];
    }

    private void decryptStoredMessages(ChatGroup group, byte[] key) {
        List<ChatMessage> messages = histories.get(groupKey(group.getId()));
        if (messages == null) return;
        for (ChatMessage message : messages) {
            if (message.isEncrypted() && message.isIncoming()) {
                try {
                    message.setDecryptedText(ChatMessage.truncate(GroupCrypto.decrypt(
                            key, message.getIv(), message.getCipherText())));
                } catch (GeneralSecurityException | IllegalArgumentException ignored) {
                }
            }
        }
    }

    private void deleteGroup(ChatGroup group) {
        int result = JOptionPane.showConfirmDialog(
                isVisible() ? this : null,
                I18n.t(I18n.Key.DELETE_GROUP_MESSAGE, group.getName()),
                I18n.t(I18n.Key.DELETE_GROUP_TITLE),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (result != JOptionPane.YES_OPTION) {
            return;
        }

        settings.deleteGroup(group.getId());
        histories.remove(groupKey(group.getId()));
        unreadCounts.remove(groupKey(group.getId()));
        if (activeGroup != null && activeGroup.getId().equals(group.getId())) {
            showPeerSelector();
        } else {
            renderPeerList();
        }
        notifyGroupMembersAboutDeletion(group);
    }

    /**
     * Removes a group after another member deleted it. Returns the deleted
     * group so the caller can show a notification, or null when nothing
     * was deleted.
     */
    public ChatGroup handleRemoteGroupDeletion(Peer sender, String groupId) {
        ChatGroup group = settings.getGroup(groupId);
        if (group == null || !group.hasMember(sender.getId())) {
            return null;
        }

        settings.deleteGroup(groupId);
        histories.remove(groupKey(groupId));
        unreadCounts.remove(groupKey(groupId));
        if (activeGroup != null && activeGroup.getId().equals(groupId)) {
            // Do not pop the window open when the group was deleted while
            // Aero was hidden in the background.
            showPeerSelector(isVisible());
        } else {
            renderPeerList();
        }
        return group;
    }

    private void notifyGroupMembersAboutDeletion(ChatGroup group) {
        Thread thread = new Thread(() -> network.sendGroupDeletion(group),
                "aero-group-delete");
        thread.setDaemon(true);
        thread.start();
    }

    private void notifySenderThatGroupWasDeleted(Peer sender, String groupId) {
        Thread thread = new Thread(() -> network.sendGroupDeletionTo(sender, groupId),
                "aero-group-delete-reply");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Opens a small window that lists every member of the group with their
     * current online state.
     */
    private void showGroupMembers(ChatGroup group) {
        if (group == null || settings.isGroupDeleted(group.getId())) {
            return;
        }
        ChatGroup local = settings.getGroup(group.getId());
        if (local != null) {
            group = local;
        }

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(Theme.BG_WINDOW);
        body.setBorder(BorderFactory.createEmptyBorder(10, 16, 4, 16));

        JLabel summary = new JLabel(memberStatus(group));
        summary.setFont(Theme.FONT_SMALL);
        summary.setForeground(Theme.TEXT_MUTED);
        summary.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(summary);
        body.add(Box.createVerticalStrut(10));

        JPanel memberList = new JPanel();
        memberList.setLayout(new BoxLayout(memberList, BoxLayout.Y_AXIS));
        memberList.setBackground(Theme.BG_WINDOW);

        for (Map.Entry<String, String> entry : group.getMembers().entrySet()) {
            memberList.add(createMemberRow(entry.getKey(), entry.getValue()));
            memberList.add(Box.createVerticalStrut(5));
        }

        JScrollPane scroll = new JScrollPane(memberList);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1));
        scroll.getViewport().setBackground(Theme.BG_WINDOW);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(12);
        int listHeight = Math.min(240, Math.max(56, group.getMemberCount() * 39 + 8));
        scroll.setPreferredSize(new Dimension(300, listHeight));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, listHeight));
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(scroll);

        JButton closeButton = accentButton(I18n.t(I18n.Key.CLOSE));
        closeButton.setPreferredSize(new Dimension(96, 32));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setBackground(Theme.BG_WINDOW);
        buttons.setBorder(BorderFactory.createEmptyBorder(10, 16, 12, 16));
        buttons.add(closeButton);

        JDialog dialog = createStyledDialog(
                I18n.t(I18n.Key.GROUP_MEMBERS_TITLE, group.getName()),
                body, buttons, closeButton, closeButton);
        closeButton.addActionListener(e -> dialog.dispose());
        dialog.setVisible(true);
    }

    private JPanel createMemberRow(String memberId, String memberName) {
        boolean self = memberId.equals(network.getMyId());
        boolean online = self || latestPeers.stream()
                .anyMatch(peer -> peer.getId().equals(memberId));

        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBackground(Theme.BG_CARD);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1),
                BorderFactory.createEmptyBorder(7, 10, 7, 10)
        ));

        JLabel dot = new JLabel("●");
        dot.setFont(Theme.FONT_SMALL);
        dot.setForeground(online ? Theme.ONLINE_DOT : Theme.TEXT_MUTED);
        dot.setToolTipText(I18n.t(online ? I18n.Key.ONLINE : I18n.Key.OFFLINE));

        String displayName = memberName == null || memberName.isBlank()
                ? memberId
                : memberName;
        if (self) {
            displayName += " " + I18n.t(I18n.Key.MEMBER_YOU);
        }
        JLabel name = new JLabel(displayName);
        name.setFont(Theme.FONT_BOLD);
        name.setForeground(Theme.TEXT_PRIMARY);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        left.add(dot);
        left.add(name);
        row.add(left, BorderLayout.WEST);

        JLabel status = new JLabel(I18n.t(online ? I18n.Key.ONLINE : I18n.Key.OFFLINE));
        status.setFont(Theme.FONT_SMALL);
        status.setForeground(online ? Theme.ONLINE_DOT : Theme.TEXT_MUTED);
        row.add(status, BorderLayout.EAST);
        return row;
    }

    private JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.FONT_SMALL.deriveFont(Font.BOLD));
        label.setForeground(Theme.TEXT_MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));
        return label;
    }

    private void styleCheckBox(JCheckBox box) {
        box.setOpaque(false);
        box.setForeground(Theme.TEXT_PRIMARY);
        box.setFont(Theme.FONT_BODY);
        box.setFocusPainted(false);
        box.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
    }

    private void styleTextField(JTextField field) {
        field.setBackground(Theme.BG_INPUT);
        field.setForeground(Theme.TEXT_PRIMARY);
        field.setCaretColor(Theme.TEXT_PRIMARY);
        field.setFont(Theme.FONT_SMALL);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)
        ));
    }

    /**
     * Caps the length of a text field so pasting a huge text can never create
     * an oversized chat message. Excess characters are simply ignored.
     */
    private static void installLengthLimit(JTextField field, int maxLength) {
        AbstractDocument document = (AbstractDocument) field.getDocument();
        document.setDocumentFilter(new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String string,
                                     AttributeSet attributes) throws BadLocationException {
                replace(fb, offset, 0, string, attributes);
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text,
                                AttributeSet attributes) throws BadLocationException {
                if (text == null) {
                    super.replace(fb, offset, length, null, attributes);
                    return;
                }
                int available = maxLength - (fb.getDocument().getLength() - length);
                if (available <= 0) {
                    return;
                }
                super.replace(fb, offset, length, limitText(text, available), attributes);
            }
        });
    }

    private static String limitText(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        int end = maxLength;
        if (Character.isHighSurrogate(text.charAt(end - 1))
                && Character.isLowSurrogate(text.charAt(end))) {
            end--;
        }
        return text.substring(0, end);
    }

    private JButton linkButton(String text, Runnable action) {
        JButton button = new JButton(text);
        button.setFont(Theme.FONT_SMALL);
        button.setForeground(Theme.ACCENT_HOVER);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setMargin(new Insets(0, 2, 0, 2));
        button.addActionListener(e -> action.run());
        return button;
    }

    private JButton accentButton(String text) {
        JButton button = new JButton(text);
        button.setFont(Theme.FONT_SMALL);
        button.setBackground(Theme.ACCENT);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setMargin(new Insets(4, 8, 4, 8));
        return button;
    }

    private JButton secondaryButton(String text) {
        JButton button = new JButton(text);
        button.setFont(Theme.FONT_SMALL);
        button.setForeground(Theme.TEXT_PRIMARY);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setOpaque(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setMargin(new Insets(4, 10, 4, 10));
        return button;
    }

    private static String peerKey(String peerId) {
        return "peer:" + peerId;
    }

    private static String groupKey(String groupId) {
        return "group:" + groupId;
    }

    private void centerOnScreen() {
        Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();
        int x = bounds.x + (bounds.width - WIDTH) / 2;
        int y = bounds.y + (bounds.height - HEIGHT) / 2;
        setLocation(x, y);
    }

    /**
     * Recenters the window only when its current position is completely
     * outside all connected screens (for example after a monitor was removed).
     */
    private void ensureOnScreen() {
        Rectangle windowBounds = getBounds();
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getScreenDevices()) {
            if (device.getDefaultConfiguration().getBounds().intersects(windowBounds)) {
                return;
            }
        }
        centerOnScreen();
    }
}
