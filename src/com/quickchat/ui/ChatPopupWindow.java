package com.quickchat.ui;

import com.quickchat.config.AppSettings;
import com.quickchat.config.ChatHistoryStore;
import com.quickchat.i18n.I18n;
import com.quickchat.model.ChatGroup;
import com.quickchat.model.ChatMessage;
import com.quickchat.model.FileAttachment;
import com.quickchat.model.Peer;
import com.quickchat.net.PeerNetwork;
import com.quickchat.security.GroupCrypto;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
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
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.TransferHandler;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.DocumentFilter;
import javax.swing.text.JTextComponent;
import javax.swing.text.View;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TooManyListenersException;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * Small, lightweight floating on-screen chat window.
 * Toggled by the global keyboard shortcut; lets the user pick a person or
 * group and chat immediately.
 */
public class ChatPopupWindow extends JDialog {
    private static final int DEFAULT_WIDTH = 350;
    private static final int DEFAULT_HEIGHT = 470;
    private static final int MIN_WIDTH = 350;
    private static final int MIN_HEIGHT = 380;
    private static final int MAX_INLINE_IMG_W = 216;
    private static final int MAX_INLINE_IMG_H = 260;
    private static final DateTimeFormatter SCREENSHOT_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
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
    private final JPanel peerListContainer = new ScrollableListPanel();
    private final JLabel myNameLabel = new JLabel();
    private final JLabel myIpLabel = new JLabel();
    private JButton newGroupButton;
    private JButton renameButton;
    private JTextField ipField;
    private JButton connectButton;

    // Chat view
    private final JPanel messagesContainer = new ScrollableListPanel();
    private final JScrollPane messagesScroll;
    private final JTextField inputField = new JTextField();
    private final JLabel messageCounter = new JLabel();
    private final JButton attachButton = UiIcons.darkButton(
            null,
            UiIcons.paperclip(Theme.TEXT_PRIMARY, 16),
            Theme.BTN_DARK,
            Theme.BTN_DARK_HOVER,
            Theme.BTN_DARK_BORDER,
            Theme.TEXT_PRIMARY,
            8
    );
    private final JPanel attachmentStrip = new JPanel();
    private final JLabel dropBanner = new JLabel("", SwingConstants.CENTER);
    private final List<PendingAttachment> pendingAttachments = new ArrayList<>();
    private final Map<String, ImageIcon> inlineImageCache = new HashMap<>();
    private final Map<FileAttachment, Runnable> attachmentRefreshCallbacks = new WeakHashMap<>();
    private JPanel chatViewPanel;
    private JPanel chatInputBar;
    private TransferHandler fileDropHandler;
    private Timer dropHighlightClearTimer;
    private int lastMessagesViewportWidth = -1;

    // Settings view
    private final JTextField displayNameField = new JTextField();
    private final JCheckBox keepMessagesCheck = new JCheckBox();
    private final JCheckBox notificationsCheck = new JCheckBox();
    private final JCheckBox alwaysOnTopCheck = new JCheckBox();
    private final JRadioButton germanRadio = new JRadioButton("Deutsch");
    private final JRadioButton englishRadio = new JRadioButton("English");
    private final JLabel settingsStatus = new JLabel(" ");
    private JLabel messagesSectionLabel;
    private JLabel keepMessagesHintLabel;
    private JLabel notificationsSectionLabel;
    private JLabel nameSectionLabel;
    private JLabel languageSectionLabel;
    private JLabel storageLabel;
    private JButton saveNameButton;
    private Timer settingsStatusTimer;

    // State
    private final Map<String, List<ChatMessage>> histories = new HashMap<>();
    private final ChatHistoryStore historyStore;
    private final Timer historySaveTimer;
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
        this.historyStore = new ChatHistoryStore(settings.getHistoryFile());
        this.historySaveTimer = new Timer(1200, e -> saveHistoryNow());
        this.historySaveTimer.setRepeats(false);

