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
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
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
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.DocumentFilter;
import javax.swing.text.JTextComponent;
import javax.swing.text.View;
import java.awt.BasicStroke;
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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
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
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Lightweight, themeable floating LAN chat window.
 * Supports bottom-right/edge resizing, automatic <5MB screenshot downloads,
 * message editing/deleting, and live customizable color themes.
 */
public class ChatPopupWindow extends JDialog {
    private static final int DEFAULT_WIDTH = 360;
    private static final int DEFAULT_HEIGHT = 490;
    private static final int MIN_WIDTH = 350;
    private static final int MIN_HEIGHT = 380;
    private static final int MAX_INLINE_IMG_W = 196;
    private static final int MAX_INLINE_IMG_H = 260;
    private static final long AUTO_DOWNLOAD_IMAGE_MAX_BYTES = 5L * 1024L * 1024L;
    private static final DateTimeFormatter SCREENSHOT_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String CARD_PEERS = "PEERS";
    private static final String CARD_CHAT = "CHAT";
    private static final String CARD_SETTINGS = "SETTINGS";

    private final PeerNetwork network;
    private final AppSettings settings;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel contentCards = new JPanel(cardLayout);
    private final JPanel root;

    // Header
    private JPanel headerPanel;
    private final JButton backButton;
    private final JLabel headerTitle = new JLabel("Aero");
    private final JLabel headerSubtitle = new JLabel(I18n.t(I18n.Key.APP_SUBTITLE));
    private final UiComponents.PillLabel shortcutLabel = new UiComponents.PillLabel(
            "Ctrl+Alt+Space", 10, () -> Theme.BG_CARD, () -> Theme.TEXT_MUTED, () -> Theme.BORDER);
    private final JButton membersButton;
    private final JButton settingsButton;

    // Peer list view
    private JPanel peersViewPanel;
    private UiComponents.RoundedPanel selfBarCard;
    private UiComponents.AvatarBadge selfAvatar;
    private final JPanel peerListContainer = new ScrollableListPanel();
    private JScrollPane peerListScroll;
    private final JLabel myNameLabel = new JLabel();
    private final JLabel myStatusLabel = new JLabel();
    private JButton newGroupButton;
    private JButton renameButton;

    // Chat view
    private final JPanel messagesContainer = new ScrollableListPanel();
    private final JScrollPane messagesScroll;
    private final JTextArea inputField = createChatInputArea();
    private JScrollPane inputScroll;
    private UiComponents.RoundedPanel inputBox;
    private int lastInputHeight = 30;
    private final JLabel messageCounter = new JLabel();
    private final JButton attachButton = UiIcons.themedButton(
            null,
            UiIcons.paperclip(() -> Theme.TEXT_PRIMARY, 16),
            () -> Theme.BTN_DARK,
            () -> Theme.BTN_DARK_HOVER,
            () -> Theme.BTN_DARK_BORDER,
            () -> Theme.TEXT_PRIMARY,
            9
    );
    private JButton sendButton;
    private Icon sendIconNormal;
    private Icon saveEditIcon;
    private JPanel editBanner;
    private JPanel editAccentBar;
    private JLabel editTitleLabel;
    private JLabel editPreviewLabel;
    private JButton cancelEditButton;
    private ChatMessage editingMessage;
    private ChatGroup editingTargetGroup;
    private Peer editingTargetPeer;
    private String draftBeforeEdit;
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
    private JPanel settingsPanel;
    private JScrollPane settingsScroll;
    private final JTextField displayNameField = new JTextField();
    private final JCheckBox autoDownloadImagesCheck = new UiComponents.ToggleSwitch();
    private final JCheckBox keepMessagesCheck = new UiComponents.ToggleSwitch();
    private final JCheckBox notificationsCheck = new UiComponents.ToggleSwitch();
    private final JCheckBox alwaysOnTopCheck = new UiComponents.ToggleSwitch();
    private final JRadioButton germanRadio = new UiComponents.ThemedRadioButton("Deutsch");
    private final JRadioButton englishRadio = new UiComponents.ThemedRadioButton("English");
    private final JLabel settingsStatus = new JLabel(" ");
    private JLabel themeSectionLabel;
    private JLabel presetsHintLabel;
    private JLabel messagesSectionLabel;
    private JLabel autoDownloadImagesHintLabel;
    private JLabel keepMessagesHintLabel;
    private JLabel windowSectionLabel;
    private JLabel nameSectionLabel;
    private JLabel languageSectionLabel;
    private JLabel storageLabel;
    private JButton saveNameButton;
    private JButton resetColorsButton;
    private final List<ColorSwatchControl> colorSwatches = new ArrayList<>();
    private final List<PresetChipButton> presetButtons = new ArrayList<>();
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
    private Runnable themeChangeListener = () -> {};