        setUndecorated(true);
        setAlwaysOnTop(settings.isAlwaysOnTop());
        Dimension screenMax = WindowResizer.usableScreenSize(null, MIN_WIDTH, MIN_HEIGHT);
        int savedW = settings.getWindowWidth();
        int savedH = settings.getWindowHeight();
        int initW = Math.max(MIN_WIDTH, Math.min(screenMax.width, savedW > 0 ? savedW : DEFAULT_WIDTH));
        int initH = Math.max(MIN_HEIGHT, Math.min(screenMax.height, savedH > 0 ? savedH : DEFAULT_HEIGHT));
        setSize(initW, initH);
        setDefaultCloseOperation(HIDE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG_WINDOW);
        root.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1));

        root.add(buildHeader(), BorderLayout.NORTH);

        contentCards.setOpaque(false);
        contentCards.add(buildPeersView(), CARD_PEERS);

        chatViewPanel = new JPanel(new BorderLayout());
        chatViewPanel.setOpaque(false);

        messagesContainer.setLayout(new BoxLayout(messagesContainer, BoxLayout.Y_AXIS));
        messagesContainer.setBackground(Theme.BG_WINDOW);
        messagesContainer.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        messagesScroll = new JScrollPane(messagesContainer);
        messagesScroll.setBorder(null);
        messagesScroll.getViewport().setBackground(Theme.BG_WINDOW);
        messagesScroll.getVerticalScrollBar().setUnitIncrement(14);
        messagesScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        messagesScroll.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                relayoutMessageBubblesIfNeeded();
            }
        });

        dropBanner.setFont(Theme.FONT_BOLD);
        dropBanner.setForeground(Theme.ACCENT);
        dropBanner.setBackground(Theme.BG_CARD);
        dropBanner.setOpaque(true);
        dropBanner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.ACCENT),
                BorderFactory.createEmptyBorder(7, 10, 7, 10)
        ));
        dropBanner.setVisible(false);

        attachmentStrip.setLayout(new BoxLayout(attachmentStrip, BoxLayout.Y_AXIS));
        attachmentStrip.setBackground(Theme.BG_HEADER);
        attachmentStrip.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(6, 10, 4, 10)
        ));
        attachmentStrip.setVisible(false);

        chatInputBar = buildInputBar();

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.setOpaque(false);
        southPanel.add(attachmentStrip, BorderLayout.NORTH);
        southPanel.add(chatInputBar, BorderLayout.SOUTH);

        chatViewPanel.add(dropBanner, BorderLayout.NORTH);
        chatViewPanel.add(messagesScroll, BorderLayout.CENTER);
        chatViewPanel.add(southPanel, BorderLayout.SOUTH);
        installChatClipboardAndDrop(chatViewPanel, southPanel);

        contentCards.add(chatViewPanel, CARD_CHAT);

        JScrollPane settingsScroll = new JScrollPane(buildSettingsView());
        settingsScroll.setBorder(null);
        settingsScroll.getViewport().setBackground(Theme.BG_WINDOW);
        settingsScroll.getVerticalScrollBar().setUnitIncrement(14);
        settingsScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        contentCards.add(settingsScroll, CARD_SETTINGS);
        root.add(contentCards, BorderLayout.CENTER);

        setContentPane(root);
        WindowResizer.install(this, 16, MIN_WIDTH, MIN_HEIGHT, 44,
                () -> settings.setWindowSize(getWidth(), getHeight()));
        centerOnScreen();
        applyLanguageTexts();
        loadStoredHistory();

        root.registerKeyboardAction(
                e -> setVisible(false),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JPanel.WHEN_IN_FOCUSED_WINDOW
        );
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        boolean sizeChanged = width != getWidth() || height != getHeight();
        super.setBounds(x, y, width, height);
        if (sizeChanged && isUndecorated() && width > 0 && height > 0) {
            WindowResizer.updateRoundShape(this, 16);
        }
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

        if (CARD_CHAT.equals(currentCard)) {
            boolean cleared = false;
            if (activeGroup != null) {
                cleared = unreadCounts.remove(groupKey(activeGroup.getId())) != null;
            } else if (activePeer != null) {
                cleared = unreadCounts.remove(peerKey(activePeer.getId())) != null;
            }
            if (cleared) {
                renderPeerList();
            }
        }

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
            if (CARD_CHAT.equals(currentCard)) {
                relayoutMessageBubblesIfNeeded();
            }
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
        cardLayout.show(contentCards, CARD_CHAT);
        renderMessages();
        renderPeerList();
        showWindow();
        SwingUtilities.invokeLater(() -> {
            relayoutMessageBubblesIfNeeded();
            inputField.requestFocusInWindow();
        });
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
        cardLayout.show(contentCards, CARD_CHAT);
        renderMessages();
        renderPeerList();
        showWindow();
        SwingUtilities.invokeLater(() -> {
            relayoutMessageBubblesIfNeeded();
            inputField.requestFocusInWindow();
        });
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
        keepMessagesCheck.setSelected(settings.isKeepMessages());
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
        boolean wasEmpty = appendHistory(key, message);

        boolean viewing = CARD_CHAT.equals(currentCard)
                && activePeer != null && activeGroup == null
                && activePeer.getId().equals(sender.getId());
        if (viewing) {
            if (wasEmpty) {
                renderMessages();
            } else {
                appendMessageBubble(message, false);
                scrollToBottom();
            }
        }
        if (!isChattingWith(sender)) {
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
        boolean wasEmpty = appendHistory(key, message);

        boolean viewing = CARD_CHAT.equals(currentCard)
                && activeGroup != null
                && activeGroup.getId().equals(local.getId());
        if (viewing) {
            if (wasEmpty) {
                renderMessages();
            } else {
                appendMessageBubble(message, true);
                scrollToBottom();
            }
        }
        if (!isChattingWithGroup(local)) {
            unreadCounts.merge(key, 1, Integer::sum);
            renderPeerList();
        }
        return local;
    }

    public ChatGroup handleIncomingGroupFileOffered(Peer sender, ChatGroup incoming, ChatMessage message) {
        if (settings.isGroupDeleted(incoming.getId())) {
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

        FileAttachment att = message.getAttachment();
        if (att != null && att.isEncryptedPendingUnlock() && local.hasPasswordKey()) {
            byte[] key = local.getPasswordKeyBytes();
            if (key != null) {
                decryptGroupOfferMetadata(message, key);
            }
        }

        String key = groupKey(local.getId());
        boolean wasEmpty = appendHistory(key, message);

        boolean viewing = CARD_CHAT.equals(currentCard)
                && activeGroup != null
                && activeGroup.getId().equals(local.getId());
        if (viewing) {
            if (wasEmpty) {
                renderMessages();
            } else {
                appendMessageBubble(message, true);
                scrollToBottom();
            }
        }
        if (!isChattingWithGroup(local)) {
            unreadCounts.merge(key, 1, Integer::sum);
            renderPeerList();
        }
        return local;
    }

    public void handleRemoteMessageEdited(Peer sender, String messageId, String newText) {
        if (sender == null || messageId == null) {
            return;
        }
        String key = peerKey(sender.getId());
        ChatMessage target = findIncomingMessage(key, messageId, sender);
        if (target == null) {
            return;
        }
        target.editText(newText);
        scheduleHistorySave();
        if (isViewingPeerConversation(sender.getId())) {
            refreshMessageBubbleInView(target);
        }
    }

    public void handleRemoteMessageDeleted(Peer sender, String messageId) {
        if (sender == null || messageId == null) {
            return;
        }
        String key = peerKey(sender.getId());
        ChatMessage removed = removeIncomingMessage(key, messageId, sender);
        if (removed == null) {
            return;
        }
        cleanupMessageResources(removed);
        scheduleHistorySave();
        if (isViewingPeerConversation(sender.getId())) {
            removeMessageBubbleFromView(removed);
        } else if (decrementUnread(key)) {
            renderPeerList();
        }
    }

    public void handleRemoteGroupMessageEdited(Peer sender, String groupId, String messageId,
                                               String salt, String check, String iv, String payload) {
        if (sender == null || groupId == null || messageId == null
                || settings.isGroupDeleted(groupId)) {
            return;
        }
        ChatGroup local = settings.getGroup(groupId);
        if (local == null || !local.hasMember(sender.getId())) {
            return;
        }
        String key = groupKey(groupId);
        ChatMessage target = findIncomingMessage(key, messageId, sender);
        if (target == null) {
            return;
        }

        if (local.isPasswordProtected()) {
            if (salt == null || check == null || salt.isEmpty() || check.isEmpty()
                    || !salt.equals(local.getPasswordSalt())
                    || !check.equals(local.getPasswordCheck())) {
                return;
            }
            if (local.hasPasswordKey()) {
                byte[] groupKeyBytes = local.getPasswordKeyBytes();
                if (groupKeyBytes == null) {
                    return;
                }
                try {
                    String decrypted = GroupCrypto.decrypt(groupKeyBytes, iv, payload);
                    target.updateEncryptedPayload(iv, payload, ChatMessage.truncate(decrypted), true);
                } catch (GeneralSecurityException | IllegalArgumentException e) {
                    return;
                }
            } else {
                target.updateEncryptedPayload(iv, payload, null, true);
            }
        } else {
            if ((salt != null && !salt.isEmpty()) || (check != null && !check.isEmpty())) {
                return;
            }
            try {
                String decoded = new String(Base64.getDecoder().decode(payload), StandardCharsets.UTF_8);
                target.editText(decoded);
            } catch (IllegalArgumentException e) {
                return;
            }
        }

        scheduleHistorySave();
        if (isViewingGroupConversation(groupId)) {
            refreshMessageBubbleInView(target);
        }
    }

    public void handleRemoteGroupMessageDeleted(Peer sender, String groupId, String messageId) {
        if (sender == null || groupId == null || messageId == null
                || settings.isGroupDeleted(groupId)) {
            return;
        }
        ChatGroup local = settings.getGroup(groupId);
        if (local == null || !local.hasMember(sender.getId())) {
            return;
        }
        String key = groupKey(groupId);
        ChatMessage removed = removeIncomingMessage(key, messageId, sender);
        if (removed == null) {
            return;
        }
        cleanupMessageResources(removed);
        scheduleHistorySave();
        if (isViewingGroupConversation(groupId)) {
            removeMessageBubbleFromView(removed);
        } else if (decrementUnread(key)) {
            renderPeerList();
        }
    }

    private boolean isViewingPeerConversation(String peerId) {
        return CARD_CHAT.equals(currentCard)
                && activePeer != null && activeGroup == null
                && activePeer.getId().equals(peerId);
    }

    private boolean isViewingGroupConversation(String groupId) {
        return CARD_CHAT.equals(currentCard)
                && activeGroup != null
                && activeGroup.getId().equals(groupId);
    }

    private ChatMessage findIncomingMessage(String historyKey, String messageId, Peer sender) {
        synchronized (histories) {
            List<ChatMessage> list = histories.get(historyKey);
            if (list == null) {
                return null;
            }
            for (ChatMessage msg : list) {
                if (msg.getId().equals(messageId) && msg.isIncoming() && matchesSender(msg, sender)) {
                    return msg;
                }
            }
        }
        return null;
    }

    private ChatMessage removeIncomingMessage(String historyKey, String messageId, Peer sender) {
        synchronized (histories) {
            List<ChatMessage> list = histories.get(historyKey);
            if (list == null) {
                return null;
            }
            for (Iterator<ChatMessage> it = list.iterator(); it.hasNext(); ) {
                ChatMessage msg = it.next();
                if (msg.getId().equals(messageId) && msg.isIncoming() && matchesSender(msg, sender)) {
                    it.remove();
                    return msg;
                }
            }
        }
        return null;
    }

    private static boolean matchesSender(ChatMessage msg, Peer sender) {
        String msgSenderId = msg.getSenderId();
        if (msgSenderId != null && !msgSenderId.isEmpty()) {
            return msgSenderId.equals(sender.getId());
        }
        return Objects.equals(msg.getSenderName(), sender.getName());
    }

    private boolean decrementUnread(String key) {
        Integer existing = unreadCounts.get(key);
        if (existing == null) {
            return false;
        }
        if (existing <= 1) {
            unreadCounts.remove(key);
        } else {
            unreadCounts.put(key, existing - 1);
        }
        return true;
    }

    /**
     * Restores saved conversations when the setting is enabled. Histories of
     * groups that were deleted locally are ignored.
     */
    private void loadStoredHistory() {
        if (!settings.isKeepMessages()) {
            return;
        }

        Map<String, List<ChatMessage>> stored = historyStore.load();
        synchronized (histories) {
            histories.putAll(stored);
            histories.entrySet().removeIf(entry -> {
                String key = entry.getKey();
                return key.startsWith("group:")
                        && settings.isGroupDeleted(key.substring("group:".length()));
            });
        }

        // Password-protected groups that were already unlocked can show their
        // decrypted text immediately, without asking for the password again.
        for (ChatGroup group : settings.getGroups()) {
            if (group.isPasswordProtected() && group.hasPasswordKey()) {
                byte[] key = group.getPasswordKeyBytes();
                if (key != null) {
                    decryptStoredMessages(group, key);
                }
            }
        }
    }

    /**
     * Writes the current histories immediately. Called on shutdown and after
     * every change, debounced so fast message bursts only write once.
     */
    public void saveHistoryNow() {
        historySaveTimer.stop();
        if (!settings.isKeepMessages()) {
            historyStore.clear();
            return;
        }

        Map<String, List<ChatMessage>> snapshot = new HashMap<>();
        synchronized (histories) {
            for (Map.Entry<String, List<ChatMessage>> entry : histories.entrySet()) {
                snapshot.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }
        historyStore.save(snapshot);
    }

    private void scheduleHistorySave() {
        if (settings.isKeepMessages()) {
            historySaveTimer.restart();
        }
    }

    /**
     * Adds a message to the in-memory history and persists it shortly after.
     *
     * @return {@code true} if this was the first message of the conversation
     */
    private boolean appendHistory(String key, ChatMessage message) {
        boolean wasEmpty;
        synchronized (histories) {
            List<ChatMessage> list = histories.computeIfAbsent(key, k -> new ArrayList<>());
            wasEmpty = list.isEmpty();
            list.add(message);
        }
        scheduleHistorySave();
        return wasEmpty;
    }

    private void removeHistory(String key) {
        synchronized (histories) {
            histories.remove(key);
        }
        scheduleHistorySave();
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

        header.add(left, BorderLayout.CENTER);
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

        selfBar.add(selfInfo, BorderLayout.CENTER);
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
                BorderFactory.createEmptyBorder(8, 10, 12, 18)
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

        messagesSectionLabel = sectionLabel("");
        panel.add(messagesSectionLabel);

        styleCheckBox(keepMessagesCheck);
        keepMessagesCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        keepMessagesCheck.setSelected(settings.isKeepMessages());
        keepMessagesCheck.addActionListener(e -> {
            boolean keep = keepMessagesCheck.isSelected();
            settings.setKeepMessages(keep);
            if (keep) {
                saveHistoryNow();
            } else {
                historySaveTimer.stop();
                historyStore.clear();
            }
        });
        panel.add(keepMessagesCheck);

        keepMessagesHintLabel = new JLabel();
        keepMessagesHintLabel.setFont(Theme.FONT_SMALL);
        keepMessagesHintLabel.setForeground(Theme.TEXT_MUTED);
        keepMessagesHintLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        keepMessagesHintLabel.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
        panel.add(keepMessagesHintLabel);

        panel.add(Box.createVerticalStrut(14));

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
        JPanel bar = new JPanel(new BorderLayout(6, 0));
        bar.setBackground(Theme.BG_HEADER);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(8, 10, 12, 18)
        ));

        attachButton.setBorder(BorderFactory.createEmptyBorder());
        attachButton.setPreferredSize(new Dimension(32, 32));
        attachButton.setMinimumSize(new Dimension(32, 32));
        attachButton.setToolTipText(I18n.t(I18n.Key.ATTACH_FILE_TOOLTIP));
        attachButton.addActionListener(e -> promptSelectFiles());

        styleTextField(inputField);
        inputField.setFont(Theme.FONT_BODY);
        inputField.addActionListener(e -> sendCurrentMessage());
        installLengthLimit(inputField, ChatMessage.MAX_TEXT_LENGTH);
        installInputPasteHandler();
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
        sendBtn.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
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

        bar.add(attachButton, BorderLayout.WEST);
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
        if (text.isEmpty() && pendingAttachments.isEmpty()) return;
        if (text.length() > ChatMessage.MAX_TEXT_LENGTH) {
            JOptionPane.showMessageDialog(this,
                    I18n.t(I18n.Key.MESSAGE_TOO_LONG,
                            text.length(), ChatMessage.MAX_TEXT_LENGTH),
                    I18n.t(I18n.Key.MESSAGE_TOO_LONG_TITLE),
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (!pendingAttachments.isEmpty()) {
            sendPendingAttachments(text);
            return;
        }

        if (activeGroup != null) {
            inputField.setText("");
            String key = groupKey(activeGroup.getId());
            ChatMessage msg = ChatMessage.outgoing(
                    activeGroup.getId(), network.getMyId(), network.getMyName(), text, false);
            int delivered = network.sendGroupMessage(activeGroup, msg.getId(), text);
            if (delivered <= 0) {
                msg.setNotDelivered(true);
            }
            boolean wasEmpty = appendHistory(key, msg);
            if (wasEmpty) {
                renderMessages();
            } else {
                appendMessageBubble(msg, false);
                scrollToBottom();
            }
            return;
        }

        if (activePeer == null) return;
        inputField.setText("");
        String key = peerKey(activePeer.getId());
        ChatMessage msg = ChatMessage.outgoing(
                activePeer.getId(), network.getMyId(), network.getMyName(), text, false);
        boolean sent = network.sendMessage(activePeer, msg.getId(), text);
        if (!sent) {
            msg.setNotDelivered(true);
        }
        boolean wasEmpty = appendHistory(key, msg);
        if (wasEmpty) {
            renderMessages();
        } else {
            appendMessageBubble(msg, false);
            scrollToBottom();
        }
    }

    private void sendPendingAttachments(String text) {
        if (activeGroup == null && activePeer == null) {
            return;
        }
        List<PendingAttachment> toSend = new ArrayList<>(pendingAttachments);
        pendingAttachments.clear();
        renderAttachmentStrip();
        inputField.setText("");

        String targetKey = activeGroup != null
                ? groupKey(activeGroup.getId())
                : peerKey(activePeer.getId());
        String targetId = activeGroup != null ? activeGroup.getId() : activePeer.getId();

        boolean wasEmpty = true;

        for (int i = 0; i < toSend.size(); i++) {
            PendingAttachment pa = toSend.get(i);
            String caption = (i == 0) ? text : "";
            FileAttachment att = new FileAttachment(
                    null, pa.displayName, pa.size, pa.image, pa.file,
                    FileAttachment.Status.TRANSFERRING);
            ChatMessage msg = ChatMessage.outgoing(
                    targetId, network.getMyId(), network.getMyName(), caption, att);
            boolean emptyBeforeAppend = appendHistory(targetKey, msg);
            if (i == 0) {
                wasEmpty = emptyBeforeAppend;
            }

            if (activeGroup != null) {
                network.sendGroupFileAsync(activeGroup, pa.file, att, caption);
            } else {
                network.sendFileAsync(activePeer, pa.file, att, caption);
            }

            if (!wasEmpty) {
                appendMessageBubble(msg, false);
            }
        }

        if (wasEmpty) {
            renderMessages();
        } else {
            scrollToBottom();
        }
    }

    private void promptSelectFiles() {
        FileDialog fd = new FileDialog(this, I18n.t(I18n.Key.SELECT_FILE_TITLE), FileDialog.LOAD);
        fd.setMultipleMode(true);
        fd.setVisible(true);
        File[] files = fd.getFiles();
        if (files != null && files.length > 0) {
            stageFiles(Arrays.asList(files));
        }
        SwingUtilities.invokeLater(inputField::requestFocusInWindow);
    }

    private void installInputPasteHandler() {
        Action defaultPaste = inputField.getActionMap().get(DefaultEditorKit.pasteAction);
        inputField.getActionMap().put(DefaultEditorKit.pasteAction, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!handleClipboardPaste(false) && defaultPaste != null) {
                    defaultPaste.actionPerformed(e);
                }
            }
        });
    }

    private void installChatClipboardAndDrop(JPanel chatView, JPanel southPanel) {
        chatView.registerKeyboardAction(
                e -> {
                    if (!inputField.isFocusOwner()) {
                        handleClipboardPaste(true);
                    }
                },
                KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK),
                JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
        );

        fileDropHandler = new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return hasSupportedDropFlavor(support.getDataFlavors());
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (dropHighlightClearTimer != null) {
                    dropHighlightClearTimer.stop();
                }
                setDropHighlight(false);
                if (!canImport(support)) {
                    return false;
                }
                boolean imported = handleTransferable(support.getTransferable(), false);
                if (imported) {
                    SwingUtilities.invokeLater(() -> {
                        toFront();
                        inputField.requestFocusInWindow();
                    });
                }
                return imported;
            }
        };

        dropHighlightClearTimer = new Timer(80, e -> setDropHighlight(false));
        dropHighlightClearTimer.setRepeats(false);

        installDropSupport(chatView);
        installDropSupport(dropBanner);
        installDropSupport(messagesScroll);
        installDropSupport(messagesScroll.getViewport());
        installDropSupport(messagesContainer);
        installDropSupport(southPanel);
        installDropSupport(attachmentStrip);
        installDropSupport(chatInputBar);
        installDropSupport(attachButton);
        installDropSupport(inputField);
    }

    private void installDropSupport(JComponent component) {
        if (component == null || fileDropHandler == null) {
            return;
        }
        if (component instanceof JTextComponent textComp) {
            TransferHandler existing = textComp.getTransferHandler();
            if (!(existing instanceof DelegatingDropTransferHandler)) {
                textComp.setTransferHandler(new DelegatingDropTransferHandler(existing, fileDropHandler));
            }
        } else {
            component.setTransferHandler(fileDropHandler);
        }
        attachDropHighlightListener(component);
    }

    private void attachDropHighlightListener(JComponent component) {
        DropTarget target = component.getDropTarget();
        if (target == null) {
            return;
        }
        try {
            target.addDropTargetListener(new DropTargetAdapter() {
                @Override
                public void dragEnter(DropTargetDragEvent dtde) {
                    handleDragUpdate(dtde);
                }

                @Override
                public void dragOver(DropTargetDragEvent dtde) {
                    handleDragUpdate(dtde);
                }

                @Override
                public void dragExit(DropTargetEvent dte) {
                    scheduleClearDropHighlight();
                }

                @Override
                public void drop(DropTargetDropEvent dtde) {
                    if (dropHighlightClearTimer != null) {
                        dropHighlightClearTimer.stop();
                    }
                    setDropHighlight(false);
                }
            });
        } catch (TooManyListenersException ignored) {
        }
    }

    private void handleDragUpdate(DropTargetDragEvent dtde) {
        if (!CARD_CHAT.equals(currentCard)) {
            return;
        }
        if (hasSupportedDropFlavor(dtde.getCurrentDataFlavors())) {
            if (dropHighlightClearTimer != null) {
                dropHighlightClearTimer.stop();
            }
            setDropHighlight(true);
        }
    }

    private void scheduleClearDropHighlight() {
        if (dropHighlightClearTimer != null) {
            dropHighlightClearTimer.restart();
        } else {
            setDropHighlight(false);
        }
    }

    private void setDropHighlight(boolean active) {
        if (chatViewPanel == null) {
            return;
        }
        if (dropBanner.isVisible() == active) {
            return;
        }
        dropBanner.setVisible(active);
        chatViewPanel.setBorder(active ? BorderFactory.createLineBorder(Theme.ACCENT, 2) : null);
        chatViewPanel.revalidate();
        chatViewPanel.repaint();
    }

    private static boolean hasSupportedDropFlavor(DataFlavor[] flavors) {
        if (flavors == null) {
            return false;
        }
        for (DataFlavor flavor : flavors) {
            if (DataFlavor.javaFileListFlavor.equals(flavor)
                    || DataFlavor.imageFlavor.equals(flavor)
                    || isUriListFlavor(flavor)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isUriListFlavor(DataFlavor flavor) {
        return flavor != null
                && "text".equalsIgnoreCase(flavor.getPrimaryType())
                && "uri-list".equalsIgnoreCase(flavor.getSubType())
                && String.class.isAssignableFrom(flavor.getRepresentationClass());
    }

    private static List<File> extractFilesFromUriList(String uriList) {
        List<File> files = new ArrayList<>();
        if (uriList == null || uriList.isBlank()) {
            return files;
        }
        for (String rawLine : uriList.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            try {
                URI uri = URI.create(line);
                if ("file".equalsIgnoreCase(uri.getScheme())) {
                    File file = Paths.get(uri).toFile();
                    if (file.exists()) {
                        files.add(file);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return files;
    }

    private final class DelegatingDropTransferHandler extends TransferHandler {
        private static final long serialVersionUID = 1L;
        private final TransferHandler delegate;
        private final TransferHandler dropHandler;

        private DelegatingDropTransferHandler(TransferHandler delegate, TransferHandler dropHandler) {
            this.delegate = delegate;
            this.dropHandler = dropHandler;
        }

        @Override
        public boolean canImport(TransferSupport support) {
            if (dropHandler.canImport(support)) {
                return true;
            }
            return delegate != null && delegate.canImport(support);
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (dropHandler.canImport(support) && dropHandler.importData(support)) {
                return true;
            }
            return delegate != null && delegate.importData(support);
        }

        @Override
        public int getSourceActions(JComponent c) {
            return delegate != null ? delegate.getSourceActions(c) : COPY;
        }

        @Override
        public void exportToClipboard(JComponent comp, Clipboard clip, int action)
                throws IllegalStateException {
            if (delegate != null) {
                delegate.exportToClipboard(comp, clip, action);
            } else {
                super.exportToClipboard(comp, clip, action);
            }
        }
    }

    private boolean handleClipboardPaste(boolean allowTextIntoInput) {
        if (!CARD_CHAT.equals(currentCard)) {
            return false;
        }
        try {
            Transferable transferable = Toolkit.getDefaultToolkit()
                    .getSystemClipboard().getContents(null);
            if (transferable == null) {
                return false;
            }
            return handleTransferable(transferable, allowTextIntoInput);
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private boolean handleTransferable(Transferable transferable, boolean allowTextIntoInput) {
        try {
            if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                Object data = transferable.getTransferData(DataFlavor.javaFileListFlavor);
                if (data instanceof List<?>) {
                    List<File> files = new ArrayList<>();
                    for (Object item : (List<?>) data) {
                        if (item instanceof File) {
                            files.add((File) item);
                        }
                    }
                    if (!files.isEmpty()) {
                        stageFiles(files);
                        inputField.requestFocusInWindow();
                        return true;
                    }
                }
            }

            if (transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                Object data = transferable.getTransferData(DataFlavor.imageFlavor);
                if (data instanceof Image) {
                    BufferedImage bi = UiIcons.toBufferedImage((Image) data);
                    if (bi != null) {
                        stageClipboardImage(bi);
                        inputField.requestFocusInWindow();
                        return true;
                    }
                }
            }

            for (DataFlavor flavor : transferable.getTransferDataFlavors()) {
                if (isUriListFlavor(flavor)) {
                    Object data = transferable.getTransferData(flavor);
                    if (data instanceof String uriList) {
                        List<File> files = extractFilesFromUriList(uriList);
                        if (!files.isEmpty()) {
                            stageFiles(files);
                            inputField.requestFocusInWindow();
                            return true;
                        }
                    }
                }
            }

            if (allowTextIntoInput && transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                inputField.requestFocusInWindow();
                inputField.paste();
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private void stageClipboardImage(BufferedImage image) {
        String name = "screenshot-" + SCREENSHOT_TIME.format(LocalDateTime.now()) + ".png";
        new Thread(() -> {
            try {
                Path dir = network.getTransferDir();
                Files.createDirectories(dir);
                Path out = dir.resolve(UUID.randomUUID().toString().substring(0, 8) + "-" + name);
                ImageIO.write(image, "png", out.toFile());
                long size = Files.size(out);
                ImageIcon thumb = createThumbIcon(out);
                PendingAttachment pa = new PendingAttachment(out, name, size, true, thumb);
                SwingUtilities.invokeLater(() -> {
                    pendingAttachments.add(pa);
                    renderAttachmentStrip();
                });
            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                        this,
                        I18n.t(I18n.Key.FILE_READ_ERROR, ex.getMessage()),
                        I18n.t(I18n.Key.AERO_ERROR_TITLE),
                        JOptionPane.ERROR_MESSAGE));
            }
        }, "aero-clipboard-img").start();
    }

    private void stageFiles(List<File> files) {
        List<File> valid = new ArrayList<>();
        for (File f : files) {
            if (f != null && f.isFile() && f.canRead()) {
                valid.add(f);
            }
        }
        if (valid.isEmpty()) {
            return;
        }
        new Thread(() -> {
            List<PendingAttachment> staged = new ArrayList<>();
            for (File f : valid) {
                Path path = f.toPath();
                String name = FileAttachment.sanitizeFileName(f.getName());
                long size = f.length();
                boolean isImage = FileAttachment.isImageFileName(name);
                ImageIcon thumb = null;
                if (isImage) {
                    thumb = createThumbIcon(path);
                    if (thumb == null) {
                        isImage = false;
                    }
                }
                staged.add(new PendingAttachment(path, name, size, isImage, thumb));
            }
            SwingUtilities.invokeLater(() -> {
                pendingAttachments.addAll(staged);
                renderAttachmentStrip();
            });
        }, "aero-stage-files").start();
    }

    private ImageIcon createThumbIcon(Path path) {
        try {
            BufferedImage img = UiIcons.loadScaledImage(path, 34, 34);
            return img == null ? null : new ImageIcon(img);
        } catch (IOException e) {
            return null;
        }
    }

    private void renderAttachmentStrip() {
        attachmentStrip.removeAll();
        if (pendingAttachments.isEmpty()) {
            attachmentStrip.setVisible(false);
            attachmentStrip.revalidate();
            attachmentStrip.repaint();
            return;
        }

        attachmentStrip.setVisible(true);
        for (int i = 0; i < pendingAttachments.size(); i++) {
            final int index = i;
            PendingAttachment pa = pendingAttachments.get(i);

            JPanel item = new JPanel(new BorderLayout(8, 0));
            item.setBackground(Theme.BG_CARD);
            item.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Theme.BORDER, 1),
                    BorderFactory.createEmptyBorder(4, 6, 4, 6)
            ));
            item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));

            JLabel iconLabel = new JLabel(pa.thumbnail != null
                    ? pa.thumbnail
                    : UiIcons.fileBadge(Theme.TEXT_PRIMARY, 20));
            iconLabel.setPreferredSize(new Dimension(34, 34));
            iconLabel.setHorizontalAlignment(JLabel.CENTER);

            JPanel info = new JPanel(new BorderLayout(0, 1));
            info.setOpaque(false);
            JLabel nameLabel = new JLabel(pa.displayName);
            nameLabel.setFont(Theme.FONT_BOLD);
            nameLabel.setForeground(Theme.TEXT_PRIMARY);
            JLabel sizeLabel = new JLabel(FileAttachment.formatSize(pa.size));
            sizeLabel.setFont(Theme.FONT_SMALL);
            sizeLabel.setForeground(Theme.TEXT_MUTED);
            info.add(nameLabel, BorderLayout.NORTH);
            info.add(sizeLabel, BorderLayout.SOUTH);

            JButton removeBtn = UiIcons.darkButton(
                    null,
                    UiIcons.cancel(Theme.TEXT_MUTED, 11),
                    Theme.BTN_DARK,
                    Theme.BTN_DANGER_HOVER,
                    Theme.BTN_DARK_BORDER,
                    Theme.TEXT_PRIMARY,
                    6
            );
            removeBtn.setPreferredSize(new Dimension(22, 22));
            removeBtn.setBorder(BorderFactory.createEmptyBorder());
            removeBtn.setToolTipText(I18n.t(I18n.Key.REMOVE_ATTACHMENT_TOOLTIP));
            removeBtn.addActionListener(e -> {
                if (index < pendingAttachments.size()) {
                    pendingAttachments.remove(index);
                    renderAttachmentStrip();
                }
            });

            JPanel removeWrap = new JPanel(new GridBagLayout());
            removeWrap.setOpaque(false);
            removeWrap.add(removeBtn);

            item.add(iconLabel, BorderLayout.WEST);
            item.add(info, BorderLayout.CENTER);
            item.add(removeWrap, BorderLayout.EAST);

            attachmentStrip.add(item);
            if (i < pendingAttachments.size() - 1) {
                attachmentStrip.add(Box.createVerticalStrut(4));
            }
        }
        attachmentStrip.revalidate();
        attachmentStrip.repaint();
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

        JPanel left = new JPanel(new BorderLayout(8, 0));
        left.setOpaque(false);
        left.add(dot, BorderLayout.WEST);
        left.add(name, BorderLayout.CENTER);

        card.add(left, BorderLayout.CENTER);

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

        JPanel left = new JPanel(new BorderLayout(8, 0));
        left.setOpaque(false);
        left.add(icon, BorderLayout.WEST);
        left.add(texts, BorderLayout.CENTER);

        card.add(left, BorderLayout.CENTER);

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
        left.setComponentPopupMenu(popup);
        texts.setComponentPopupMenu(popup);
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
        for (Component comp : messagesContainer.getComponents()) {
            if (comp instanceof MessageRow row && row.msg.hasAttachment()) {
                row.msg.getAttachment().setChangeListener(null);
            }
        }
        messagesContainer.removeAll();
        lastMessagesViewportWidth = messagesScroll.getViewport().getWidth();

        String key = activeConversationKey();
        List<ChatMessage> messages;
        if (key == null) {
            messages = List.of();
        } else {
            synchronized (histories) {
                messages = new ArrayList<>(histories.getOrDefault(key, List.of()));
            }
        }

        if (messages.isEmpty()) {
            messagesContainer.add(createEmptyMessagesPlaceholder());
        } else {
            boolean showSender = activeGroup != null;
            for (ChatMessage message : messages) {
                appendMessageBubble(message, showSender);
            }
        }

        messagesContainer.invalidate();
        messagesScroll.validate();
        messagesContainer.repaint();
        scrollToBottom();
    }

    private JTextArea createEmptyMessagesPlaceholder() {
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
        installDropSupport(empty);
        return empty;
    }

    private String activeConversationKey() {
        if (activeGroup != null) {
            return groupKey(activeGroup.getId());
        }
        if (activePeer != null) {
            return peerKey(activePeer.getId());
        }
        return null;
    }

    private int currentMaxBubbleTextWidth() {
        int viewportW = messagesScroll.getViewport().getWidth();
        if (viewportW <= 0) {
            viewportW = Math.max(MIN_WIDTH, getWidth()) - 20;
        }
        int availableRowW = Math.max(160, viewportW - 20);
        int maxBubbleW = (int) Math.round(availableRowW * 0.80);
        return Math.max(120, maxBubbleW - 24);
    }

    private void relayoutMessageBubblesIfNeeded() {
        int vw = messagesScroll.getViewport().getWidth();
        if (vw <= 0 || vw == lastMessagesViewportWidth) {
            return;
        }
        lastMessagesViewportWidth = vw;
        JScrollBar bar = messagesScroll.getVerticalScrollBar();
        boolean wasAtBottom = bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum() - 24;
        int maxTextW = currentMaxBubbleTextWidth();
        for (Component comp : messagesContainer.getComponents()) {
            if (comp instanceof MessageRow row) {
                row.updateWrapWidth(maxTextW);
            }
        }
        messagesContainer.invalidate();
        messagesScroll.validate();
        messagesContainer.repaint();
        if (wasAtBottom) {
            scrollToBottom();
        }
    }

    private void appendMessageBubble(ChatMessage msg, boolean showSender) {
        MessageRow row = new MessageRow(msg, showSender, currentMaxBubbleTextWidth());
        messagesContainer.add(row);
        messagesContainer.revalidate();
    }

    private void refreshMessageBubbleInView(ChatMessage msg) {
        int maxTextW = currentMaxBubbleTextWidth();
        for (Component comp : messagesContainer.getComponents()) {
            if (comp instanceof MessageRow row && row.msg == msg) {
                row.updateTextAndMeta(maxTextW);
                messagesContainer.revalidate();
                messagesContainer.repaint();
                return;
            }
        }
        renderMessages();
    }

    private void removeMessageBubbleFromView(ChatMessage msg) {
        String key = activeConversationKey();
        boolean emptyNow = true;
        if (key != null) {
            synchronized (histories) {
                List<ChatMessage> list = histories.get(key);
                emptyNow = list == null || list.isEmpty();
            }
        }
        if (emptyNow) {
            renderMessages();
            return;
        }
        for (Component comp : messagesContainer.getComponents()) {
            if (comp instanceof MessageRow row && row.msg == msg) {
                messagesContainer.remove(row);
                messagesContainer.revalidate();
                messagesContainer.repaint();
                return;
            }
        }
    }

    private JTextArea createSelectableBubbleText(String content, int maxTextWidth, MessageRow row) {
        JTextArea text = new JTextArea(content);
        text.setEditable(false);
        text.setWrapStyleWord(true);
        text.setLineWrap(true);
        text.setOpaque(false);
        text.setForeground(Theme.TEXT_PRIMARY);
        text.setSelectionColor(new Color(255, 255, 255, 80));
        text.setSelectedTextColor(Color.WHITE);
        text.setFont(Theme.FONT_BODY);
        applyBubbleTextWidth(text, maxTextWidth);
        attachMessagePopup(text, row);
        installDropSupport(text);
        return text;
    }

    private static void applyBubbleTextWidth(JTextArea text, int maxTextWidth) {
        String s = text.getText();
        FontMetrics fm = text.getFontMetrics(text.getFont());
        int naturalMax = 0;
        if (s != null && !s.isEmpty()) {
            for (String line : s.split("\n", -1)) {
                naturalMax = Math.max(naturalMax, fm.stringWidth(line));
            }
        }
        int targetW = Math.max(24, Math.min(maxTextWidth, naturalMax + 8));
        Insets ins = text.getInsets();
        int insetW = ins.left + ins.right;
        int insetH = ins.top + ins.bottom;

        text.setSize(new Dimension(targetW + insetW, 100_000));
        View view = text.getUI().getRootView(text);
        view.setSize(targetW, 100_000f);

        if (s != null && !s.contains("\n") && targetW < maxTextWidth) {
            int singleLineMaxH = (int) Math.ceil(fm.getHeight() * 1.4);
            while (targetW < maxTextWidth && view.getPreferredSpan(View.Y_AXIS) > singleLineMaxH) {
                targetW = Math.min(maxTextWidth, targetW + 16);
                text.setSize(new Dimension(targetW + insetW, 100_000));
                view.setSize(targetW, 100_000f);
            }
        }

        int prefH = Math.max(fm.getHeight(), (int) Math.ceil(view.getPreferredSpan(View.Y_AXIS)));
        Dimension fixed = new Dimension(targetW + insetW, prefH + insetH);
        text.setPreferredSize(fixed);
        text.setMinimumSize(fixed);
        text.setMaximumSize(fixed);
    }

    private void attachMessagePopup(JComponent comp, MessageRow row) {
        comp.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowPopup(e);
            }

            private void maybeShowPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    buildMessagePopupMenu(row).show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });
    }

    private JPopupMenu buildMessagePopupMenu(MessageRow row) {
        ChatMessage msg = row.msg;
        FileAttachment att = msg.getAttachment();
        boolean pendingUnlock = att != null && att.isEncryptedPendingUnlock();
        String selectedText = row.textArea != null ? row.textArea.getSelectedText() : null;
        String fullText = msg.getText();
        boolean hasSelected = selectedText != null && !selectedText.isEmpty();
        boolean hasText = fullText != null && !fullText.isEmpty() && !pendingUnlock;

        JPopupMenu menu = new JPopupMenu();

        if (hasSelected || hasText) {
            JMenuItem copyItem = new JMenuItem(I18n.t(I18n.Key.COPY_TEXT));
            copyItem.addActionListener(e -> copyTextToClipboard(hasSelected ? selectedText : fullText));
            menu.add(copyItem);
        }

        if (att != null && !pendingUnlock) {
            boolean hasLocalFile = att.getLocalPath() != null
                    && Files.isRegularFile(att.getLocalPath())
                    && (!msg.isIncoming() || att.getStatus() == FileAttachment.Status.COMPLETED);
            boolean canDownload = hasLocalFile || att.canDownloadFromPeer();
            if (att.isImage() && hasLocalFile) {
                JMenuItem copyImg = new JMenuItem(I18n.t(I18n.Key.COPY_IMAGE));
                copyImg.addActionListener(e -> UiIcons.copyImageToClipboard(att.getLocalPath()));
                menu.add(copyImg);
            }
            if (canDownload) {
                JMenuItem saveItem = new JMenuItem(I18n.t(I18n.Key.DOWNLOAD_TOOLTIP));
                saveItem.addActionListener(e -> downloadAttachment(att));
                menu.add(saveItem);
            }
        }

        if (!msg.isIncoming() && !pendingUnlock) {
            JMenuItem editItem = new JMenuItem(I18n.t(I18n.Key.EDIT_MESSAGE));
            editItem.addActionListener(e -> promptEditMessage(msg));
            menu.add(editItem);
        }

        if (menu.getComponentCount() > 0) {
            menu.addSeparator();
        }

        JMenuItem deleteItem = new JMenuItem(I18n.t(I18n.Key.DELETE_MESSAGE));
        deleteItem.setForeground(Theme.DANGER);
        deleteItem.addActionListener(e -> confirmAndDeleteMessage(msg));
        menu.add(deleteItem);
        return menu;
    }

    private static void copyTextToClipboard(String text) {
        if (text == null) {
            return;
        }
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(text), null);
        } catch (IllegalStateException ignored) {
        }
    }

    private void promptEditMessage(ChatMessage msg) {
        if (msg == null || msg.isIncoming()) {
            return;
        }
        FileAttachment att = msg.getAttachment();
        if (att != null && att.isEncryptedPendingUnlock()) {
            return;
        }

        final String convKey = activeConversationKey();
        final ChatGroup targetGroup = activeGroup;
        final Peer targetPeer = activePeer;
        if (convKey == null) {
            return;
        }

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(Theme.BG_WINDOW);
        body.setBorder(BorderFactory.createEmptyBorder(12, 16, 8, 16));

        JTextArea editArea = new JTextArea(msg.getText());
        editArea.setLineWrap(true);
        editArea.setWrapStyleWord(true);
        editArea.setBackground(Theme.BG_INPUT);
        editArea.setForeground(Theme.TEXT_PRIMARY);
        editArea.setCaretColor(Theme.TEXT_PRIMARY);
        editArea.setFont(Theme.FONT_BODY);
        editArea.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        installLengthLimit(editArea, ChatMessage.MAX_TEXT_LENGTH);
        editArea.setCaretPosition(editArea.getDocument().getLength());

        JScrollPane editScroll = new JScrollPane(editArea);
        editScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1));
        editScroll.getViewport().setBackground(Theme.BG_INPUT);
        editScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        editScroll.getVerticalScrollBar().setUnitIncrement(12);
        editScroll.setPreferredSize(new Dimension(320, 120));
        editScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        editScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(editScroll);
        body.add(Box.createVerticalStrut(6));

        JPanel footer = new JPanel(new BorderLayout(8, 0));
        footer.setOpaque(false);
        footer.setAlignmentX(Component.LEFT_ALIGNMENT);
        footer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

        JLabel hintLabel = new JLabel(I18n.t(I18n.Key.EDIT_MESSAGE_HINT));
        hintLabel.setFont(Theme.FONT_SMALL);
        hintLabel.setForeground(Theme.TEXT_MUTED);

        JLabel counterLabel = new JLabel();
        counterLabel.setFont(Theme.FONT_SMALL);

        footer.add(hintLabel, BorderLayout.WEST);
        footer.add(counterLabel, BorderLayout.EAST);
        body.add(footer);

        JButton cancelButton = secondaryButton(I18n.t(I18n.Key.CANCEL));
        JButton saveButton = accentButton(I18n.t(I18n.Key.SAVE));
        saveButton.setPreferredSize(new Dimension(96, 32));

        Runnable refreshState = () -> {
            int len = editArea.getText().length();
            counterLabel.setText(len + "/" + ChatMessage.MAX_TEXT_LENGTH);
            counterLabel.setForeground(len >= ChatMessage.MAX_TEXT_LENGTH
                    ? Theme.DANGER : Theme.TEXT_MUTED);
            boolean valid = msg.hasAttachment() || !editArea.getText().trim().isEmpty();
            saveButton.setEnabled(valid);
        };
        refreshState.run();
        editArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refreshState.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refreshState.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refreshState.run();
            }
        });

        JDialog dialog = createStyledDialog(I18n.t(I18n.Key.EDIT_MESSAGE_TITLE), body,
                dialogButtons(cancelButton, saveButton), null, editArea);
        cancelButton.addActionListener(e -> dialog.dispose());

        Runnable commitEdit = () -> {
            String updated = editArea.getText().trim();
            if (!msg.hasAttachment() && updated.isEmpty()) {
                return;
            }
            dialog.dispose();
            if (!updated.equals(msg.getText())) {
                applyLocalMessageEdit(msg, updated, targetGroup, targetPeer);
            }
        };

        saveButton.addActionListener(e -> commitEdit.run());
        editArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "aero-commit-edit");
        editArea.getActionMap().put("aero-commit-edit", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                commitEdit.run();
            }
        });
        editArea.getInputMap().put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK),
                "aero-insert-newline");
        editArea.getActionMap().put("aero-insert-newline", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                editArea.replaceSelection("\n");
            }
        });

        dialog.setVisible(true);
    }

    private void confirmAndDeleteMessage(ChatMessage msg) {
        if (msg == null) {
            return;
        }
        final String convKey = activeConversationKey();
        final ChatGroup targetGroup = activeGroup;
        final Peer targetPeer = activePeer;
        if (convKey == null) {
            return;
        }

        JPanel body = new JPanel(new BorderLayout());
        body.setBackground(Theme.BG_WINDOW);
        body.setBorder(BorderFactory.createEmptyBorder(14, 16, 12, 16));

        JLabel prompt = new JLabel(I18n.t(msg.isIncoming()
                ? I18n.Key.DELETE_MESSAGE_CONFIRM_LOCAL
                : I18n.Key.DELETE_MESSAGE_CONFIRM));
        prompt.setFont(Theme.FONT_BODY);
        prompt.setForeground(Theme.TEXT_PRIMARY);
        body.add(prompt, BorderLayout.CENTER);

        JButton cancelButton = secondaryButton(I18n.t(I18n.Key.CANCEL));
        JButton deleteButton = UiIcons.darkButton(
                I18n.t(I18n.Key.DELETE_MESSAGE),
                null,
                Theme.BTN_DANGER,
                Theme.BTN_DANGER_HOVER,
                Theme.BTN_DARK_BORDER,
                Color.WHITE,
                8
        );
        deleteButton.setFont(Theme.FONT_SMALL);
        deleteButton.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        deleteButton.setPreferredSize(new Dimension(96, 32));

        JDialog dialog = createStyledDialog(I18n.t(I18n.Key.DELETE_MESSAGE_TITLE), body,
                dialogButtons(cancelButton, deleteButton), deleteButton, deleteButton);
        cancelButton.addActionListener(e -> dialog.dispose());
        deleteButton.addActionListener(e -> {
            dialog.dispose();
            applyLocalMessageDelete(msg, convKey, targetGroup, targetPeer);
        });
        dialog.setVisible(true);
    }

    private void applyLocalMessageEdit(ChatMessage msg, String newText,
                                       ChatGroup targetGroup, Peer targetPeer) {
        msg.editText(newText);
        scheduleHistorySave();
        refreshMessageBubbleInView(msg);

        Thread t = new Thread(() -> {
            if (targetGroup != null) {
                network.sendGroupMessageEdit(targetGroup, msg.getId(), newText);
            } else if (targetPeer != null) {
                Peer live = network.getPeerById(targetPeer.getId());
                network.sendMessageEdit(live != null ? live : targetPeer, msg.getId(), newText);
            }
        }, "aero-msg-edit");
        t.setDaemon(true);
        t.start();
    }

    private void applyLocalMessageDelete(ChatMessage msg, String convKey,
                                         ChatGroup targetGroup, Peer targetPeer) {
        boolean removed;
        synchronized (histories) {
            List<ChatMessage> list = histories.get(convKey);
            removed = list != null && list.remove(msg);
        }
        if (!removed) {
            return;
        }

        cleanupMessageResources(msg);
        if (!msg.isIncoming() && msg.hasAttachment()) {
            network.revokeFileOffer(msg.getAttachment().getTransferId());
        }
        scheduleHistorySave();
        removeMessageBubbleFromView(msg);

        if (!msg.isIncoming()) {
            Thread t = new Thread(() -> {
                if (targetGroup != null) {
                    network.sendGroupMessageDelete(targetGroup, msg.getId());
                } else if (targetPeer != null) {
                    Peer live = network.getPeerById(targetPeer.getId());
                    network.sendMessageDelete(live != null ? live : targetPeer, msg.getId());
                }
            }, "aero-msg-delete");
            t.setDaemon(true);
            t.start();
        }
    }

    private void cleanupMessageResources(ChatMessage msg) {
        if (msg == null || !msg.hasAttachment()) {
            return;
        }
        FileAttachment att = msg.getAttachment();
        att.setChangeListener(null);
        att.cancel();
        att.cancelSaving();
        inlineImageCache.remove(att.getTransferId());
    }

    private JPanel buildAttachmentView(ChatMessage msg, FileAttachment att, MessageRow row) {
        JPanel container = new JPanel(new BorderLayout(0, 4));
        container.setOpaque(false);

        Runnable rebuild = () -> {
            if (!SwingUtilities.isDescendingFrom(row, messagesContainer)) {
                return;
            }
            container.removeAll();
            populateAttachmentContainer(container, msg, att, row);
            container.revalidate();
            container.repaint();
            row.revalidate();
            messagesContainer.revalidate();
        };

        populateAttachmentContainer(container, msg, att, row);
        att.setChangeListener(() -> SwingUtilities.invokeLater(rebuild));
        return container;
    }

    private void populateAttachmentContainer(JPanel container, ChatMessage msg,
                                             FileAttachment att, MessageRow row) {
        boolean hasLocalFile = !att.isEncryptedPendingUnlock()
                && att.getLocalPath() != null
                && Files.isRegularFile(att.getLocalPath())
                && (!msg.isIncoming() || att.getStatus() == FileAttachment.Status.COMPLETED);
        boolean canDownload = !att.isEncryptedPendingUnlock()
                && (hasLocalFile || att.canDownloadFromPeer());

        if (att.isImage() && hasLocalFile) {
            JPanel imageRow = new JPanel(new BorderLayout(6, 0));
            imageRow.setOpaque(false);

            JLabel imgLabel = new JLabel();
            imgLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            imgLabel.setToolTipText(I18n.t(I18n.Key.OPEN_PREVIEW_TOOLTIP));
            imgLabel.setFocusable(true);

            ImageIcon cached = inlineImageCache.get(att.getTransferId());
            if (cached != null) {
                imgLabel.setIcon(cached);
            } else {
                imgLabel.setText(att.getFileName());
                imgLabel.setForeground(Theme.TEXT_PRIMARY);
                imgLabel.setFont(Theme.FONT_SMALL);
                loadInlineImageAsync(att, imgLabel, row);
            }

            imgLabel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        new ImagePreviewDialog(ChatPopupWindow.this, att,
                                ChatPopupWindow.this::downloadAttachment).setVisible(true);
                    }
                }
            });

            imgLabel.registerKeyboardAction(
                    e -> UiIcons.copyImageToClipboard(att.getLocalPath()),
                    KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK),
                    JComponent.WHEN_FOCUSED
            );

            attachMessagePopup(imgLabel, row);

            JButton dlBtn = createAttachmentActionButton(container, msg, att, row, true);
            JPanel dlWrap = new JPanel(new BorderLayout());
            dlWrap.setOpaque(false);
            dlWrap.add(dlBtn, BorderLayout.SOUTH);

            imageRow.add(imgLabel, BorderLayout.CENTER);
            imageRow.add(dlWrap, BorderLayout.EAST);

            JLabel metaLabel = new JLabel(buildAttachmentSubtitle(msg, att, true));
            metaLabel.setFont(Theme.FONT_SMALL);
            metaLabel.setForeground(att.getStatus() == FileAttachment.Status.FAILED
                    ? Theme.DANGER : new Color(225, 228, 240, 200));

            attachMessagePopup(imageRow, row);
            attachMessagePopup(metaLabel, row);

            container.add(imageRow, BorderLayout.CENTER);
            container.add(metaLabel, BorderLayout.SOUTH);
            return;
        }

        // File card (or locked encrypted file, or in-progress incoming transfer)
        JPanel card = new JPanel(new BorderLayout(8, 0));
        card.setBackground(new Color(0, 0, 0, 45));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(255, 255, 255, 32), 1),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));

        JLabel icon = new JLabel(UiIcons.fileBadge(Theme.TEXT_PRIMARY, 22));
        icon.setPreferredSize(new Dimension(24, 28));

        String titleText = att.isEncryptedPendingUnlock()
                ? "\uD83D\uDD12 " + I18n.t(I18n.Key.ENCRYPTED_FILE)
                : truncateFileName(att.getFileName(), 24);
        JLabel nameLabel = new JLabel(titleText);
        nameLabel.setFont(Theme.FONT_BOLD);
        nameLabel.setForeground(Theme.TEXT_PRIMARY);
        if (!att.isEncryptedPendingUnlock()) {
            nameLabel.setToolTipText(att.getFileName());
        }

        JLabel subLabel = new JLabel(buildAttachmentSubtitle(msg, att, false));
        subLabel.setFont(Theme.FONT_SMALL);
        Color subColor;
        if (att.getStatus() == FileAttachment.Status.FAILED) {
            subColor = Theme.DANGER;
        } else if (att.getStatus() == FileAttachment.Status.AVAILABLE && !att.isEncryptedPendingUnlock()) {
            subColor = Theme.ACCENT;
        } else {
            subColor = new Color(225, 228, 240, 200);
        }
        subLabel.setForeground(subColor);

        JPanel info = new JPanel(new BorderLayout(0, 1));
        info.setOpaque(false);
        info.add(nameLabel, BorderLayout.NORTH);
        info.add(subLabel, BorderLayout.SOUTH);

        JButton dlBtn = createAttachmentActionButton(container, msg, att, row, canDownload);
        JPanel dlWrap = new JPanel(new GridBagLayout());
        dlWrap.setOpaque(false);
        dlWrap.add(dlBtn);

        card.add(icon, BorderLayout.WEST);
        card.add(info, BorderLayout.CENTER);
        card.add(dlWrap, BorderLayout.EAST);

        attachMessagePopup(card, row);
        attachMessagePopup(info, row);
        attachMessagePopup(nameLabel, row);
        attachMessagePopup(subLabel, row);
        attachMessagePopup(icon, row);

        container.add(card, BorderLayout.CENTER);
    }

    private JButton createAttachmentActionButton(JPanel container, ChatMessage msg,
                                                 FileAttachment att, MessageRow row,
                                                 boolean downloadEnabled) {
        Runnable refresh = () -> refreshAttachmentContainer(container, msg, att, row);
        attachmentRefreshCallbacks.put(att, refresh);
        if (att.isSaving()) {
            return createCancelButton(I18n.t(I18n.Key.CANCEL_DOWNLOAD_TOOLTIP), () -> {
                att.cancelSaving();
                refresh.run();
            });
        }
        if (att.getStatus() == FileAttachment.Status.TRANSFERRING) {
            I18n.Key tipKey = msg.isIncoming()
                    ? I18n.Key.CANCEL_DOWNLOAD_TOOLTIP
                    : I18n.Key.CANCEL_UPLOAD_TOOLTIP;
            return createCancelButton(I18n.t(tipKey), () -> {
                att.cancel();
                refresh.run();
            });
        }
        return createDownloadButton(att, downloadEnabled, refresh);
    }

    private void refreshAttachmentContainer(JPanel container, ChatMessage msg,
                                            FileAttachment att, MessageRow row) {
        Runnable rebuild = () -> {
            if (!SwingUtilities.isDescendingFrom(row, messagesContainer)) {
                return;
            }
            container.removeAll();
            populateAttachmentContainer(container, msg, att, row);
            container.revalidate();
            container.repaint();
            row.revalidate();
            row.repaint();
        };
        if (SwingUtilities.isEventDispatchThread()) {
            rebuild.run();
        } else {
            SwingUtilities.invokeLater(rebuild);
        }
    }

    private JButton createDownloadButton(FileAttachment att, boolean enabled,
                                         Runnable onStateChanged) {
        JButton btn = UiIcons.darkButton(
                null,
                UiIcons.download(enabled ? Theme.TEXT_PRIMARY : Theme.TEXT_MUTED, 15),
                Theme.BTN_DARK,
                Theme.BTN_DARK_HOVER,
                Theme.BTN_DARK_BORDER,
                Theme.TEXT_PRIMARY,
                8
        );
        btn.setPreferredSize(new Dimension(30, 30));
        btn.setBorder(BorderFactory.createEmptyBorder());
        btn.setEnabled(enabled);
        btn.setCursor(Cursor.getPredefinedCursor(enabled ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        btn.setToolTipText(I18n.t(I18n.Key.DOWNLOAD_TOOLTIP));
        btn.addActionListener(e -> downloadAttachment(att, onStateChanged));
        return btn;
    }

    private JButton createCancelButton(String tooltip, Runnable action) {
        JButton btn = UiIcons.darkButton(
                null,
                UiIcons.cancel(Theme.TEXT_PRIMARY, 13),
                Theme.BTN_DANGER,
                Theme.BTN_DANGER_HOVER,
                Theme.BTN_DARK_BORDER,
                Theme.TEXT_PRIMARY,
                8
        );
        btn.setPreferredSize(new Dimension(30, 30));
        btn.setBorder(BorderFactory.createEmptyBorder());
        btn.setToolTipText(tooltip);
        btn.addActionListener(e -> action.run());
        return btn;
    }

    private String buildAttachmentSubtitle(ChatMessage msg, FileAttachment att, boolean includeName) {
        String sizeStr = FileAttachment.formatSize(att.getFileSize());
        String base = includeName ? truncateFileName(att.getFileName(), 22) + " · " + sizeStr : sizeStr;
        if (att.isSaving()) {
            return sizeStr + " · " + I18n.t(I18n.Key.SAVING_PROGRESS, att.getSaveProgressPercent());
        }
        if (att.getStatus() == FileAttachment.Status.TRANSFERRING) {
            I18n.Key key = msg.isIncoming() ? I18n.Key.TRANSFERRING_RECV : I18n.Key.TRANSFERRING_SEND;
            return sizeStr + " · " + I18n.t(key, att.getProgressPercent());
        }
        if (att.getStatus() == FileAttachment.Status.AVAILABLE) {
            return sizeStr + " · " + I18n.t(I18n.Key.READY_TO_DOWNLOAD);
        }
        if (att.getStatus() == FileAttachment.Status.CANCELLED) {
            return sizeStr + " · " + I18n.t(I18n.Key.TRANSFER_CANCELLED);
        }
        if (att.getStatus() == FileAttachment.Status.FAILED) {
            return sizeStr + " · " + I18n.t(I18n.Key.TRANSFER_FAILED);
        }
        return base;
    }

    private void loadInlineImageAsync(FileAttachment att, JLabel target, MessageRow row) {
        Path path = att.getLocalPath();
        if (path == null) {
            return;
        }
        new Thread(() -> {
            try {
                BufferedImage img = UiIcons.loadScaledImage(path, MAX_INLINE_IMG_W, MAX_INLINE_IMG_H);
                if (img != null) {
                    ImageIcon icon = new ImageIcon(img);
                    SwingUtilities.invokeLater(() -> {
                        inlineImageCache.put(att.getTransferId(), icon);
                        if (!SwingUtilities.isDescendingFrom(row, messagesContainer)) {
                            return;
                        }
                        JScrollBar bar = messagesScroll.getVerticalScrollBar();
                        boolean wasAtBottom = bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum() - 32;
                        target.setText(null);
                        target.setIcon(icon);
                        target.revalidate();
                        row.revalidate();
                        messagesContainer.revalidate();
                        messagesContainer.repaint();
                        if (wasAtBottom) {
                            scrollToBottom();
                        }
                    });
                }
            } catch (IOException ignored) {
            }
        }, "aero-img-thumb").start();
    }

    private static String truncateFileName(String name, int maxChars) {
        if (name == null || name.length() <= maxChars) {
            return name == null ? "" : name;
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0 && name.length() - dot <= 7 && maxChars > 10) {
            String ext = name.substring(dot);
            int keep = maxChars - ext.length() - 1;
            if (keep > 3) {
                return name.substring(0, keep) + "\u2026" + ext;
            }
        }
        return name.substring(0, maxChars - 1) + "\u2026";
    }

    private void downloadAttachment(FileAttachment att) {
        downloadAttachment(att, att != null ? attachmentRefreshCallbacks.get(att) : null);
    }

    private void downloadAttachment(FileAttachment att, Runnable onStateChanged) {
        if (att == null || att.isEncryptedPendingUnlock()
                || att.isSaving() || att.getStatus() == FileAttachment.Status.TRANSFERRING) {
            return;
        }
        Path src = att.getLocalPath();
        boolean hasLocal = src != null && Files.isRegularFile(src);
        if (!hasLocal && !att.canDownloadFromPeer()) {
            return;
        }
        FileDialog fd = new FileDialog(this, I18n.t(I18n.Key.SAVE_FILE_TITLE), FileDialog.SAVE);
        fd.setFile(att.getFileName());
        fd.setVisible(true);
        if (fd.getFile() == null) {
            return;
        }
        Path dest = Paths.get(fd.getDirectory(), fd.getFile());
        if (!hasLocal) {
            byte[] groupKey = null;
            if (att.isEncryptedOffer()) {
                ChatGroup group = att.getOfferGroupId() != null
                        ? settings.getGroup(att.getOfferGroupId()) : null;
                groupKey = group != null ? group.getPasswordKeyBytes() : null;
                if (groupKey == null) {
                    JOptionPane.showMessageDialog(
                            this,
                            I18n.t(I18n.Key.UNLOCK_FAILED),
                            I18n.t(I18n.Key.AERO_ERROR_TITLE),
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }
            network.downloadOfferedFileAsync(att, dest, groupKey, outcome -> SwingUtilities.invokeLater(() -> {
                if (onStateChanged != null) {
                    onStateChanged.run();
                }
                if (outcome == PeerNetwork.DownloadOutcome.UNAVAILABLE) {
                    JOptionPane.showMessageDialog(
                            this,
                            I18n.t(I18n.Key.FILE_UNAVAILABLE_ERROR),
                            I18n.t(I18n.Key.AERO_ERROR_TITLE),
                            JOptionPane.WARNING_MESSAGE);
                } else if (outcome == PeerNetwork.DownloadOutcome.FAILED) {
                    JOptionPane.showMessageDialog(
                            this,
                            I18n.t(I18n.Key.TRANSFER_FAILED),
                            I18n.t(I18n.Key.AERO_ERROR_TITLE),
                            JOptionPane.ERROR_MESSAGE);
                }
            }));
            return;
        }
        if (src.toAbsolutePath().normalize().equals(dest.toAbsolutePath().normalize())) {
            return;
        }
        if (!att.startSaving()) {
            return;
        }
        if (onStateChanged != null) {
            onStateChanged.run();
        }
        new Thread(() -> {
            boolean cancelled = false;
            try {
                try (InputStream in = Files.newInputStream(src);
                     OutputStream out = Files.newOutputStream(dest)) {
                    byte[] buffer = new byte[65536];
                    long copied = 0L;
                    int lastPercent = -1;
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        if (att.isSaveCancelled()) {
                            cancelled = true;
                            break;
                        }
                        out.write(buffer, 0, read);
                        copied += read;
                        att.updateSaveProgress(copied);
                        int pct = att.getSaveProgressPercent();
                        if (pct != lastPercent) {
                            lastPercent = pct;
                            if (onStateChanged != null) {
                                onStateChanged.run();
                            }
                        }
                    }
                    out.flush();
                }
                if (cancelled || att.isSaveCancelled()) {
                    Files.deleteIfExists(dest);
                }
            } catch (IOException ex) {
                try {
                    Files.deleteIfExists(dest);
                } catch (IOException ignored) {
                }
                if (!att.isSaveCancelled()) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                            this,
                            I18n.t(I18n.Key.FILE_SAVE_ERROR, ex.getMessage()),
                            I18n.t(I18n.Key.AERO_ERROR_TITLE),
                            JOptionPane.ERROR_MESSAGE));
                }
            } finally {
                att.finishSaving();
                if (onStateChanged != null) {
                    onStateChanged.run();
                }
            }
        }, "aero-file-save").start();
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
        attachButton.setToolTipText(I18n.t(I18n.Key.ATTACH_FILE_TOOLTIP));
        dropBanner.setText(I18n.t(I18n.Key.DROP_FILES_HINT));
        newGroupButton.setText(I18n.t(I18n.Key.GROUP_BUTTON));
        renameButton.setText(I18n.t(I18n.Key.RENAME_BUTTON));
        connectButton.setText(I18n.t(I18n.Key.IP_CONNECT));
        ipField.setToolTipText(I18n.t(I18n.Key.IP_TOOLTIP));
        messageCounter.setToolTipText(I18n.t(I18n.Key.MESSAGE_LIMIT_TOOLTIP,
                ChatMessage.MAX_TEXT_LENGTH));

        notificationsSectionLabel.setText(I18n.t(I18n.Key.NOTIFICATIONS_SECTION));
        notificationsCheck.setText(I18n.t(I18n.Key.NOTIFICATIONS_LABEL));
        alwaysOnTopCheck.setText(I18n.t(I18n.Key.ALWAYS_ON_TOP_LABEL));
        messagesSectionLabel.setText(I18n.t(I18n.Key.MESSAGES_SECTION));
        keepMessagesCheck.setText(I18n.t(I18n.Key.KEEP_MESSAGES_LABEL));
        keepMessagesHintLabel.setText(I18n.t(I18n.Key.KEEP_MESSAGES_HINT));
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
        List<ChatMessage> snapshot;
        synchronized (histories) {
            List<ChatMessage> messages = histories.get(groupKey(group.getId()));
            if (messages == null || messages.isEmpty()) return;
            snapshot = new ArrayList<>(messages);
        }
        for (ChatMessage message : snapshot) {
            if (message.hasAttachment() && message.isIncoming()) {
                FileAttachment att = message.getAttachment();
                if (att.isEncryptedPendingUnlock()) {
                    decryptGroupOfferMetadata(message, key);
                }
            }
            if (message.isEncrypted() && message.isIncoming()) {
                try {
                    message.setDecryptedText(ChatMessage.truncate(GroupCrypto.decrypt(
                            key, message.getIv(), message.getCipherText())));
                } catch (GeneralSecurityException | IllegalArgumentException ignored) {
                }
            }
        }
    }

    private void decryptGroupOfferMetadata(ChatMessage message, byte[] key) {
        FileAttachment att = message.getAttachment();
        if (att == null || !att.isEncryptedPendingUnlock()) {
            return;
        }
        try {
            String metaPlain = GroupCrypto.decrypt(key, att.getMetaIv(), att.getMetaCipherText());
            String[] metaParts = metaPlain.split("\\|", -1);
            if (metaParts.length < 3) {
                return;
            }
            boolean isImage = "1".equals(metaParts[0]);
            String fileName = new String(Base64.getDecoder().decode(metaParts[1]), StandardCharsets.UTF_8);
            String caption = ChatMessage.truncate(
                    new String(Base64.getDecoder().decode(metaParts[2]), StandardCharsets.UTF_8));

            att.setImage(isImage);
            att.setFileName(fileName);
            att.setEncryptedPendingUnlock(false);
            message.setDecryptedText(caption);
            att.notifyListener();
        } catch (Exception ignored) {
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
        removeHistory(groupKey(group.getId()));
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
        removeHistory(groupKey(groupId));
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
    private static void installLengthLimit(JTextComponent field, int maxLength) {
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
        JButton button = UiIcons.darkButton(
                text,
                null,
                Theme.BTN_ACCENT,
                Theme.BTN_ACCENT_HOVER,
                Theme.BTN_DARK_BORDER,
                Color.WHITE,
                8
        );
        button.setFont(Theme.FONT_SMALL);
        button.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
        return button;
    }

    private JButton secondaryButton(String text) {
        JButton button = UiIcons.darkButton(
                text,
                null,
                Theme.BTN_DARK,
                Theme.BTN_DARK_HOVER,
                Theme.BTN_DARK_BORDER,
                Theme.TEXT_PRIMARY,
                8
        );
        button.setFont(Theme.FONT_SMALL);
        button.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
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
        int w = Math.max(MIN_WIDTH, getWidth());
        int h = Math.max(MIN_HEIGHT, getHeight());
        int x = bounds.x + Math.max(0, (bounds.width - w) / 2);
        int y = bounds.y + Math.max(0, (bounds.height - h) / 2);
        setLocation(x, y);
    }

    /**
     * Recenters the window only when its current position is completely
     * outside all connected screens (for example after a monitor was removed).
     */
    private void ensureOnScreen() {
        Dimension max = WindowResizer.usableScreenSize(this, MIN_WIDTH, MIN_HEIGHT);
        int w = Math.max(MIN_WIDTH, Math.min(max.width, getWidth()));
        int h = Math.max(MIN_HEIGHT, Math.min(max.height, getHeight()));
        if (w != getWidth() || h != getHeight()) {
            setSize(w, h);
            WindowResizer.updateRoundShape(this, 16);
        }
        Rectangle windowBounds = getBounds();
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getScreenDevices()) {
            if (device.getDefaultConfiguration().getBounds().intersects(windowBounds)) {
                return;
            }
        }
        centerOnScreen();
    }

    private static final class ScrollableListPanel extends JPanel implements Scrollable {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(16, visibleRect.height - 20);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private final class MessageRow extends JPanel {
        final ChatMessage msg;
        final JPanel bubble;
        final JPanel body;
        final JLabel metaLabel;
        final JButton optionsBtn;
        JTextArea textArea;
        Component textSpacer;

        MessageRow(ChatMessage msg, boolean showSender, int maxTextWidth) {
            super(new FlowLayout(msg.isIncoming() ? FlowLayout.LEFT : FlowLayout.RIGHT, 0, 0));
            this.msg = msg;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));

            this.bubble = new JPanel(new BorderLayout(0, 4));
            this.bubble.setBackground(msg.isIncoming() ? Theme.BUBBLE_IN : Theme.BUBBLE_OUT);
            this.bubble.setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 10));

            if (showSender && msg.isIncoming()) {
                JLabel sender = new JLabel(msg.getSenderName());
                sender.setFont(Theme.FONT_BOLD);
                sender.setForeground(Theme.ACCENT_HOVER);
                attachMessagePopup(sender, this);
                this.bubble.add(sender, BorderLayout.NORTH);
            }

            this.body = new JPanel();
            this.body.setLayout(new BoxLayout(this.body, BoxLayout.Y_AXIS));
            this.body.setOpaque(false);

            FileAttachment att = msg.getAttachment();
            if (att != null) {
                JPanel attPanel = buildAttachmentView(msg, att, this);
                attPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
                this.body.add(attPanel);
            }

            if (shouldShowText()) {
                if (att != null) {
                    this.textSpacer = Box.createVerticalStrut(5);
                    this.body.add(this.textSpacer);
                }
                this.textArea = createSelectableBubbleText(msg.getText(), maxTextWidth, this);
                this.textArea.setAlignmentX(Component.LEFT_ALIGNMENT);
                this.body.add(this.textArea);
            }

            this.metaLabel = new JLabel();
            this.metaLabel.setFont(Theme.FONT_SMALL);
            this.metaLabel.setHorizontalAlignment(SwingConstants.RIGHT);
            updateMetaLabel();

            this.optionsBtn = new JButton(UiIcons.moreDots(new Color(220, 224, 235, 165), 14));
            this.optionsBtn.setRolloverIcon(UiIcons.moreDots(Color.WHITE, 14));
            this.optionsBtn.setContentAreaFilled(false);
            this.optionsBtn.setBorderPainted(false);
            this.optionsBtn.setFocusPainted(false);
            this.optionsBtn.setRolloverEnabled(true);
            this.optionsBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            this.optionsBtn.setMargin(new Insets(0, 2, 0, 0));
            this.optionsBtn.setPreferredSize(new Dimension(18, 16));
            this.optionsBtn.setToolTipText(I18n.t(I18n.Key.MESSAGE_OPTIONS_TOOLTIP));
            this.optionsBtn.addActionListener(e -> {
                JPopupMenu menu = buildMessagePopupMenu(this);
                menu.show(this.optionsBtn, 0, this.optionsBtn.getHeight());
            });

            JPanel footer = new JPanel(new BorderLayout(6, 0));
            footer.setOpaque(false);
            footer.add(this.metaLabel, BorderLayout.CENTER);
            footer.add(this.optionsBtn, BorderLayout.EAST);

            this.bubble.add(this.body, BorderLayout.CENTER);
            this.bubble.add(footer, BorderLayout.SOUTH);

            attachMessagePopup(this.bubble, this);
            attachMessagePopup(this.body, this);
            attachMessagePopup(this.metaLabel, this);
            attachMessagePopup(footer, this);
            attachMessagePopup(this, this);

            add(this.bubble);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        private boolean shouldShowText() {
            FileAttachment att = msg.getAttachment();
            String msgText = msg.getText();
            return att == null || (!att.isEncryptedPendingUnlock() && msgText != null && !msgText.isEmpty());
        }

        private void updateMetaLabel() {
            StringBuilder sb = new StringBuilder(msg.getFormattedTime());
            if (msg.isEdited()) {
                sb.append(" · ").append(I18n.t(I18n.Key.EDITED_LABEL));
            }
            if (msg.isNotDelivered()) {
                sb.append(" · ").append(I18n.t(I18n.Key.NOT_DELIVERED_LABEL));
            }
            metaLabel.setText(sb.toString());
            metaLabel.setForeground(msg.isNotDelivered()
                    ? new Color(255, 196, 196)
                    : new Color(220, 224, 235, 170));
        }

        void updateWrapWidth(int maxTextWidth) {
            if (textArea != null) {
                applyBubbleTextWidth(textArea, maxTextWidth);
                textArea.revalidate();
            }
            revalidate();
            repaint();
        }

        void updateTextAndMeta(int maxTextWidth) {
            updateMetaLabel();
            boolean show = shouldShowText();
            if (show) {
                if (textArea == null) {
                    if (msg.hasAttachment() && textSpacer == null) {
                        textSpacer = Box.createVerticalStrut(5);
                        body.add(textSpacer);
                    }
                    textArea = createSelectableBubbleText(msg.getText(), maxTextWidth, this);
                    textArea.setAlignmentX(Component.LEFT_ALIGNMENT);
                    body.add(textArea);
                } else {
                    textArea.setText(msg.getText());
                    applyBubbleTextWidth(textArea, maxTextWidth);
                }
            } else {
                if (textArea != null) {
                    body.remove(textArea);
                    textArea = null;
                }
                if (textSpacer != null) {
                    body.remove(textSpacer);
                    textSpacer = null;
                }
            }
            body.revalidate();
            bubble.revalidate();
            revalidate();
            repaint();
        }
    }

    private static final class PendingAttachment {
        final Path file;
        final String displayName;
        final long size;
        final boolean image;
        final ImageIcon thumbnail;

        PendingAttachment(Path file, String displayName, long size, boolean image, ImageIcon thumbnail) {
            this.file = file;
            this.displayName = displayName;
            this.size = size;
            this.image = image;
            this.thumbnail = thumbnail;
        }
    }
}