    public ChatPopupWindow(PeerNetwork network, AppSettings settings) {
        this.network = network;
        this.settings = settings;
        this.historyStore = new ChatHistoryStore(settings.getHistoryFile());
        this.historySaveTimer = new Timer(1200, e -> saveHistoryNow());
        this.historySaveTimer.setRepeats(false);

        this.backButton = UiIcons.iconButton(
                UiIcons.backArrow(() -> Theme.TEXT_PRIMARY, 16),
                UiIcons.backArrow(() -> Theme.ACCENT_HOVER, 16),
                28, 8);
        this.membersButton = UiIcons.iconButton(
                UiIcons.members(() -> Theme.TEXT_MUTED, 16),
                UiIcons.members(() -> Theme.TEXT_PRIMARY, 16),
                28, 8);
        this.settingsButton = UiIcons.iconButton(
                UiIcons.settings(() -> Theme.TEXT_MUTED, 15),
                UiIcons.settings(() -> Theme.TEXT_PRIMARY, 15),
                28, 8);

        setUndecorated(true);
        setAlwaysOnTop(settings.isAlwaysOnTop());
        Dimension screenMax = WindowResizer.usableScreenSize(null, MIN_WIDTH, MIN_HEIGHT);
        int savedW = settings.getWindowWidth();
        int savedH = settings.getWindowHeight();
        int initW = Math.max(MIN_WIDTH, Math.min(screenMax.width, savedW > 0 ? savedW : DEFAULT_WIDTH));
        int initH = Math.max(MIN_HEIGHT, Math.min(screenMax.height, savedH > 0 ? savedH : DEFAULT_HEIGHT));
        setSize(initW, initH);
        setDefaultCloseOperation(HIDE_ON_CLOSE);

        root = new JPanel(new BorderLayout());
        root.add(buildHeader(), BorderLayout.NORTH);

        contentCards.setOpaque(false);
        contentCards.add(buildPeersView(), CARD_PEERS);

        chatViewPanel = new JPanel(new BorderLayout());
        chatViewPanel.setOpaque(false);

        messagesContainer.setLayout(new BoxLayout(messagesContainer, BoxLayout.Y_AXIS));
        messagesContainer.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        messagesScroll = new JScrollPane(messagesContainer);
        UiComponents.styleScrollPane(messagesScroll, () -> Theme.BG_WINDOW);
        messagesScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        messagesScroll.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                relayoutMessageBubblesIfNeeded();
            }
        });

        dropBanner.setFont(Theme.FONT_BOLD);
        dropBanner.setOpaque(true);
        dropBanner.setVisible(false);

        attachmentStrip.setLayout(new BoxLayout(attachmentStrip, BoxLayout.Y_AXIS));
        attachmentStrip.setVisible(false);

        editBanner = buildEditBanner();
        chatInputBar = buildInputBar();

        JPanel southTop = new JPanel();
        southTop.setLayout(new BoxLayout(southTop, BoxLayout.Y_AXIS));
        southTop.setOpaque(false);
        southTop.add(editBanner);
        southTop.add(attachmentStrip);

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.setOpaque(false);
        southPanel.add(southTop, BorderLayout.NORTH);
        southPanel.add(chatInputBar, BorderLayout.SOUTH);

        chatViewPanel.add(dropBanner, BorderLayout.NORTH);
        chatViewPanel.add(messagesScroll, BorderLayout.CENTER);
        chatViewPanel.add(southPanel, BorderLayout.SOUTH);
        installChatClipboardAndDrop(chatViewPanel, southPanel);

        contentCards.add(chatViewPanel, CARD_CHAT);

        settingsPanel = buildSettingsView();
        settingsScroll = new JScrollPane(settingsPanel);
        UiComponents.styleScrollPane(settingsScroll, () -> Theme.BG_WINDOW);
        settingsScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        contentCards.add(settingsScroll, CARD_SETTINGS);

        root.add(contentCards, BorderLayout.CENTER);

        setContentPane(root);
        WindowResizer.install(this, 16, MIN_WIDTH, MIN_HEIGHT, 44,
                () -> settings.setWindowSize(getWidth(), getHeight()));
        centerOnScreen();
        applyThemeColors(false);
        applyLanguageTexts();
        loadStoredHistory();

        root.registerKeyboardAction(
                e -> {
                    if (editingMessage != null) {
                        cancelEditingMessage(true);
                    } else {
                        setVisible(false);
                    }
                },
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

    public void setThemeChangeListener(Runnable listener) {
        this.themeChangeListener = listener == null ? () -> {} : listener;
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
            ensureOnScreen();
            setVisible(true);
        }

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
        cancelEditingMessage(false);
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
        cancelEditingMessage(false);
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
        cancelEditingMessage(false);
        this.activePeer = null;
        this.activeGroup = null;
        this.currentCard = CARD_PEERS;
        backButton.setVisible(false);
        membersButton.setVisible(false);
        settingsButton.setVisible(true);
        headerTitle.setText("Aero");
        headerSubtitle.setText(I18n.t(I18n.Key.APP_SUBTITLE));
        headerSubtitle.setForeground(Theme.TEXT_MUTED);
        renderPeerList();
        cardLayout.show(contentCards, CARD_PEERS);
        if (makeVisible) {
            showWindow();
        }
    }

    public void showSettings() {
        cancelEditingMessage(false);
        this.activePeer = null;
        this.activeGroup = null;
        this.currentCard = CARD_SETTINGS;
        backButton.setVisible(true);
        membersButton.setVisible(false);
        settingsButton.setVisible(false);
        headerTitle.setText(I18n.t(I18n.Key.SETTINGS_TITLE));
        headerSubtitle.setText(I18n.t(I18n.Key.SETTINGS_SUBTITLE));
        headerSubtitle.setForeground(Theme.TEXT_MUTED);
        displayNameField.setText(network.getMyName());
        autoDownloadImagesCheck.setSelected(settings.isAutoDownloadImages());
        keepMessagesCheck.setSelected(settings.isKeepMessages());
        notificationsCheck.setSelected(settings.isNotificationsEnabled());
        alwaysOnTopCheck.setSelected(settings.isAlwaysOnTop());
        refreshThemeControlsState();
        settingsStatus.setText(" ");
        cardLayout.show(contentCards, CARD_SETTINGS);
        SwingUtilities.invokeLater(() -> {
            if (settingsScroll != null) {
                settingsScroll.getVerticalScrollBar().setValue(0);
            }
        });
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
        autoDownloadImageIfEligible(message, null);

        if (isViewingPeerConversation(sender.getId())) {
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

        if (isViewingGroupConversation(local.getId())) {
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
        autoDownloadImageIfEligible(message, local);

        if (isViewingGroupConversation(local.getId())) {
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

    private void autoDownloadImageIfEligible(ChatMessage msg, ChatGroup group) {
        if (msg == null || !msg.isIncoming() || !settings.isAutoDownloadImages()) {
            return;
        }
        FileAttachment att = msg.getAttachment();
        if (att == null || !att.isImage() || att.isEncryptedPendingUnlock()) {
            return;
        }
        if (att.getFileSize() < 0 || att.getFileSize() >= AUTO_DOWNLOAD_IMAGE_MAX_BYTES) {
            return;
        }
        if (att.getStatus() != FileAttachment.Status.AVAILABLE || !att.canDownloadFromPeer()) {
            return;
        }
        if (att.getLocalPath() != null && Files.isRegularFile(att.getLocalPath())) {
            return;
        }
        byte[] groupKey = null;
        if (att.isEncryptedOffer()) {
            ChatGroup resolved = group != null ? group
                    : (att.getOfferGroupId() != null ? settings.getGroup(att.getOfferGroupId()) : null);
            groupKey = resolved != null ? resolved.getPasswordKeyBytes() : null;
            if (groupKey == null) {
                return;
            }
        }
        Path dest = resolveAutoDownloadPath(att);
        if (dest == null) {
            return;
        }
        network.downloadOfferedFileAsync(att, dest, groupKey, outcome -> SwingUtilities.invokeLater(() -> {
            if (outcome == PeerNetwork.DownloadOutcome.COMPLETED) {
                scheduleHistorySave();
                Runnable refresh = attachmentRefreshCallbacks.get(att);
                if (refresh != null) {
                    refresh.run();
                }
            }
        }));
    }

    private Path resolveAutoDownloadPath(FileAttachment att) {
        Path dir = settings.isKeepMessages() ? settings.getMediaDir() : network.getTransferDir();
        if (dir == null) {
            return null;
        }
        String safeName = FileAttachment.sanitizeFileName(att.getFileName());
        return dir.resolve(att.getTransferId() + "-" + safeName);
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

        for (ChatGroup group : settings.getGroups()) {
            if (group.isPasswordProtected() && group.hasPasswordKey()) {
                byte[] key = group.getPasswordKeyBytes();
                if (key != null) {
                    decryptStoredMessages(group, key, false);
                }
            }
        }
    }

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
        headerPanel = new JPanel(new BorderLayout(8, 0));
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(8, 12, 8, 10)
        ));

        JPanel left = new JPanel(new BorderLayout(6, 0));
        left.setOpaque(false);

        backButton.setVisible(false);
        backButton.addActionListener(e -> showPeerSelector());

        headerTitle.setFont(Theme.FONT_TITLE);
        headerSubtitle.setFont(Theme.FONT_SMALL);

        JPanel titles = new JPanel(new BorderLayout(0, 1));
        titles.setOpaque(false);
        titles.add(headerTitle, BorderLayout.NORTH);
        titles.add(headerSubtitle, BorderLayout.SOUTH);

        left.add(backButton, BorderLayout.WEST);
        left.add(titles, BorderLayout.CENTER);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        right.setOpaque(false);

        membersButton.setVisible(false);
        membersButton.addActionListener(e -> showGroupMembers(activeGroup));

        settingsButton.addActionListener(e -> showSettings());

        JButton closeBtn = UiIcons.iconButton(
                UiIcons.cancel(() -> Theme.TEXT_MUTED, 13),
                UiIcons.cancel(() -> Theme.TEXT_PRIMARY, 13),
                28, 8);
        closeBtn.setToolTipText(I18n.t(I18n.Key.CLOSE_TOOLTIP));
        closeBtn.addActionListener(e -> setVisible(false));

        right.add(shortcutLabel);
        right.add(membersButton);
        right.add(settingsButton);
        right.add(closeBtn);

        headerPanel.add(left, BorderLayout.CENTER);
        headerPanel.add(right, BorderLayout.EAST);

        installDrag(headerPanel);
        installDrag(left);
        installDrag(titles);
        installDrag(headerTitle);
        installDrag(headerSubtitle);

        return headerPanel;
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
        peersViewPanel = new JPanel(new BorderLayout());
        peersViewPanel.setOpaque(false);

        JPanel selfBarWrap = new JPanel(new BorderLayout());
        selfBarWrap.setOpaque(false);
        selfBarWrap.setBorder(BorderFactory.createEmptyBorder(10, 12, 6, 12));

        selfBarCard = new UiComponents.RoundedPanel(
                new BorderLayout(10, 0), 12, () -> Theme.BG_CARD, () -> Theme.BORDER);
        selfBarCard.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        selfAvatar = new UiComponents.AvatarBadge(network.getMyName(), 32, true);

        JPanel selfInfo = new JPanel(new BorderLayout(0, 1));
        selfInfo.setOpaque(false);

        myNameLabel.setFont(Theme.FONT_BOLD);
        updateMyNameLabel();

        myStatusLabel.setFont(Theme.FONT_SMALL);
        myStatusLabel.setText(I18n.t(I18n.Key.ONLINE));

        selfInfo.add(myNameLabel, BorderLayout.NORTH);
        selfInfo.add(myStatusLabel, BorderLayout.SOUTH);

        JPanel leftSelf = new JPanel(new BorderLayout(10, 0));
        leftSelf.setOpaque(false);
        leftSelf.add(selfAvatar, BorderLayout.WEST);
        leftSelf.add(selfInfo, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        actions.setOpaque(false);

        newGroupButton = UiIcons.themedButton(
                I18n.t(I18n.Key.GROUP_BUTTON),
                UiIcons.plus(() -> Theme.BTN_ACCENT_TEXT, 11),
                () -> Theme.BTN_ACCENT,
                () -> Theme.BTN_ACCENT_HOVER,
                null,
                () -> Theme.BTN_ACCENT_TEXT,
                8);
        newGroupButton.setBorder(BorderFactory.createEmptyBorder(5, 9, 5, 10));
        newGroupButton.setIconTextGap(4);
        newGroupButton.addActionListener(e -> createGroupDialog());

        renameButton = UiIcons.themedButton(
                null,
                UiIcons.edit(() -> Theme.TEXT_PRIMARY, 13),
                () -> Theme.BTN_DARK,
                () -> Theme.BTN_DARK_HOVER,
                () -> Theme.BTN_DARK_BORDER,
                () -> Theme.TEXT_PRIMARY,
                8);
        renameButton.setPreferredSize(new Dimension(28, 26));
        renameButton.setBorder(BorderFactory.createEmptyBorder());
        renameButton.setToolTipText(I18n.t(I18n.Key.RENAME_BUTTON));
        renameButton.addActionListener(e -> promptRename());

        actions.add(newGroupButton);
        actions.add(renameButton);

        selfBarCard.add(leftSelf, BorderLayout.CENTER);
        selfBarCard.add(actions, BorderLayout.EAST);
        selfBarWrap.add(selfBarCard, BorderLayout.CENTER);

        peerListContainer.setLayout(new BoxLayout(peerListContainer, BoxLayout.Y_AXIS));
        peerListContainer.setBorder(BorderFactory.createEmptyBorder(2, 12, 14, 12));

        peerListScroll = new JScrollPane(peerListContainer);
        UiComponents.styleScrollPane(peerListScroll, () -> Theme.BG_WINDOW);
        peerListScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        peersViewPanel.add(selfBarWrap, BorderLayout.NORTH);
        peersViewPanel.add(peerListScroll, BorderLayout.CENTER);
        renderPeerList();
        return peersViewPanel;
    }

    private JPanel buildSettingsView() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 14, 18, 14));

        // 1. Design & Colors Card
        themeSectionLabel = sectionLabel("");
        panel.add(themeSectionLabel);

        UiComponents.RoundedPanel themeCard = settingsCard();
        presetsHintLabel = new JLabel();
        presetsHintLabel.setFont(Theme.FONT_SMALL);
        presetsHintLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        themeCard.add(presetsHintLabel);
        themeCard.add(Box.createVerticalStrut(6));

        JPanel presetsRow = new JPanel(new GridLayout(2, 3, 6, 6));
        presetsRow.setOpaque(false);
        presetsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        presetsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
        presetButtons.clear();
        for (Theme.Preset preset : Theme.PRESETS) {
            PresetChipButton btn = new PresetChipButton(preset);
            presetButtons.add(btn);
            presetsRow.add(btn);
        }
        themeCard.add(presetsRow);
        themeCard.add(Box.createVerticalStrut(10));

        colorSwatches.clear();
        JPanel swatchesGrid = new JPanel(new GridLayout(2, 2, 8, 6));
        swatchesGrid.setOpaque(false);
        swatchesGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        swatchesGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 66));

        ColorSwatchControl bgSwatch = new ColorSwatchControl(
                I18n.Key.COLOR_BG,
                () -> Theme.BG_WINDOW,
                c -> applyCustomPalette(c, Theme.ACCENT, Theme.BUBBLE_OUT, Theme.BUBBLE_IN));
        ColorSwatchControl accentSwatch = new ColorSwatchControl(
                I18n.Key.COLOR_ACCENT,
                () -> Theme.ACCENT,
                c -> applyCustomPalette(Theme.BG_WINDOW, c, Theme.BUBBLE_OUT, Theme.BUBBLE_IN));
        ColorSwatchControl outSwatch = new ColorSwatchControl(
                I18n.Key.COLOR_BUBBLE_OUT,
                () -> Theme.BUBBLE_OUT,
                c -> applyCustomPalette(Theme.BG_WINDOW, Theme.ACCENT, c, Theme.BUBBLE_IN));
        ColorSwatchControl inSwatch = new ColorSwatchControl(
                I18n.Key.COLOR_BUBBLE_IN,
                () -> Theme.BUBBLE_IN,
                c -> applyCustomPalette(Theme.BG_WINDOW, Theme.ACCENT, Theme.BUBBLE_OUT, c));

        colorSwatches.add(bgSwatch);
        colorSwatches.add(accentSwatch);
        colorSwatches.add(outSwatch);
        colorSwatches.add(inSwatch);
        swatchesGrid.add(bgSwatch);
        swatchesGrid.add(accentSwatch);
        swatchesGrid.add(outSwatch);
        swatchesGrid.add(inSwatch);
        themeCard.add(swatchesGrid);
        themeCard.add(Box.createVerticalStrut(8));

        resetColorsButton = secondaryButton("");
        resetColorsButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        resetColorsButton.addActionListener(e -> applyCustomPalette(
                Theme.DEFAULT_BG_WINDOW, Theme.DEFAULT_ACCENT,
                Theme.DEFAULT_BUBBLE_OUT, Theme.DEFAULT_BUBBLE_IN));
        themeCard.add(resetColorsButton);
        panel.add(themeCard);
        panel.add(Box.createVerticalStrut(12));

        // 2. Messages & Media Card
        messagesSectionLabel = sectionLabel("");
        panel.add(messagesSectionLabel);

        UiComponents.RoundedPanel msgCard = settingsCard();
        autoDownloadImagesCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        autoDownloadImagesCheck.setSelected(settings.isAutoDownloadImages());
        autoDownloadImagesCheck.addActionListener(e ->
                settings.setAutoDownloadImages(autoDownloadImagesCheck.isSelected()));
        msgCard.add(autoDownloadImagesCheck);

        autoDownloadImagesHintLabel = new JLabel();
        autoDownloadImagesHintLabel.setFont(Theme.FONT_SMALL);
        autoDownloadImagesHintLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        autoDownloadImagesHintLabel.setBorder(BorderFactory.createEmptyBorder(0, 44, 6, 2));
        msgCard.add(autoDownloadImagesHintLabel);

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
                deleteDirectoryQuietly(settings.getMediaDir());
            }
        });
        msgCard.add(keepMessagesCheck);

        keepMessagesHintLabel = new JLabel();
        keepMessagesHintLabel.setFont(Theme.FONT_SMALL);
        keepMessagesHintLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        keepMessagesHintLabel.setBorder(BorderFactory.createEmptyBorder(0, 44, 0, 2));
        msgCard.add(keepMessagesHintLabel);
        panel.add(msgCard);
        panel.add(Box.createVerticalStrut(12));

        // 3. Window & Notifications Card
        windowSectionLabel = sectionLabel("");
        panel.add(windowSectionLabel);

        UiComponents.RoundedPanel winCard = settingsCard();
        notificationsCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        notificationsCheck.setSelected(settings.isNotificationsEnabled());
        notificationsCheck.addActionListener(e ->
                settings.setNotificationsEnabled(notificationsCheck.isSelected()));
        winCard.add(notificationsCheck);

        alwaysOnTopCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        alwaysOnTopCheck.setSelected(settings.isAlwaysOnTop());
        alwaysOnTopCheck.addActionListener(e -> {
            settings.setAlwaysOnTop(alwaysOnTopCheck.isSelected());
            setAlwaysOnTop(settings.isAlwaysOnTop());
        });
        winCard.add(alwaysOnTopCheck);
        panel.add(winCard);
        panel.add(Box.createVerticalStrut(12));

        // 4. Language Card
        languageSectionLabel = sectionLabel("");
        panel.add(languageSectionLabel);

        UiComponents.RoundedPanel langCard = settingsCard();
        germanRadio.setText(I18n.Language.GERMAN.getDisplayName());
        englishRadio.setText(I18n.Language.ENGLISH.getDisplayName());
        ButtonGroup languageGroup = new ButtonGroup();
        languageGroup.add(germanRadio);
        languageGroup.add(englishRadio);
        germanRadio.setSelected(I18n.getLanguage() == I18n.Language.GERMAN);
        englishRadio.setSelected(I18n.getLanguage() == I18n.Language.ENGLISH);
        germanRadio.addActionListener(e -> changeLanguage(I18n.Language.GERMAN));
        englishRadio.addActionListener(e -> changeLanguage(I18n.Language.ENGLISH));

        JPanel languageRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        languageRow.setOpaque(false);
        languageRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        languageRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        languageRow.add(germanRadio);
        languageRow.add(englishRadio);
        langCard.add(languageRow);
        panel.add(langCard);
        panel.add(Box.createVerticalStrut(12));

        // 5. Display Name Card
        nameSectionLabel = sectionLabel("");
        panel.add(nameSectionLabel);

        UiComponents.RoundedPanel nameCard = settingsCard();
        styleTextField(displayNameField);
        displayNameField.setText(network.getMyName());
        displayNameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        displayNameField.addActionListener(e -> saveDisplayNameFromSettings());

        saveNameButton = accentButton("");
        saveNameButton.setPreferredSize(new Dimension(88, 30));
        saveNameButton.addActionListener(e -> saveDisplayNameFromSettings());

        JPanel nameRow = new JPanel(new BorderLayout(8, 0));
        nameRow.setOpaque(false);
        nameRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        nameRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        nameRow.add(displayNameField, BorderLayout.CENTER);
        nameRow.add(saveNameButton, BorderLayout.EAST);
        nameCard.add(nameRow);

        nameCard.add(Box.createVerticalStrut(4));
        settingsStatus.setFont(Theme.FONT_SMALL);
        settingsStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
        nameCard.add(settingsStatus);
        panel.add(nameCard);

        panel.add(Box.createVerticalStrut(10));
        storageLabel = new JLabel();
        storageLabel.setFont(Theme.FONT_SMALL);
        storageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        storageLabel.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
        panel.add(storageLabel);

        return panel;
    }

    private static UiComponents.RoundedPanel settingsCard() {
        UiComponents.RoundedPanel card = new UiComponents.RoundedPanel(
                null, 12, () -> Theme.BG_CARD, () -> Theme.BORDER);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        return card;
    }

    private void applyCustomPalette(Color bgWindow, Color accent, Color bubbleOut, Color bubbleIn) {
        Theme.applyPalette(bgWindow, accent, bubbleOut, bubbleIn);
        settings.setThemeColors(
                Theme.toHex(Theme.BG_WINDOW),
                Theme.toHex(Theme.ACCENT),
                Theme.toHex(Theme.BUBBLE_OUT),
                Theme.toHex(Theme.BUBBLE_IN));
        applyThemeColors(true);
        themeChangeListener.run();
    }

    private void applyThemeColors(boolean reRenderActiveLists) {
        root.setBackground(Theme.BG_WINDOW);
        root.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1));

        if (headerPanel != null) {
            headerPanel.setBackground(Theme.BG_HEADER);
            headerPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                    BorderFactory.createEmptyBorder(8, 12, 8, 10)
            ));
        }
        headerTitle.setForeground(Theme.TEXT_PRIMARY);
        updateChatSubtitle();

        peerListContainer.setBackground(Theme.BG_WINDOW);
        if (peerListScroll != null) {
            peerListScroll.getViewport().setBackground(Theme.BG_WINDOW);
        }
        myNameLabel.setForeground(Theme.TEXT_PRIMARY);
        myStatusLabel.setForeground(Theme.ONLINE_DOT);

        messagesContainer.setBackground(Theme.BG_WINDOW);
        if (messagesScroll != null) {
            messagesScroll.getViewport().setBackground(Theme.BG_WINDOW);
        }

        dropBanner.setForeground(Theme.ACCENT);
        dropBanner.setBackground(Theme.BG_CARD);
        dropBanner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.ACCENT),
                BorderFactory.createEmptyBorder(7, 10, 7, 10)
        ));

        if (editBanner != null) {
            editBanner.setBackground(Theme.BG_HEADER);
            editBanner.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                    BorderFactory.createEmptyBorder(6, 12, 6, 12)
            ));
            editAccentBar.setBackground(Theme.ACCENT);
            editTitleLabel.setForeground(Theme.isLight(Theme.BG_WINDOW) ? Theme.ACCENT : Theme.ACCENT_HOVER);
            editPreviewLabel.setForeground(Theme.TEXT_MUTED);
        }

        attachmentStrip.setBackground(Theme.BG_HEADER);
        attachmentStrip.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(6, 10, 4, 10)
        ));

        if (chatInputBar != null) {
            chatInputBar.setBackground(Theme.BG_HEADER);
            chatInputBar.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                    BorderFactory.createEmptyBorder(8, 10, 12, 18)
            ));
        }
        inputField.setBackground(Theme.BG_INPUT);
        inputField.setForeground(Theme.TEXT_PRIMARY);
        inputField.setCaretColor(Theme.TEXT_PRIMARY);
        inputField.setSelectionColor(Theme.withAlpha(Theme.ACCENT, 120));
        inputField.setSelectedTextColor(Theme.TEXT_PRIMARY);
        if (inputScroll != null) {
            inputScroll.getViewport().setBackground(Theme.BG_INPUT);
        }

        if (settingsPanel != null) {
            settingsPanel.setBackground(Theme.BG_WINDOW);
            if (settingsScroll != null) {
                settingsScroll.getViewport().setBackground(Theme.BG_WINDOW);
            }
            themeSectionLabel.setForeground(Theme.TEXT_MUTED);
            presetsHintLabel.setForeground(Theme.TEXT_MUTED);
            messagesSectionLabel.setForeground(Theme.TEXT_MUTED);
            autoDownloadImagesHintLabel.setForeground(Theme.TEXT_MUTED);
            keepMessagesHintLabel.setForeground(Theme.TEXT_MUTED);
            windowSectionLabel.setForeground(Theme.TEXT_MUTED);
            languageSectionLabel.setForeground(Theme.TEXT_MUTED);
            nameSectionLabel.setForeground(Theme.TEXT_MUTED);
            storageLabel.setForeground(Theme.TEXT_MUTED);
            settingsStatus.setForeground(Theme.ONLINE_DOT);
            styleTextField(displayNameField);
        }

        refreshThemeControlsState();
        updateMessageCounter();
        refreshEditingHighlights();

        if (reRenderActiveLists) {
            inlineImageCache.clear();
            renderAttachmentStrip();
            renderPeerList();
            if (CARD_CHAT.equals(currentCard)) {
                renderMessages();
            }
        }
        repaint();
    }

    private void refreshThemeControlsState() {
        for (PresetChipButton btn : presetButtons) {
            btn.refresh();
        }
        for (ColorSwatchControl sw : colorSwatches) {
            sw.refresh();
        }
    }

    private JTextArea createChatInputArea() {
        JTextArea area = new JTextArea() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getText().isEmpty()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g2.setColor(Theme.TEXT_MUTED);
                    g2.setFont(getFont());
                    Insets ins = getInsets();
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(I18n.t(I18n.Key.CHAT_INPUT_PLACEHOLDER), ins.left, ins.top + fm.getAscent());
                    g2.dispose();
                }
            }
        };
        area.setRows(1);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(Theme.FONT_BODY);
        area.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));
        return area;
    }

    private JPanel buildEditBanner() {
        JPanel banner = new JPanel(new BorderLayout(8, 0)) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        banner.setAlignmentX(Component.LEFT_ALIGNMENT);
        banner.setOpaque(true);
        banner.setBackground(Theme.BG_HEADER);
        banner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));

        editAccentBar = new JPanel();
        editAccentBar.setBackground(Theme.ACCENT);
        editAccentBar.setPreferredSize(new Dimension(3, 28));

        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setOpaque(false);

        editTitleLabel = new JLabel(I18n.t(I18n.Key.EDIT_MESSAGE_TITLE));
        editTitleLabel.setIcon(UiIcons.edit(
                () -> Theme.isLight(Theme.BG_WINDOW) ? Theme.ACCENT : Theme.ACCENT_HOVER, 11));
        editTitleLabel.setIconTextGap(5);
        editTitleLabel.setFont(Theme.FONT_SMALL.deriveFont(Font.BOLD));
        editTitleLabel.setForeground(Theme.isLight(Theme.BG_WINDOW) ? Theme.ACCENT : Theme.ACCENT_HOVER);

        editPreviewLabel = new JLabel(" ");
        editPreviewLabel.setFont(Theme.FONT_SMALL);
        editPreviewLabel.setForeground(Theme.TEXT_MUTED);

        info.add(editTitleLabel);
        info.add(editPreviewLabel);

        cancelEditButton = UiIcons.iconButton(
                UiIcons.cancel(() -> Theme.TEXT_MUTED, 12),
                UiIcons.cancel(() -> Theme.TEXT_PRIMARY, 12),
                22, 6);
        cancelEditButton.setToolTipText(I18n.t(I18n.Key.CANCEL));
        cancelEditButton.addActionListener(e -> cancelEditingMessage(true));

        JPanel left = new JPanel(new BorderLayout(8, 0));
        left.setOpaque(false);
        left.add(editAccentBar, BorderLayout.WEST);
        left.add(info, BorderLayout.CENTER);

        JPanel right = new JPanel(new GridBagLayout());
        right.setOpaque(false);
        right.add(cancelEditButton);

        banner.add(left, BorderLayout.CENTER);
        banner.add(right, BorderLayout.EAST);
        banner.setVisible(false);
        return banner;
    }

    private JPanel buildInputBar() {
        JPanel bar = new JPanel(new BorderLayout(6, 0));
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(8, 10, 12, 18)
        ));

        attachButton.setBorder(BorderFactory.createEmptyBorder());
        attachButton.setPreferredSize(new Dimension(32, 32));
        attachButton.setMinimumSize(new Dimension(32, 32));
        attachButton.setToolTipText(I18n.t(I18n.Key.ATTACH_FILE_TOOLTIP));
        attachButton.addActionListener(e -> promptSelectFiles());

        installLengthLimit(inputField, ChatMessage.MAX_TEXT_LENGTH);
        installInputPasteHandler();
        inputField.setToolTipText(I18n.t(I18n.Key.CHAT_INPUT_TOOLTIP));
        inputField.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "aero-send-msg");
        inputField.getActionMap().put("aero-send-msg", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendCurrentMessage();
            }
        });
        inputField.getInputMap().put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK),
                "aero-input-newline");
        inputField.getActionMap().put("aero-input-newline", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                inputField.replaceSelection("\n");
            }
        });
        inputField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                onInputChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onInputChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                onInputChanged();
            }
        });

        inputScroll = new JScrollPane(inputField);
        UiComponents.styleScrollPane(inputScroll, () -> Theme.BG_INPUT);
        inputScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        inputScroll.setPreferredSize(new Dimension(120, 28));

        messageCounter.setFont(Theme.FONT_SMALL);
        messageCounter.setHorizontalAlignment(SwingConstants.RIGHT);
        messageCounter.setToolTipText(I18n.t(I18n.Key.MESSAGE_LIMIT_TOOLTIP,
                ChatMessage.MAX_TEXT_LENGTH));

        inputBox = new UiComponents.RoundedPanel(
                new BorderLayout(4, 0), 10, () -> Theme.BG_INPUT, () -> Theme.BORDER);
        inputBox.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 6));
        inputBox.add(inputScroll, BorderLayout.CENTER);
        inputBox.add(messageCounter, BorderLayout.EAST);

        sendIconNormal = UiIcons.send(() -> Theme.BTN_ACCENT_TEXT, 15);
        saveEditIcon = UiIcons.check(() -> Theme.BTN_ACCENT_TEXT, 15);
        sendButton = UiIcons.themedButton(
                null,
                sendIconNormal,
                () -> Theme.BTN_ACCENT,
                () -> Theme.BTN_ACCENT_HOVER,
                null,
                () -> Theme.BTN_ACCENT_TEXT,
                9);
        sendButton.setPreferredSize(new Dimension(34, 32));
        sendButton.setBorder(BorderFactory.createEmptyBorder());
        sendButton.addActionListener(e -> sendCurrentMessage());

        JPanel leftWrap = new JPanel(new BorderLayout());
        leftWrap.setOpaque(false);
        leftWrap.add(attachButton, BorderLayout.SOUTH);

        JPanel rightWrap = new JPanel(new BorderLayout());
        rightWrap.setOpaque(false);
        rightWrap.add(sendButton, BorderLayout.SOUTH);

        bar.add(leftWrap, BorderLayout.WEST);
        bar.add(inputBox, BorderLayout.CENTER);
        bar.add(rightWrap, BorderLayout.EAST);

        updateMessageCounter();
        return bar;
    }

    private void onInputChanged() {
        updateMessageCounter();
        updateInputAreaHeight();
    }

    private void updateInputAreaHeight() {
        if (inputScroll == null || chatInputBar == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            int width = inputScroll.getViewport().getWidth();
            if (width > 10) {
                inputField.setSize(new Dimension(width, 1000));
            }
            int prefH = inputField.getPreferredSize().height;
            int targetH = Math.max(28, Math.min(82, prefH + 2));
            if (targetH != lastInputHeight) {
                lastInputHeight = targetH;
                inputScroll.setPreferredSize(new Dimension(120, targetH));
                chatInputBar.revalidate();
                chatViewPanel.revalidate();
            }
        });
    }

    private void updateMessageCounter() {
        int length = inputField.getText().length();
        if (length == 0) {
            messageCounter.setText("");
            messageCounter.setVisible(false);
        } else {
            messageCounter.setVisible(true);
            messageCounter.setText(length + "/" + ChatMessage.MAX_TEXT_LENGTH);
            messageCounter.setForeground(length >= ChatMessage.MAX_TEXT_LENGTH
                    ? Theme.DANGER : Theme.TEXT_MUTED);
        }
    }

    private void sendCurrentMessage() {
        if (editingMessage != null) {
            commitEditingMessage();
            return;
        }
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
        installDropSupport(inputBox);
        installDropSupport(inputScroll);
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
                Path dir = settings.isKeepMessages()
                        ? settings.getMediaDir()
                        : network.getTransferDir();
                Files.createDirectories(dir);
                Path out = dir.resolve(UUID.randomUUID().toString().substring(0, 8) + "-" + name);
                ImageIO.write(image, "png", out.toFile());
                long size = Files.size(out);
                ImageIcon thumb = createThumbIcon(out);
                PendingAttachment pa = new PendingAttachment(out, name, size, true, thumb, true);
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
                staged.add(new PendingAttachment(path, name, size, isImage, thumb, false));
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

            UiComponents.RoundedPanel item = new UiComponents.RoundedPanel(
                    new BorderLayout(8, 0), 10, () -> Theme.BG_CARD, () -> Theme.BORDER);
            item.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
            item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));

            JLabel iconLabel = new JLabel(pa.thumbnail != null
                    ? pa.thumbnail
                    : UiIcons.fileBadge(() -> Theme.TEXT_PRIMARY, 20));
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

            JButton removeBtn = UiIcons.themedButton(
                    null,
                    UiIcons.cancel(() -> Theme.TEXT_MUTED, 11),
                    () -> Theme.BTN_DARK,
                    () -> Theme.BTN_DANGER_HOVER,
                    () -> Theme.BTN_DARK_BORDER,
                    () -> Theme.TEXT_PRIMARY,
                    6
            );
            removeBtn.setPreferredSize(new Dimension(22, 22));
            removeBtn.setBorder(BorderFactory.createEmptyBorder());
            removeBtn.setToolTipText(I18n.t(I18n.Key.REMOVE_ATTACHMENT_TOOLTIP));
            removeBtn.addActionListener(e -> {
                if (index < pendingAttachments.size()) {
                    PendingAttachment removed = pendingAttachments.remove(index);
                    if (removed.ownedTempFile && isManagedCacheFile(removed.file)) {
                        try {
                            Files.deleteIfExists(removed.file);
                        } catch (IOException ignored) {
                        }
                    }
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
            JPanel emptyWrap = new JPanel(new GridBagLayout());
            emptyWrap.setOpaque(false);

            JPanel centerBox = new JPanel();
            centerBox.setLayout(new BoxLayout(centerBox, BoxLayout.Y_AXIS));
            centerBox.setOpaque(false);
            centerBox.setBorder(BorderFactory.createEmptyBorder(36, 16, 20, 16));

            JLabel icon = new JLabel(UiIcons.members(() -> Theme.TEXT_MUTED, 30));
            icon.setAlignmentX(Component.CENTER_ALIGNMENT);

            JLabel title = new JLabel(I18n.t(I18n.Key.EMPTY_PEERS_TITLE));
            title.setFont(Theme.FONT_TITLE);
            title.setForeground(Theme.TEXT_PRIMARY);
            title.setAlignmentX(Component.CENTER_ALIGNMENT);

            JLabel hint = new JLabel(I18n.t(I18n.Key.EMPTY_PEERS_HINT));
            hint.setFont(Theme.FONT_SMALL);
            hint.setForeground(Theme.TEXT_MUTED);
            hint.setAlignmentX(Component.CENTER_ALIGNMENT);

            centerBox.add(icon);
            centerBox.add(Box.createVerticalStrut(8));
            centerBox.add(title);
            centerBox.add(Box.createVerticalStrut(3));
            centerBox.add(hint);
            emptyWrap.add(centerBox);
            peerListContainer.add(emptyWrap);
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
                    peerListContainer.add(Box.createVerticalStrut(6));
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
        boolean[] hovered = {false};
        UiComponents.RoundedPanel card = new UiComponents.RoundedPanel(
                new BorderLayout(10, 0), 12,
                () -> hovered[0] ? Theme.BG_CARD_HOVER : Theme.BG_CARD,
                () -> Theme.BORDER);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        card.setBorder(BorderFactory.createEmptyBorder(9, 12, 9, 12));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        UiComponents.AvatarBadge avatar = new UiComponents.AvatarBadge(peer.getName(), 32, true);

        JLabel name = new JLabel(peer.getName());
        name.setForeground(Theme.TEXT_PRIMARY);
        name.setFont(Theme.FONT_BOLD);

        JLabel sub = new JLabel(I18n.t(I18n.Key.PEER_READY_SUBTITLE));
        sub.setForeground(Theme.TEXT_MUTED);
        sub.setFont(Theme.FONT_SMALL);

        JPanel texts = new JPanel(new BorderLayout(0, 1));
        texts.setOpaque(false);
        texts.add(name, BorderLayout.NORTH);
        texts.add(sub, BorderLayout.SOUTH);

        JPanel left = new JPanel(new BorderLayout(10, 0));
        left.setOpaque(false);
        left.add(avatar, BorderLayout.WEST);
        left.add(texts, BorderLayout.CENTER);

        card.add(left, BorderLayout.CENTER);

        int unread = unreadCounts.getOrDefault(peerKey(peer.getId()), 0);
        if (unread > 0) {
            JPanel badgeWrap = new JPanel(new GridBagLayout());
            badgeWrap.setOpaque(false);
            badgeWrap.add(createUnreadBadge(unread));
            card.add(badgeWrap, BorderLayout.EAST);
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
                hovered[0] = true;
                card.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hovered[0] = false;
                card.repaint();
            }
        });

        return card;
    }

    private JPanel createGroupCard(ChatGroup group) {
        boolean[] hovered = {false};
        UiComponents.RoundedPanel card = new UiComponents.RoundedPanel(
                new BorderLayout(10, 0), 12,
                () -> hovered[0] ? Theme.BG_CARD_HOVER : Theme.BG_CARD,
                () -> Theme.BORDER);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        card.setBorder(BorderFactory.createEmptyBorder(9, 12, 9, 10));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        UiComponents.RoundedPanel iconBadge = new UiComponents.RoundedPanel(
                new GridBagLayout(), 10,
                () -> Theme.withAlpha(Theme.ACCENT, Theme.isLight(Theme.BG_WINDOW) ? 34 : 58),
                () -> Theme.withAlpha(Theme.ACCENT, 110));
        iconBadge.setPreferredSize(new Dimension(32, 32));
        iconBadge.add(new JLabel(UiIcons.group(() -> Theme.ACCENT_HOVER, 16)));

        JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        nameRow.setOpaque(false);
        JLabel name = new JLabel(group.getName());
        name.setForeground(Theme.TEXT_PRIMARY);
        name.setFont(Theme.FONT_BOLD);
        nameRow.add(name);
        if (group.isPasswordProtected()) {
            nameRow.add(new JLabel(UiIcons.lock(() -> Theme.TEXT_MUTED, 11)));
        }

        JLabel info = new JLabel(memberStatusShort(group));
        info.setForeground(Theme.TEXT_MUTED);
        info.setFont(Theme.FONT_SMALL);

        JPanel texts = new JPanel(new BorderLayout(0, 1));
        texts.setOpaque(false);
        texts.add(nameRow, BorderLayout.NORTH);
        texts.add(info, BorderLayout.SOUTH);

        JPanel left = new JPanel(new BorderLayout(10, 0));
        left.setOpaque(false);
        left.add(iconBadge, BorderLayout.WEST);
        left.add(texts, BorderLayout.CENTER);

        card.add(left, BorderLayout.CENTER);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        right.setOpaque(false);

        int unread = unreadCounts.getOrDefault(groupKey(group.getId()), 0);
        if (unread > 0) {
            right.add(createUnreadBadge(unread));
        }

        JButton groupOptsBtn = UiIcons.iconButton(
                UiIcons.moreDots(() -> Theme.TEXT_MUTED, 14),
                UiIcons.moreDots(() -> Theme.TEXT_PRIMARY, 14),
                24, 7);
        groupOptsBtn.setToolTipText(I18n.t(I18n.Key.GROUP_OPTIONS_TOOLTIP));
        groupOptsBtn.addActionListener(e -> buildGroupPopupMenu(group)
                .show(groupOptsBtn, 0, groupOptsBtn.getHeight()));
        right.add(groupOptsBtn);

        card.add(right, BorderLayout.EAST);

        MouseAdapter groupMouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    buildGroupPopupMenu(group).show(e.getComponent(), e.getX(), e.getY());
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    buildGroupPopupMenu(group).show(e.getComponent(), e.getX(), e.getY());
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    openGroupChat(group);
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                hovered[0] = true;
                card.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hovered[0] = false;
                card.repaint();
            }
        };
        card.addMouseListener(groupMouse);
        left.addMouseListener(groupMouse);
        texts.addMouseListener(groupMouse);
        nameRow.addMouseListener(groupMouse);
        name.addMouseListener(groupMouse);
        info.addMouseListener(groupMouse);

        return card;
    }

    private JPopupMenu buildGroupPopupMenu(ChatGroup group) {
        JPopupMenu popup = UiComponents.createPopupMenu();
        JMenuItem membersItem = UiComponents.createMenuItem(
                I18n.t(I18n.Key.VIEW_MEMBERS),
                UiIcons.members(() -> Theme.TEXT_PRIMARY, 13),
                null);
        membersItem.addActionListener(e -> showGroupMembers(group));
        popup.add(membersItem);
        UiComponents.addPopupSeparator(popup);
        JMenuItem deleteItem = UiComponents.createMenuItem(
                I18n.t(I18n.Key.DELETE_GROUP_MENU),
                UiIcons.trash(() -> Theme.DANGER, 13),
                Theme.DANGER);
        deleteItem.addActionListener(e -> deleteGroup(group));
        popup.add(deleteItem);
        return popup;
    }

    private JLabel createUnreadBadge(int unread) {
        String text = unread > 99 ? "99+" : String.valueOf(unread);
        UiComponents.PillLabel badge = new UiComponents.PillLabel(
                text, 12, () -> Theme.ACCENT, () -> Theme.BTN_ACCENT_TEXT, null);
        badge.setFont(Theme.FONT_SMALL.deriveFont(Font.BOLD));
        badge.setBorder(BorderFactory.createEmptyBorder(2, 7, 2, 7));
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
            ChatMessage prev = null;
            for (ChatMessage message : messages) {
                boolean sameBurst = isSameSenderBurst(prev, message);
                appendMessageBubble(message, showSender && !sameBurst, sameBurst);
                prev = message;
            }
        }

        messagesContainer.invalidate();
        messagesScroll.validate();
        messagesContainer.repaint();
        scrollToBottom();
    }

    private static boolean isSameSenderBurst(ChatMessage prev, ChatMessage next) {
        if (prev == null || next == null || prev.isIncoming() != next.isIncoming()) {
            return false;
        }
        if (!prev.isIncoming()) {
            return true;
        }
        String prevId = prev.getSenderId();
        String nextId = next.getSenderId();
        if (prevId != null && !prevId.isEmpty() && nextId != null && !nextId.isEmpty()) {
            return prevId.equals(nextId);
        }
        return Objects.equals(prev.getSenderName(), next.getSenderName());
    }

    private JComponent createEmptyMessagesPlaceholder() {
        JPanel wrap = new JPanel(new GridBagLayout());
        wrap.setOpaque(false);

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
        empty.setBorder(BorderFactory.createEmptyBorder(28, 12, 12, 12));
        installDropSupport(empty);
        installDropSupport(wrap);
        wrap.add(empty);
        return wrap;
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
        // Reserve 52px for the side action buttons (edit + menu) so rows never wrap or clip.
        int maxBubbleW = Math.min(availableRowW - 52, (int) Math.round(availableRowW * 0.78));
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
        ChatMessage prev = null;
        int count = messagesContainer.getComponentCount();
        if (count > 0 && messagesContainer.getComponent(count - 1) instanceof MessageRow prevRow) {
            prev = prevRow.msg;
        }
        boolean sameBurst = isSameSenderBurst(prev, msg);
        appendMessageBubble(msg, showSender && !sameBurst, sameBurst);
    }

    private void appendMessageBubble(ChatMessage msg, boolean showSenderHeader, boolean compactTop) {
        MessageRow row = new MessageRow(msg, showSenderHeader, compactTop, currentMaxBubbleTextWidth());
        messagesContainer.add(row);
        messagesContainer.revalidate();
    }

    private void refreshMessageBubbleInView(ChatMessage msg) {
        int maxTextW = currentMaxBubbleTextWidth();
        for (Component comp : messagesContainer.getComponents()) {
            if (comp instanceof MessageRow row && row.msg == msg) {
                row.updateTextAndMeta(maxTextW);
                messagesContainer.invalidate();
                messagesScroll.validate();
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
                refreshBurstHeaders();
                messagesContainer.invalidate();
                messagesScroll.validate();
                messagesContainer.repaint();
                return;
            }
        }
    }

    private void refreshBurstHeaders() {
        boolean showSender = activeGroup != null;
        ChatMessage prev = null;
        for (Component comp : messagesContainer.getComponents()) {
            if (comp instanceof MessageRow row) {
                boolean sameBurst = isSameSenderBurst(prev, row.msg);
                row.updateBurstGrouping(showSender && !sameBurst, sameBurst);
                prev = row.msg;
            }
        }
    }

    private JTextArea createSelectableBubbleText(String content, boolean incoming, int maxTextWidth, MessageRow row) {
        JTextArea text = new JTextArea(content);
        text.setEditable(false);
        text.setWrapStyleWord(true);
        text.setLineWrap(true);
        text.setOpaque(false);
        text.setForeground(incoming ? Theme.BUBBLE_IN_TEXT : Theme.BUBBLE_OUT_TEXT);
        text.setSelectionColor(new Color(255, 255, 255, 85));
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

        JPopupMenu menu = UiComponents.createPopupMenu();

        if (hasSelected || hasText) {
            JMenuItem copyItem = UiComponents.createMenuItem(
                    I18n.t(I18n.Key.COPY_TEXT),
                    UiIcons.copy(() -> Theme.TEXT_PRIMARY, 13),
                    null);
            copyItem.addActionListener(e -> copyTextToClipboard(hasSelected ? selectedText : fullText));
            menu.add(copyItem);
        }

        if (att != null && !pendingUnlock) {
            boolean hasLocalFile = att.getLocalPath() != null
                    && Files.isRegularFile(att.getLocalPath())
                    && (!msg.isIncoming() || att.getStatus() == FileAttachment.Status.COMPLETED);
            boolean canDownload = hasLocalFile || att.canDownloadFromPeer();
            if (att.isImage() && hasLocalFile) {
                JMenuItem copyImg = UiComponents.createMenuItem(
                        I18n.t(I18n.Key.COPY_IMAGE),
                        UiIcons.copy(() -> Theme.TEXT_PRIMARY, 13),
                        null);
                copyImg.addActionListener(e -> UiIcons.copyImageToClipboard(att.getLocalPath()));
                menu.add(copyImg);
            }
            if (canDownload) {
                JMenuItem saveItem = UiComponents.createMenuItem(
                        I18n.t(I18n.Key.DOWNLOAD_TOOLTIP),
                        UiIcons.download(() -> Theme.TEXT_PRIMARY, 13),
                        null);
                saveItem.addActionListener(e -> downloadAttachment(att));
                menu.add(saveItem);
            }
        }

        if (!msg.isIncoming() && !pendingUnlock) {
            JMenuItem editItem = UiComponents.createMenuItem(
                    I18n.t(I18n.Key.EDIT_MESSAGE),
                    UiIcons.edit(() -> Theme.TEXT_PRIMARY, 13),
                    null);
            editItem.addActionListener(e -> promptEditMessage(msg));
            menu.add(editItem);
        }

        if (menu.getComponentCount() > 0) {
            UiComponents.addPopupSeparator(menu);
        }

        JMenuItem deleteItem = UiComponents.createMenuItem(
                I18n.t(I18n.Key.DELETE_MESSAGE),
                UiIcons.trash(() -> Theme.DANGER, 13),
                Theme.DANGER);
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
        beginEditingMessage(msg);
    }

    private void beginEditingMessage(ChatMessage msg) {
        if (msg == null || msg.isIncoming()) {
            return;
        }
        FileAttachment att = msg.getAttachment();
        if (att != null && att.isEncryptedPendingUnlock()) {
            return;
        }
        if (activeConversationKey() == null) {
            return;
        }

        if (this.editingMessage == null) {
            this.draftBeforeEdit = inputField.getText();
        }
        this.editingMessage = msg;
        this.editingTargetGroup = activeGroup;
        this.editingTargetPeer = activePeer;

        String raw = msg.getText() != null ? msg.getText().replaceAll("\\s+", " ").trim() : "";
        if (raw.isEmpty() && att != null) {
            raw = att.getFileName();
        }
        editTitleLabel.setText(I18n.t(I18n.Key.EDIT_MESSAGE_TITLE));
        editPreviewLabel.setText(truncateFileName(raw, 48));
        editBanner.setVisible(true);

        attachButton.setEnabled(false);
        if (sendButton != null && saveEditIcon != null) {
            sendButton.setIcon(saveEditIcon);
            sendButton.setToolTipText(I18n.t(I18n.Key.EDIT_MESSAGE_HINT));
        }

        String text = msg.getText() != null ? msg.getText() : "";
        inputField.setText(text);
        inputField.setCaretPosition(inputField.getDocument().getLength());
        updateInputAreaHeight();
        refreshEditingHighlights();
        southPanelRevalidate();

        SwingUtilities.invokeLater(() -> {
            inputField.requestFocusInWindow();
            for (Component comp : messagesContainer.getComponents()) {
                if (comp instanceof MessageRow row && row.msg == msg) {
                    messagesContainer.scrollRectToVisible(row.getBounds());
                    break;
                }
            }
        });
    }

    private void cancelEditingMessage(boolean restoreDraft) {
        if (this.editingMessage == null) {
            return;
        }
        this.editingMessage = null;
        this.editingTargetGroup = null;
        this.editingTargetPeer = null;

        if (editBanner != null) {
            editBanner.setVisible(false);
        }
        attachButton.setEnabled(true);
        if (sendButton != null && sendIconNormal != null) {
            sendButton.setIcon(sendIconNormal);
            sendButton.setToolTipText(I18n.t(I18n.Key.CHAT_INPUT_TOOLTIP));
        }

        if (restoreDraft) {
            inputField.setText(draftBeforeEdit != null ? draftBeforeEdit : "");
            inputField.setCaretPosition(inputField.getDocument().getLength());
        } else {
            inputField.setText("");
        }
        draftBeforeEdit = null;
        updateInputAreaHeight();
        refreshEditingHighlights();
        southPanelRevalidate();
    }

    private void commitEditingMessage() {
        ChatMessage target = this.editingMessage;
        if (target == null) {
            return;
        }
        String updated = inputField.getText().trim();
        if (updated.length() > ChatMessage.MAX_TEXT_LENGTH) {
            JOptionPane.showMessageDialog(this,
                    I18n.t(I18n.Key.MESSAGE_TOO_LONG,
                            updated.length(), ChatMessage.MAX_TEXT_LENGTH),
                    I18n.t(I18n.Key.MESSAGE_TOO_LONG_TITLE),
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!target.hasAttachment() && updated.isEmpty()) {
            inputField.requestFocusInWindow();
            return;
        }

        ChatGroup targetGroup = this.editingTargetGroup;
        Peer targetPeer = this.editingTargetPeer;
        cancelEditingMessage(false);
        if (!updated.equals(target.getText())) {
            applyLocalMessageEdit(target, updated, targetGroup, targetPeer);
        }
        inputField.requestFocusInWindow();
    }

    private void southPanelRevalidate() {
        if (chatViewPanel != null) {
            chatViewPanel.revalidate();
            chatViewPanel.repaint();
        }
    }

    private void refreshEditingHighlights() {
        for (Component comp : messagesContainer.getComponents()) {
            if (comp instanceof MessageRow row) {
                row.bubble.repaint();
            }
        }
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

        showDangerConfirmDialog(
                I18n.t(I18n.Key.DELETE_MESSAGE_TITLE),
                I18n.t(msg.isIncoming()
                        ? I18n.Key.DELETE_MESSAGE_CONFIRM_LOCAL
                        : I18n.Key.DELETE_MESSAGE_CONFIRM),
                () -> applyLocalMessageDelete(msg, convKey, targetGroup, targetPeer));
    }

    private void showDangerConfirmDialog(String title, String messageText, Runnable onConfirm) {
        JPanel body = new JPanel(new BorderLayout());
        body.setBackground(Theme.BG_WINDOW);
        body.setBorder(BorderFactory.createEmptyBorder(14, 16, 12, 16));

        JTextArea prompt = new JTextArea(messageText);
        prompt.setEditable(false);
        prompt.setFocusable(false);
        prompt.setOpaque(false);
        prompt.setLineWrap(true);
        prompt.setWrapStyleWord(true);
        prompt.setFont(Theme.FONT_BODY);
        prompt.setForeground(Theme.TEXT_PRIMARY);
        prompt.setSize(new Dimension(300, Short.MAX_VALUE));
        prompt.setPreferredSize(new Dimension(300, prompt.getPreferredSize().height));
        body.add(prompt, BorderLayout.CENTER);

        JButton cancelButton = secondaryButton(I18n.t(I18n.Key.CANCEL));
        JButton deleteButton = UiIcons.themedButton(
                I18n.t(I18n.Key.DELETE_MESSAGE),
                null,
                () -> Theme.BTN_DANGER,
                () -> Theme.BTN_DANGER_HOVER,
                () -> Theme.BTN_DARK_BORDER,
                () -> Color.WHITE,
                8
        );
        deleteButton.setFont(Theme.FONT_SMALL);
        deleteButton.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        deleteButton.setPreferredSize(new Dimension(96, 32));

        JDialog dialog = createStyledDialog(title, body,
                dialogButtons(cancelButton, deleteButton), deleteButton, deleteButton);
        cancelButton.addActionListener(e -> dialog.dispose());
        deleteButton.addActionListener(e -> {
            dialog.dispose();
            onConfirm.run();
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

        if (msg == editingMessage) {
            cancelEditingMessage(false);
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
        Path localPath = att.getLocalPath();
        if (localPath != null && isManagedCacheFile(localPath)) {
            try {
                Files.deleteIfExists(localPath);
            } catch (IOException ignored) {
            }
        }
    }

    private boolean isManagedCacheFile(Path path) {
        if (path == null) {
            return false;
        }
        Path normalized = path.toAbsolutePath().normalize();
        Path mediaDir = settings.getMediaDir();
        if (mediaDir != null && normalized.startsWith(mediaDir.toAbsolutePath().normalize())) {
            return true;
        }
        Path transferDir = network.getTransferDir();
        return transferDir != null && normalized.startsWith(transferDir.toAbsolutePath().normalize());
    }

    private static void deleteDirectoryQuietly(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private JPanel buildAttachmentView(ChatMessage msg, FileAttachment att, MessageRow row) {
        JPanel container = new JPanel(new BorderLayout(0, 4));
        container.setOpaque(false);

        // Register listener BEFORE reading initial attachment state so rapid background
        // completion cannot fire notifyListener() while changeListener is still null.
        att.setChangeListener(() -> SwingUtilities.invokeLater(
                () -> rebuildAttachmentContainerIfNeeded(container, msg, att, row)));
        populateAttachmentContainer(container, msg, att, row);
        return container;
    }

    private void populateAttachmentContainer(JPanel container, ChatMessage msg,
                                             FileAttachment att, MessageRow row) {
        Color textCol = msg.isIncoming() ? Theme.BUBBLE_IN_TEXT : Theme.BUBBLE_OUT_TEXT;
        Color metaCol = msg.isIncoming() ? Theme.BUBBLE_IN_META : Theme.BUBBLE_OUT_META;

        boolean hasLocalFile = !att.isEncryptedPendingUnlock()
                && att.getLocalPath() != null
                && Files.isRegularFile(att.getLocalPath())
                && (!msg.isIncoming() || att.getStatus() == FileAttachment.Status.COMPLETED);
        boolean canDownload = !att.isEncryptedPendingUnlock()
                && (hasLocalFile || att.canDownloadFromPeer());

        if (att.isImage() && hasLocalFile) {
            container.putClientProperty("aero.renderedInline", att.getLocalPath());
            container.putClientProperty("aero.renderedSaving", Boolean.valueOf(att.isSaving()));
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
                imgLabel.setForeground(textCol);
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
            metaLabel.setForeground(att.getStatus() == FileAttachment.Status.FAILED ? Theme.DANGER : metaCol);

            attachMessagePopup(imageRow, row);
            attachMessagePopup(metaLabel, row);

            container.add(imageRow, BorderLayout.CENTER);
            container.add(metaLabel, BorderLayout.SOUTH);
            return;
        }

        container.putClientProperty("aero.renderedInline", null);
        container.putClientProperty("aero.renderedSaving", Boolean.FALSE);

        UiComponents.RoundedPanel card = new UiComponents.RoundedPanel(
                new BorderLayout(8, 0), 10,
                () -> new Color(0, 0, 0, msg.isIncoming() && Theme.isLight(Theme.BUBBLE_IN) ? 16 : 45),
                () -> new Color(textCol.getRed(), textCol.getGreen(), textCol.getBlue(), 40));
        card.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        JLabel icon = new JLabel(UiIcons.fileBadge(() -> textCol, 22));
        icon.setPreferredSize(new Dimension(24, 28));

        String titleText = att.isEncryptedPendingUnlock()
                ? I18n.t(I18n.Key.ENCRYPTED_FILE)
                : truncateFileName(att.getFileName(), 24);
        JLabel nameLabel = new JLabel(titleText);
        if (att.isEncryptedPendingUnlock()) {
            nameLabel.setIcon(UiIcons.lock(() -> textCol, 12));
            nameLabel.setIconTextGap(5);
        }
        nameLabel.setFont(Theme.FONT_BOLD);
        nameLabel.setForeground(textCol);
        if (!att.isEncryptedPendingUnlock()) {
            nameLabel.setToolTipText(att.getFileName());
        }

        JLabel subLabel = new JLabel(buildAttachmentSubtitle(msg, att, false));
        subLabel.setFont(Theme.FONT_SMALL);
        Color subColor;
        if (att.getStatus() == FileAttachment.Status.FAILED) {
            subColor = Theme.DANGER;
        } else if (att.getStatus() == FileAttachment.Status.AVAILABLE && !att.isEncryptedPendingUnlock()) {
            subColor = msg.isIncoming() ? Theme.ACCENT_HOVER : textCol;
        } else {
            subColor = metaCol;
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
        if (SwingUtilities.isEventDispatchThread()) {
            rebuildAttachmentContainerIfNeeded(container, msg, att, row);
        } else {
            SwingUtilities.invokeLater(() -> rebuildAttachmentContainerIfNeeded(container, msg, att, row));
        }
    }

    private void rebuildAttachmentContainerIfNeeded(JPanel container, ChatMessage msg,
                                                    FileAttachment att, MessageRow row) {
        if (!SwingUtilities.isDescendingFrom(row, messagesContainer)) {
            return;
        }
        if (att.isImage()
                && att.getStatus() == FileAttachment.Status.COMPLETED
                && java.util.Objects.equals(container.getClientProperty("aero.renderedInline"), att.getLocalPath())
                && Boolean.valueOf(att.isSaving()).equals(container.getClientProperty("aero.renderedSaving"))) {
            return;
        }
        container.removeAll();
        populateAttachmentContainer(container, msg, att, row);
        container.revalidate();
        container.repaint();
        row.revalidate();
        row.repaint();
        messagesContainer.invalidate();
        messagesScroll.validate();
    }

    private JButton createDownloadButton(FileAttachment att, boolean enabled,
                                         Runnable onStateChanged) {
        JButton btn = UiIcons.themedButton(
                null,
                UiIcons.download(() -> enabled ? Theme.TEXT_PRIMARY : Theme.TEXT_MUTED, 15),
                () -> Theme.BTN_DARK,
                () -> Theme.BTN_DARK_HOVER,
                () -> Theme.BTN_DARK_BORDER,
                () -> Theme.TEXT_PRIMARY,
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
        JButton btn = UiIcons.themedButton(
                null,
                UiIcons.cancel(() -> Color.WHITE, 13),
                () -> Theme.BTN_DANGER,
                () -> Theme.BTN_DANGER_HOVER,
                () -> Theme.BTN_DARK_BORDER,
                () -> Color.WHITE,
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
                        messagesContainer.invalidate();
                        messagesScroll.validate();
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
                if (outcome == PeerNetwork.DownloadOutcome.COMPLETED) {
                    scheduleHistorySave();
                } else if (outcome == PeerNetwork.DownloadOutcome.UNAVAILABLE) {
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
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(Theme.BG_WINDOW);
        body.setBorder(BorderFactory.createEmptyBorder(10, 16, 6, 16));

        JLabel promptLabel = new JLabel(I18n.t(I18n.Key.RENAME_PROMPT));
        promptLabel.setFont(Theme.FONT_BODY);
        promptLabel.setForeground(Theme.TEXT_PRIMARY);
        promptLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(promptLabel);
        body.add(Box.createVerticalStrut(8));

        JTextField nameInput = new JTextField(network.getMyName());
        styleTextField(nameInput);
        nameInput.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        nameInput.setAlignmentX(Component.LEFT_ALIGNMENT);
        nameInput.selectAll();
        body.add(nameInput);

        JButton cancelButton = secondaryButton(I18n.t(I18n.Key.CANCEL));
        JButton saveButton = accentButton(I18n.t(I18n.Key.SAVE));
        saveButton.setPreferredSize(new Dimension(96, 32));

        JDialog dialog = createStyledDialog(I18n.t(I18n.Key.RENAME_TITLE), body,
                dialogButtons(cancelButton, saveButton), saveButton, nameInput);
        cancelButton.addActionListener(e -> dialog.dispose());
        saveButton.addActionListener(e -> {
            String updated = nameInput.getText().trim();
            if (!updated.isEmpty()) {
                applyDisplayName(updated);
                dialog.dispose();
            }
        });
        dialog.setVisible(true);
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
        if (selfAvatar != null) {
            selfAvatar.setNameText(name);
        }
        nameChangeListener.accept(name);
    }

    private void updateMyNameLabel() {
        myNameLabel.setText(I18n.t(I18n.Key.YOU, network.getMyName()));
    }

    private void updateChatSubtitle() {
        if (activePeer != null) {
            boolean online = latestPeers.stream()
                    .anyMatch(peer -> peer.getId().equals(activePeer.getId()));
            headerSubtitle.setText("● " + I18n.t(online ? I18n.Key.ONLINE : I18n.Key.OFFLINE));
            headerSubtitle.setForeground(online ? Theme.ONLINE_DOT : Theme.TEXT_MUTED);
        } else if (activeGroup != null) {
            headerSubtitle.setText(memberStatus(activeGroup));
            headerSubtitle.setForeground(Theme.TEXT_MUTED);
        } else {
            headerSubtitle.setForeground(Theme.TEXT_MUTED);
        }
    }

    private String memberStatusShort(ChatGroup group) {
        int members = group.getMemberCount();
        int online = countOnlineMembers(group);
        return members == 1
                ? I18n.t(I18n.Key.MEMBER_STATUS_ONE, online)
                : I18n.t(I18n.Key.MEMBER_STATUS_MANY, members, online);
    }

    private String memberStatus(ChatGroup group) {
        String status = memberStatusShort(group);
        if (group.isPasswordProtected()) {
            status += " · " + I18n.t(I18n.Key.PASSWORD_PROTECTED);
        }
        return status;
    }

    private int countOnlineMembers(ChatGroup group) {
        int online = group.hasMember(network.getMyId()) ? 1 : 0;
        for (Peer peer : latestPeers) {
            if (group.hasMember(peer.getId())) {
                online++;
            }
        }
        return Math.min(online, group.getMemberCount());
    }

    private void changeLanguage(I18n.Language language) {
        if (I18n.getLanguage() == language) return;
        I18n.setLanguage(language);
        settings.setLanguage(language);
        applyLanguageTexts();
        languageChangeListener.run();
    }

    private void applyLanguageTexts() {
        backButton.setToolTipText(I18n.t(I18n.Key.BACK_TOOLTIP));
        settingsButton.setToolTipText(I18n.t(I18n.Key.SETTINGS_TOOLTIP));
        membersButton.setToolTipText(I18n.t(I18n.Key.VIEW_MEMBERS));
        attachButton.setToolTipText(I18n.t(I18n.Key.ATTACH_FILE_TOOLTIP));
        dropBanner.setText(I18n.t(I18n.Key.DROP_FILES_HINT));
        newGroupButton.setText(I18n.t(I18n.Key.GROUP_BUTTON));
        renameButton.setToolTipText(I18n.t(I18n.Key.RENAME_BUTTON));
        inputField.setToolTipText(editingMessage != null
                ? I18n.t(I18n.Key.EDIT_MESSAGE_HINT)
                : I18n.t(I18n.Key.CHAT_INPUT_TOOLTIP));
        if (editTitleLabel != null) {
            editTitleLabel.setText(I18n.t(I18n.Key.EDIT_MESSAGE_TITLE));
        }
        if (cancelEditButton != null) {
            cancelEditButton.setToolTipText(I18n.t(I18n.Key.CANCEL));
        }
        if (sendButton != null) {
            sendButton.setToolTipText(editingMessage != null
                    ? I18n.t(I18n.Key.EDIT_MESSAGE_HINT)
                    : I18n.t(I18n.Key.CHAT_INPUT_TOOLTIP));
        }
        myStatusLabel.setText(I18n.t(I18n.Key.ONLINE));
        messageCounter.setToolTipText(I18n.t(I18n.Key.MESSAGE_LIMIT_TOOLTIP,
                ChatMessage.MAX_TEXT_LENGTH));

        themeSectionLabel.setText(I18n.t(I18n.Key.THEME_SECTION));
        presetsHintLabel.setText(I18n.t(I18n.Key.THEME_PRESETS));
        resetColorsButton.setText(I18n.t(I18n.Key.RESET_COLORS));
        for (PresetChipButton btn : presetButtons) {
            btn.updateLabel();
        }
        for (ColorSwatchControl sw : colorSwatches) {
            sw.updateLabel();
        }

        messagesSectionLabel.setText(I18n.t(I18n.Key.MESSAGES_SECTION));
        autoDownloadImagesCheck.setText(I18n.t(I18n.Key.AUTO_DOWNLOAD_IMAGES_LABEL));
        autoDownloadImagesHintLabel.setText(I18n.t(I18n.Key.AUTO_DOWNLOAD_IMAGES_HINT));
        keepMessagesCheck.setText(I18n.t(I18n.Key.KEEP_MESSAGES_LABEL));
        keepMessagesHintLabel.setText(I18n.t(I18n.Key.KEEP_MESSAGES_HINT));

        windowSectionLabel.setText(I18n.t(I18n.Key.WINDOW_SECTION));
        notificationsCheck.setText(I18n.t(I18n.Key.NOTIFICATIONS_LABEL));
        alwaysOnTopCheck.setText(I18n.t(I18n.Key.ALWAYS_ON_TOP_LABEL));

        nameSectionLabel.setText(I18n.t(I18n.Key.DISPLAY_NAME_SECTION));
        saveNameButton.setText(I18n.t(I18n.Key.SAVE));
        languageSectionLabel.setText(I18n.t(I18n.Key.LANGUAGE_SECTION));
        storageLabel.setText("<html>" + I18n.t(I18n.Key.SETTINGS_AUTOSAVE) + "</html>");

        germanRadio.setSelected(I18n.getLanguage() == I18n.Language.GERMAN);
        englishRadio.setSelected(I18n.getLanguage() == I18n.Language.ENGLISH);

        updateMyNameLabel();

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

        JPanel dlgRoot = new JPanel(new BorderLayout());
        dlgRoot.setBackground(Theme.BG_WINDOW);
        dlgRoot.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1));

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.BG_HEADER);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(9, 14, 9, 8)
        ));

        JLabel title = new JLabel(titleText);
        title.setFont(Theme.FONT_TITLE);
        title.setForeground(Theme.TEXT_PRIMARY);

        JButton closeButton = UiIcons.iconButton(
                UiIcons.cancel(() -> Theme.TEXT_MUTED, 13),
                UiIcons.cancel(() -> Theme.TEXT_PRIMARY, 13),
                26, 7);
        closeButton.addActionListener(e -> dialog.dispose());

        header.add(title, BorderLayout.CENTER);
        header.add(closeButton, BorderLayout.EAST);

        dlgRoot.add(header, BorderLayout.NORTH);
        dlgRoot.add(body, BorderLayout.CENTER);
        dlgRoot.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(dlgRoot);
        if (defaultButton != null) {
            dialog.getRootPane().setDefaultButton(defaultButton);
        }

        dlgRoot.registerKeyboardAction(
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
        UiComponents.styleScrollPane(memberScroll, () -> Theme.BG_WINDOW);
        memberScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1));
        memberScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
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
                    decryptStoredMessages(group, key, true);
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

    private void decryptStoredMessages(ChatGroup group, byte[] key, boolean autoDownloadLiveImages) {
        List<ChatMessage> snapshot;
        synchronized (histories) {
            List<ChatMessage> messages = histories.get(groupKey(group.getId()));
            if (messages == null || messages.isEmpty()) return;
            snapshot = new ArrayList<>(messages);
        }
        for (ChatMessage message : snapshot) {
            boolean unlockedAttachment = false;
            if (message.hasAttachment() && message.isIncoming()) {
                FileAttachment att = message.getAttachment();
                if (att.isEncryptedPendingUnlock()) {
                    unlockedAttachment = decryptGroupOfferMetadata(message, key);
                }
            }
            if (message.isEncrypted() && message.isIncoming()) {
                try {
                    message.setDecryptedText(ChatMessage.truncate(GroupCrypto.decrypt(
                            key, message.getIv(), message.getCipherText())));
                } catch (GeneralSecurityException | IllegalArgumentException ignored) {
                }
            }
            if (autoDownloadLiveImages && unlockedAttachment) {
                FileAttachment att = message.getAttachment();
                if (att != null && network.getPeerById(att.getOfferPeerId()) != null) {
                    autoDownloadImageIfEligible(message, group);
                }
            }
        }
    }

    private boolean decryptGroupOfferMetadata(ChatMessage message, byte[] key) {
        FileAttachment att = message.getAttachment();
        if (att == null || !att.isEncryptedPendingUnlock()) {
            return false;
        }
        try {
            String metaPlain = GroupCrypto.decrypt(key, att.getMetaIv(), att.getMetaCipherText());
            String[] metaParts = metaPlain.split("\\|", -1);
            if (metaParts.length < 3) {
                return false;
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
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void deleteGroup(ChatGroup group) {
        showDangerConfirmDialog(
                I18n.t(I18n.Key.DELETE_GROUP_TITLE),
                I18n.t(I18n.Key.DELETE_GROUP_MESSAGE, group.getName()),
                () -> performDeleteGroup(group));
    }

    private void performDeleteGroup(ChatGroup group) {

        List<ChatMessage> removedMsgs;
        synchronized (histories) {
            removedMsgs = histories.get(groupKey(group.getId()));
            if (removedMsgs != null) {
                removedMsgs = new ArrayList<>(removedMsgs);
            }
        }
        if (removedMsgs != null) {
            for (ChatMessage msg : removedMsgs) {
                if (msg == editingMessage) {
                    cancelEditingMessage(false);
                }
                cleanupMessageResources(msg);
                if (!msg.isIncoming() && msg.hasAttachment()) {
                    network.revokeFileOffer(msg.getAttachment().getTransferId());
                }
            }
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

    public ChatGroup handleRemoteGroupDeletion(Peer sender, String groupId) {
        ChatGroup group = settings.getGroup(groupId);
        if (group == null || !group.hasMember(sender.getId())) {
            return null;
        }

        settings.deleteGroup(groupId);
        removeHistory(groupKey(groupId));
        unreadCounts.remove(groupKey(groupId));
        if (activeGroup != null && activeGroup.getId().equals(groupId)) {
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
        UiComponents.styleScrollPane(scroll, () -> Theme.BG_WINDOW);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1));
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
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

        UiComponents.RoundedPanel row = new UiComponents.RoundedPanel(
                new BorderLayout(8, 0), 10, () -> Theme.BG_CARD, () -> Theme.BORDER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        row.setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 10));

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

        JPanel left = new JPanel(new BorderLayout(8, 0));
        left.setOpaque(false);
        left.add(dot, BorderLayout.WEST);
        left.add(name, BorderLayout.CENTER);
        row.add(left, BorderLayout.CENTER);

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
        label.setBorder(BorderFactory.createEmptyBorder(4, 2, 5, 2));
        return label;
    }

    private void styleCheckBox(JCheckBox box) {
        box.setOpaque(false);
        box.setContentAreaFilled(false);
        box.setBorderPainted(false);
        box.setForeground(Theme.TEXT_PRIMARY);
        box.setFont(Theme.FONT_BODY);
        box.setFocusPainted(false);
        box.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        box.setIconTextGap(8);
        box.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        box.setIcon(new Icon() {
            private static final int S = 16;

            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                boolean selected = c instanceof JCheckBox cb && cb.isSelected();
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(selected ? Theme.ACCENT : Theme.BG_INPUT);
                g2.fillRoundRect(x, y, S, S, 5, 5);
                g2.setColor(selected ? Theme.ACCENT : Theme.BORDER);
                g2.drawRoundRect(x, y, S - 1, S - 1, 5, 5);
                if (selected) {
                    g2.setColor(Theme.BTN_ACCENT_TEXT);
                    g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawLine(x + 4, y + 8, x + 7, y + 11);
                    g2.drawLine(x + 7, y + 11, x + 12, y + 5);
                }
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return S;
            }

            @Override
            public int getIconHeight() {
                return S;
            }
        });
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

    private JButton accentButton(String text) {
        JButton button = UiIcons.themedButton(
                text,
                null,
                () -> Theme.BTN_ACCENT,
                () -> Theme.BTN_ACCENT_HOVER,
                () -> Theme.BTN_DARK_BORDER,
                () -> Theme.BTN_ACCENT_TEXT,
                8
        );
        button.setFont(Theme.FONT_SMALL);
        button.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        return button;
    }

    private JButton secondaryButton(String text) {
        JButton button = UiIcons.themedButton(
                text,
                null,
                () -> Theme.BTN_DARK,
                () -> Theme.BTN_DARK_HOVER,
                () -> Theme.BTN_DARK_BORDER,
                () -> Theme.TEXT_PRIMARY,
                8
        );
        button.setFont(Theme.FONT_SMALL);
        button.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
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

    private final class PresetChipButton extends JButton {
        private static final long serialVersionUID = 1L;
        private final Theme.Preset preset;

        PresetChipButton(Theme.Preset preset) {
            this.preset = preset;
            setUI(new BasicButtonUI());
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setFont(Theme.FONT_SMALL);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setHorizontalAlignment(SwingConstants.LEFT);
            setMargin(new Insets(3, 6, 3, 6));
            setBorder(BorderFactory.createEmptyBorder(4, 26, 4, 6));
            updateLabel();
            addActionListener(e -> applyCustomPalette(
                    preset.bgWindow(), preset.accent(), preset.bubbleOut(), preset.bubbleIn()));
        }

        void updateLabel() {
            I18n.Key key = switch (preset.id()) {
                case "ocean" -> I18n.Key.PRESET_OCEAN;
                case "emerald" -> I18n.Key.PRESET_EMERALD;
                case "rose" -> I18n.Key.PRESET_ROSE;
                case "graphite" -> I18n.Key.PRESET_GRAPHITE;
                case "light" -> I18n.Key.PRESET_LIGHT;
                default -> I18n.Key.PRESET_AERO;
            };
            setText(I18n.t(key));
        }

        void refresh() {
            setForeground(Theme.TEXT_PRIMARY);
            repaint();
        }

        private boolean isSelectedPreset() {
            return preset.bgWindow().equals(Theme.BG_WINDOW)
                    && preset.accent().equals(Theme.ACCENT)
                    && preset.bubbleOut().equals(Theme.BUBBLE_OUT)
                    && preset.bubbleIn().equals(Theme.BUBBLE_IN);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean selected = isSelectedPreset();
            Color fill = getModel().isRollover() ? Theme.BG_CARD_HOVER : Theme.BG_INPUT;
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 9, 9);
            g2.setColor(selected ? Theme.ACCENT : Theme.BORDER);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 9, 9);

            int dot = 10;
            int dy = (getHeight() - dot) / 2;
            g2.setColor(preset.bgWindow());
            g2.fillOval(6, dy, dot, dot);
            g2.setColor(preset.accent());
            g2.fillOval(11, dy, dot, dot);
            g2.setColor(Theme.BORDER);
            g2.drawOval(6, dy, dot, dot);
            g2.dispose();

            super.paintComponent(g);
        }
    }

    private final class ColorSwatchControl extends UiComponents.RoundedPanel {
        private static final long serialVersionUID = 1L;
        private final I18n.Key labelKey;
        private final Supplier<Color> colorSupplier;
        private final JLabel titleLabel = new JLabel();
        private final JLabel hexLabel = new JLabel();
        private final JComponent previewDot;

        ColorSwatchControl(I18n.Key labelKey, Supplier<Color> colorSupplier, Consumer<Color> onPicked) {
            super(new BorderLayout(8, 0), 9, () -> Theme.BG_INPUT, () -> Theme.BORDER);
            this.labelKey = labelKey;
            this.colorSupplier = colorSupplier;
            setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            previewDot = new JComponent() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(colorSupplier.get());
                    g2.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 6, 6);
                    g2.setColor(Theme.BORDER);
                    g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 6, 6);
                    g2.dispose();
                }
            };
            previewDot.setPreferredSize(new Dimension(20, 20));

            JPanel info = new JPanel(new BorderLayout(0, 0));
            info.setOpaque(false);
            titleLabel.setFont(Theme.FONT_SMALL.deriveFont(Font.BOLD));
            hexLabel.setFont(Theme.FONT_SMALL);
            info.add(titleLabel, BorderLayout.NORTH);
            info.add(hexLabel, BorderLayout.SOUTH);

            add(previewDot, BorderLayout.WEST);
            add(info, BorderLayout.CENTER);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (!SwingUtilities.isLeftMouseButton(e)) {
                        return;
                    }
                    Color current = colorSupplier.get();
                    Color chosen = JColorChooser.showDialog(
                            ChatPopupWindow.this,
                            I18n.t(I18n.Key.COLOR_PICKER_TITLE, I18n.t(labelKey)),
                            current);
                    if (chosen != null && !chosen.equals(current)) {
                        onPicked.accept(chosen);
                    }
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    setBgSupplier(() -> Theme.BG_CARD_HOVER);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    setBgSupplier(() -> Theme.BG_INPUT);
                }
            });
            updateLabel();
            refresh();
        }

        void updateLabel() {
            titleLabel.setText(I18n.t(labelKey));
        }

        void refresh() {
            titleLabel.setForeground(Theme.TEXT_PRIMARY);
            hexLabel.setForeground(Theme.TEXT_MUTED);
            hexLabel.setText(Theme.toHex(colorSupplier.get()));
            previewDot.repaint();
            repaint();
        }
    }

    private static final class ScrollableListPanel extends JPanel implements Scrollable {
        private static final long serialVersionUID = 1L;

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
        private static final long serialVersionUID = 1L;
        final ChatMessage msg;
        final UiComponents.RoundedPanel bubble;
        final JPanel body;
        final JLabel metaLabel;
        final JPanel actionStrip;
        final JButton quickEditBtn;
        final JButton optionsBtn;
        JLabel senderLabel;
        JTextArea textArea;
        Component textSpacer;

        MessageRow(ChatMessage msg, boolean showSenderHeader, boolean compactTop, int maxTextWidth) {
            this.msg = msg;
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(compactTop ? 1 : 5, 0, 2, 0));

            boolean incoming = msg.isIncoming();
            this.bubble = new UiComponents.RoundedPanel(
                    new BorderLayout(0, 4),
                    14,
                    () -> incoming ? Theme.BUBBLE_IN : Theme.BUBBLE_OUT,
                    () -> {
                        if (editingMessage == msg) {
                            return Theme.isLight(Theme.BG_WINDOW) ? Theme.ACCENT : Color.WHITE;
                        }
                        return incoming ? Theme.BORDER : null;
                    }) {
                @Override
                public Dimension getMaximumSize() {
                    return getPreferredSize();
                }
            };
            this.bubble.setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 10));
            this.bubble.setAlignmentY(Component.BOTTOM_ALIGNMENT);

            if (showSenderHeader && incoming) {
                this.senderLabel = new JLabel(msg.getSenderName());
                this.senderLabel.setFont(Theme.FONT_BOLD);
                this.senderLabel.setForeground(Theme.ACCENT_HOVER);
                attachMessagePopup(this.senderLabel, this);
                this.bubble.add(this.senderLabel, BorderLayout.NORTH);
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
                this.textArea = createSelectableBubbleText(msg.getText(), incoming, maxTextWidth, this);
                this.textArea.setAlignmentX(Component.LEFT_ALIGNMENT);
                this.body.add(this.textArea);
            }

            this.metaLabel = new JLabel();
            this.metaLabel.setFont(Theme.FONT_SMALL);
            this.metaLabel.setHorizontalAlignment(SwingConstants.RIGHT);
            updateMetaLabel();

            this.bubble.add(this.body, BorderLayout.CENTER);
            this.bubble.add(this.metaLabel, BorderLayout.SOUTH);

            this.actionStrip = new JPanel(new FlowLayout(
                    incoming ? FlowLayout.LEFT : FlowLayout.RIGHT, 2, 0));
            this.actionStrip.setOpaque(false);
            this.actionStrip.setAlignmentY(Component.BOTTOM_ALIGNMENT);

            boolean editable = !incoming && !(att != null && att.isEncryptedPendingUnlock());
            if (editable) {
                this.quickEditBtn = UiIcons.iconButton(
                        UiIcons.edit(() -> Theme.TEXT_MUTED, 12),
                        UiIcons.edit(() -> Theme.TEXT_PRIMARY, 12),
                        20, 6);
                this.quickEditBtn.setToolTipText(I18n.t(I18n.Key.EDIT_MESSAGE));
                this.quickEditBtn.addActionListener(e -> promptEditMessage(msg));
                this.actionStrip.add(this.quickEditBtn);
            } else {
                this.quickEditBtn = null;
            }

            this.optionsBtn = UiIcons.iconButton(
                    UiIcons.moreDots(() -> Theme.TEXT_MUTED, 13),
                    UiIcons.moreDots(() -> Theme.TEXT_PRIMARY, 13),
                    20, 6);
            this.optionsBtn.setToolTipText(I18n.t(I18n.Key.MESSAGE_OPTIONS_TOOLTIP));
            this.optionsBtn.addActionListener(e -> {
                JPopupMenu menu = buildMessagePopupMenu(this);
                menu.show(this.optionsBtn, 0, this.optionsBtn.getHeight());
            });
            this.actionStrip.add(this.optionsBtn);
            Dimension stripPref = this.actionStrip.getPreferredSize();
            this.actionStrip.setMaximumSize(stripPref);

            attachMessagePopup(this.bubble, this);
            attachMessagePopup(this.body, this);
            attachMessagePopup(this.metaLabel, this);
            attachMessagePopup(this, this);

            if (incoming) {
                add(this.bubble);
                add(Box.createHorizontalStrut(4));
                add(this.actionStrip);
                add(Box.createHorizontalGlue());
            } else {
                add(Box.createHorizontalGlue());
                add(this.actionStrip);
                add(Box.createHorizontalStrut(4));
                add(this.bubble);
            }
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
            if (msg.isNotDelivered()) {
                metaLabel.setForeground(Theme.isLight(Theme.BUBBLE_OUT)
                        ? Theme.DANGER
                        : new Color(255, 198, 198));
            } else {
                metaLabel.setForeground(msg.isIncoming() ? Theme.BUBBLE_IN_META : Theme.BUBBLE_OUT_META);
            }
        }

        void updateBurstGrouping(boolean showSenderHeader, boolean compactTop) {
            setBorder(BorderFactory.createEmptyBorder(compactTop ? 1 : 5, 0, 2, 0));
            if (msg.isIncoming()) {
                if (showSenderHeader && senderLabel == null) {
                    senderLabel = new JLabel(msg.getSenderName());
                    senderLabel.setFont(Theme.FONT_BOLD);
                    senderLabel.setForeground(Theme.ACCENT_HOVER);
                    attachMessagePopup(senderLabel, this);
                    bubble.add(senderLabel, BorderLayout.NORTH);
                } else if (!showSenderHeader && senderLabel != null) {
                    bubble.remove(senderLabel);
                    senderLabel = null;
                }
            }
            bubble.revalidate();
            revalidate();
        }

        void updateWrapWidth(int maxTextWidth) {
            if (textArea != null) {
                applyBubbleTextWidth(textArea, maxTextWidth);
                textArea.revalidate();
            }
            bubble.revalidate();
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
                    textArea = createSelectableBubbleText(msg.getText(), msg.isIncoming(), maxTextWidth, this);
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
        final boolean ownedTempFile;

        PendingAttachment(Path file, String displayName, long size, boolean image,
                          ImageIcon thumbnail, boolean ownedTempFile) {
            this.file = file;
            this.displayName = displayName;
            this.size = size;
            this.image = image;
            this.thumbnail = thumbnail;
            this.ownedTempFile = ownedTempFile;
        }
    }
}
