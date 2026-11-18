package com.quickchat.net;

import com.quickchat.model.ChatGroup;
import com.quickchat.model.ChatMessage;
import com.quickchat.model.FileAttachment;
import com.quickchat.model.Peer;
import com.quickchat.security.GroupCrypto;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Zero-database peer-to-peer networking layer.
 * Uses a local TCP server for direct messaging and UDP multicast/broadcast + direct IP/localhost probing
 * for peer discovery without any central server or user accounts.
 */
public class PeerNetwork {
    private static final int PORT_START = 55550;
    private static final int PORT_END = 55559;
    private static final String MULTICAST_GROUP = "230.0.0.1";
    private static final int MULTICAST_PORT = 55556;
    private static final int BROADCAST_PORT = 55557;
    private static final long PEER_TIMEOUT_MS = 12_000;
    private static final String ID_PATTERN = "[A-Za-z0-9_-]{1,64}";
    private static final int FILE_BUFFER_SIZE = 65_536;
    private static final int FILE_SOCKET_TIMEOUT_MS = 60_000;
    private static final int OFFER_ACK_TIMEOUT_MS = 5_000;
    private static final int MAX_HEADER_LINE_BYTES = 65_536;

    public enum DownloadOutcome {
        COMPLETED,
        CANCELLED,
        UNAVAILABLE,
        FAILED
    }

    public interface Listener {
        void onPeersUpdated(List<Peer> peers);
        void onMessageReceived(Peer sender, ChatMessage message);

        default void onGroupMessageReceived(Peer sender, ChatGroup group, ChatMessage message) {
        }

        default void onGroupDeleted(Peer sender, String groupId) {
        }

        default void onFileOffered(Peer sender, ChatMessage message) {
        }

        default boolean onGroupFileOffered(Peer sender, ChatGroup group, ChatMessage message) {
            return true;
        }

        default void onMessageEdited(Peer sender, String messageId, String newText) {
        }

        default void onMessageDeleted(Peer sender, String messageId) {
        }

        default void onGroupMessageEdited(Peer sender, String groupId, String messageId,
                                          String salt, String check, String iv, String payload) {
        }

        default void onGroupMessageDeleted(Peer sender, String groupId, String messageId) {
        }
    }

    private static final class OutgoingOffer {
        final Path file;
        final long fileSize;
        final boolean encrypted;
        final byte[] key;
        final FileAttachment senderAttachment;
        final Set<String> allowedPeerIds;
        final AtomicInteger activeDownloads = new AtomicInteger(0);
        volatile boolean revoked;

        OutgoingOffer(Path file, long fileSize, boolean encrypted, byte[] key,
                      FileAttachment senderAttachment, Set<String> allowedPeerIds) {
            this.file = file;
            this.fileSize = fileSize;
            this.encrypted = encrypted;
            this.key = key;
            this.senderAttachment = senderAttachment;
            this.allowedPeerIds = allowedPeerIds;
        }
    }

    private final String myId;
    private volatile String myName;
    private final Listener listener;
    private volatile Path transferDir = Paths.get(System.getProperty("java.io.tmpdir"), "aero-transfers");

    private final Map<String, Peer> peers = new ConcurrentHashMap<>();
    private final Set<String> manualEndpoints = ConcurrentHashMap.newKeySet();
    private final Map<String, OutgoingOffer> offeredFiles = new ConcurrentHashMap<>();
    private final ExecutorService connectionPool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "quickchat-conn");
        t.setDaemon(true);
        return t;
    });
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "quickchat-discovery");
        t.setDaemon(true);
        return t;
    });

    private ServerSocket serverSocket;
    private MulticastSocket multicastSocket;
    private DatagramSocket broadcastSocket;
    private int myPort = -1;
    private volatile boolean running = false;

    public PeerNetwork(String initialName, Listener listener) {
        this(null, initialName, listener);
    }

    public PeerNetwork(String peerId, String initialName, Listener listener) {
        String trimmedId = peerId == null ? "" : peerId.trim();
        this.myId = trimmedId.isEmpty()
                ? UUID.randomUUID().toString().substring(0, 8)
                : trimmedId;
        this.myName = initialName;
        this.listener = listener;
    }

    public String getMyId() {
        return myId;
    }

    public String getMyName() {
        return myName;
    }

    public void setMyName(String myName) {
        if (myName != null && !myName.trim().isEmpty()) {
            this.myName = myName.trim();
        }
    }

    public void setTransferDir(Path dir) {
        if (dir != null) {
            this.transferDir = dir;
        }
    }

    public Path getTransferDir() {
        return transferDir;
    }

    public int getMyPort() {
        return myPort;
    }

    /**
     * Returns the best local LAN IPv4 address (e.g. 192.168.x.x) to share with friends.
     */
    public String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                String display = ni.getDisplayName().toLowerCase();
                if (display.contains("virtual") || display.contains("vmware") || display.contains("wsl")) continue;

                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress() && addr.isSiteLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    /**
     * Directly connects to a peer by IP (e.g. "192.168.178.42" or "192.168.178.42:55551").
     * Useful when Wi-Fi routers block UDP multicast/broadcast.
     */
    public boolean connectByIp(String input) {
        if (input == null || input.trim().isEmpty()) return false;
        String trimmed = input.trim();
        String host = trimmed;
        int explicitPort = -1;

        if (trimmed.contains(":")) {
            String[] parts = trimmed.split(":", 2);
            host = parts[0].trim();
            try {
                explicitPort = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException e) {
                return false;
            }
        }

        if (explicitPort > 0) {
            if (probeHostSync(host, explicitPort, 1200)) {
                manualEndpoints.add(host + ":" + explicitPort);
                return true;
            }
            return false;
        }

        for (int port = PORT_START; port <= PORT_START + 4; port++) {
            if (probeHostSync(host, port, 500)) {
                manualEndpoints.add(host + ":" + port);
                return true;
            }
        }
        return false;
    }

    public void start() throws IOException {
        running = true;
        serverSocket = bindFirstAvailablePort();
        myPort = serverSocket.getLocalPort();

        Thread acceptThread = new Thread(this::acceptLoop, "quickchat-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();

        startMulticastListener();
        startBroadcastListener();

        scheduler.scheduleAtFixedRate(this::broadcastAndProbe, 0, 2500, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(this::pruneStalePeers, 3000, 3000, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        running = false;
        offeredFiles.clear();
        scheduler.shutdownNow();
        connectionPool.shutdownNow();
        closeQuietly(serverSocket);
        closeQuietly(multicastSocket);
        closeQuietly(broadcastSocket);
    }

    public boolean sendMessage(Peer peer, String text) {
        return sendMessage(peer, null, text);
    }

    public boolean sendMessage(Peer peer, String messageId, String text) {
        if (peer == null || text == null || text.isEmpty()
                || text.length() > ChatMessage.MAX_TEXT_LENGTH) {
            return false;
        }
        String safeId = normalizeMessageId(messageId);
        String payload = String.join("|",
                "MSG",
                myId,
                encode(myName),
                String.valueOf(myPort),
                encode(text),
                safeId);
        return sendRaw(peer, payload);
    }

    public boolean sendMessageEdit(Peer peer, String messageId, String newText) {
        if (peer == null || newText == null || newText.length() > ChatMessage.MAX_TEXT_LENGTH
                || messageId == null || !messageId.matches(ID_PATTERN)) {
            return false;
        }
        String payload = String.join("|",
                "MEDIT",
                myId,
                encode(myName),
                String.valueOf(myPort),
                messageId,
                encode(newText));
        return sendRaw(peer, payload);
    }

    public boolean sendMessageDelete(Peer peer, String messageId) {
        if (peer == null || messageId == null || !messageId.matches(ID_PATTERN)) {
            return false;
        }
        String payload = String.join("|",
                "MDEL",
                myId,
                encode(myName),
                String.valueOf(myPort),
                messageId);
        return sendRaw(peer, payload);
    }

    /**
     * Sends a message to every member of the group that is currently online.
     * Returns the number of members the message was delivered to.
     */
    public int sendGroupMessage(ChatGroup group, String text) {
        return sendGroupMessage(group, null, text);
    }

    public int sendGroupMessage(ChatGroup group, String messageId, String text) {
        if (group == null || text == null || text.isEmpty()
                || text.length() > ChatMessage.MAX_TEXT_LENGTH) {
            return 0;
        }

        String iv = "";
        String payload;
        if (group.isPasswordProtected()) {
            byte[] key = group.getPasswordKeyBytes();
            if (key == null) {
                return 0;
            }
            try {
                GroupCrypto.Encrypted encrypted = GroupCrypto.encrypt(key, text);
                iv = encrypted.getIv();
                payload = encrypted.getCipherText();
            } catch (GeneralSecurityException e) {
                return 0;
            }
        } else {
            payload = encode(text);
        }

        String safeId = normalizeMessageId(messageId);
        String members = serializeGroupMembers(group);
        String packet = String.join("|",
                "GMSG",
                myId,
                encode(myName),
                String.valueOf(myPort),
                group.getId(),
                encode(group.getName()),
                members,
                group.getPasswordSalt(),
                group.getPasswordCheck(),
                iv,
                payload,
                safeId);

        return deliverToGroup(group, packet);
    }

    public int sendGroupMessageEdit(ChatGroup group, String messageId, String newText) {
        if (group == null || newText == null || newText.length() > ChatMessage.MAX_TEXT_LENGTH
                || messageId == null || !messageId.matches(ID_PATTERN)) {
            return 0;
        }

        String iv = "";
        String payload;
        if (group.isPasswordProtected()) {
            byte[] key = group.getPasswordKeyBytes();
            if (key == null) {
                return 0;
            }
            try {
                GroupCrypto.Encrypted encrypted = GroupCrypto.encrypt(key, newText);
                iv = encrypted.getIv();
                payload = encrypted.getCipherText();
            } catch (GeneralSecurityException e) {
                return 0;
            }
        } else {
            payload = encode(newText);
        }

        String packet = String.join("|",
                "GMEDIT",
                myId,
                encode(myName),
                String.valueOf(myPort),
                group.getId(),
                messageId,
                group.getPasswordSalt(),
                group.getPasswordCheck(),
                iv,
                payload);

        return deliverToGroup(group, packet);
    }

    public int sendGroupMessageDelete(ChatGroup group, String messageId) {
        if (group == null || messageId == null || !messageId.matches(ID_PATTERN)) {
            return 0;
        }
        String packet = String.join("|",
                "GMDEL",
                myId,
                encode(myName),
                String.valueOf(myPort),
                group.getId(),
                messageId);

        return deliverToGroup(group, packet);
    }

    private int deliverToGroup(ChatGroup group, String packet) {
        int delivered = 0;
        for (String memberId : group.getMemberIds()) {
            if (myId.equals(memberId)) continue;
            Peer peer = peers.get(memberId);
            if (peer != null && sendRaw(peer, packet)) {
                delivered++;
            }
        }
        return delivered;
    }

    public void revokeFileOffer(String transferId) {
        if (transferId == null || transferId.isEmpty()) {
            return;
        }
        OutgoingOffer offer = offeredFiles.remove(transferId);
        if (offer != null) {
            synchronized (offer) {
                offer.revoked = true;
                offer.senderAttachment.cancel();
            }
        }
    }

    private static String normalizeMessageId(String messageId) {
        if (messageId != null && messageId.matches(ID_PATTERN)) {
            return messageId;
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * Tells all currently online members that the group was deleted.
     * Returns the number of members that received the notice.
     */
    public int sendGroupDeletion(ChatGroup group) {
        if (group == null) {
            return 0;
        }
        return deliverToGroup(group, buildGroupDeletionPayload(group.getId()));
    }

    /**
     * Sends a single deletion notice. Used when a member that was offline
     * writes into a group that was already deleted here.
     */
    public boolean sendGroupDeletionTo(Peer peer, String groupId) {
        if (peer == null || groupId == null || !groupId.matches(ID_PATTERN)) {
            return false;
        }
        return sendRaw(peer, buildGroupDeletionPayload(groupId));
    }

    private String buildGroupDeletionPayload(String groupId) {
        return String.join("|",
                "GDEL",
                myId,
                encode(myName),
                String.valueOf(myPort),
                groupId);
    }

    /**
     * Sends a lightweight file offer to a peer without pushing payload bytes automatically.
     * The receiver chooses whether and where to download the file via {@link #downloadOfferedFileAsync}.
     */
    public void sendFileAsync(Peer peer, Path file, FileAttachment attachment, String caption) {
        if (peer == null || file == null || attachment == null) {
            if (attachment != null) {
                attachment.setStatus(FileAttachment.Status.FAILED);
            }
            return;
        }
        connectionPool.submit(() -> {
            if (attachment.isCancelled()) {
                attachment.setStatus(FileAttachment.Status.CANCELLED);
                return;
            }
            String safeCaption = caption == null ? "" : caption;
            long fileSize = attachment.getFileSize();
            String transferId = attachment.getTransferId();
            OutgoingOffer offer = new OutgoingOffer(
                    file, fileSize, false, null, attachment, Set.of(peer.getId()));
            offeredFiles.put(transferId, offer);

            String header = String.join("|",
                    "FOFFER",
                    myId,
                    encode(myName),
                    String.valueOf(myPort),
                    transferId,
                    attachment.isImage() ? "1" : "0",
                    encode(attachment.getFileName()),
                    String.valueOf(fileSize),
                    encode(safeCaption));

            boolean ok = sendOfferHeader(peer, header, attachment);
            finishOutgoingOffer(transferId, offer, ok);
        });
    }

    /**
     * Sends a lightweight file offer to every online member of a group without pushing
     * payload bytes automatically.
     */
    public void sendGroupFileAsync(ChatGroup group, Path file, FileAttachment attachment, String caption) {
        if (group == null || file == null || attachment == null) {
            if (attachment != null) {
                attachment.setStatus(FileAttachment.Status.FAILED);
            }
            return;
        }
        connectionPool.submit(() -> {
            if (attachment.isCancelled()) {
                attachment.setStatus(FileAttachment.Status.CANCELLED);
                return;
            }
            List<Peer> targets = new ArrayList<>();
            Set<String> allowedIds = new HashSet<>();
            for (String memberId : group.getMemberIds()) {
                if (myId.equals(memberId)) continue;
                allowedIds.add(memberId);
                Peer peer = peers.get(memberId);
                if (peer != null) {
                    targets.add(peer);
                }
            }
            if (targets.isEmpty()) {
                attachment.setStatus(FileAttachment.Status.FAILED);
                return;
            }

            boolean encrypted = group.isPasswordProtected();
            byte[] key = encrypted ? group.getPasswordKeyBytes() : null;
            if (encrypted && key == null) {
                attachment.setStatus(FileAttachment.Status.FAILED);
                return;
            }

            String safeCaption = caption == null ? "" : caption;
            long fileSize = attachment.getFileSize();
            String members = serializeGroupMembers(group);
            String transferId = attachment.getTransferId();

            String header;
            if (encrypted) {
                try {
                    String metaPlain = (attachment.isImage() ? "1" : "0") + "|"
                            + encode(attachment.getFileName()) + "|" + encode(safeCaption);
                    GroupCrypto.Encrypted encMeta = GroupCrypto.encrypt(key, metaPlain);
                    header = String.join("|",
                            "GOFFER",
                            myId,
                            encode(myName),
                            String.valueOf(myPort),
                            group.getId(),
                            encode(group.getName()),
                            members,
                            group.getPasswordSalt(),
                            group.getPasswordCheck(),
                            transferId,
                            "0",
                            encMeta.getIv(),
                            String.valueOf(fileSize),
                            encMeta.getCipherText());
                } catch (GeneralSecurityException e) {
                    attachment.setStatus(FileAttachment.Status.FAILED);
                    return;
                }
            } else {
                header = String.join("|",
                        "GOFFER",
                        myId,
                        encode(myName),
                        String.valueOf(myPort),
                        group.getId(),
                        encode(group.getName()),
                        members,
                        "",
                        "",
                        transferId,
                        attachment.isImage() ? "1" : "0",
                        encode(attachment.getFileName()),
                        String.valueOf(fileSize),
                        encode(safeCaption));
            }

            OutgoingOffer offer = new OutgoingOffer(
                    file, fileSize, encrypted, key, attachment, Set.copyOf(allowedIds));
            offeredFiles.put(transferId, offer);

            AtomicInteger delivered = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(targets.size());

            for (Peer target : targets) {
                connectionPool.submit(() -> {
                    try {
                        if (sendOfferHeader(target, header, attachment)) {
                            delivered.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            finishOutgoingOffer(transferId, offer, delivered.get() > 0);
        });
    }

    private void finishOutgoingOffer(String transferId, OutgoingOffer offer, boolean ok) {
        FileAttachment attachment = offer.senderAttachment;
        synchronized (offer) {
            if (attachment.isCancelled()) {
                offer.revoked = true;
                offeredFiles.remove(transferId, offer);
                if (offer.activeDownloads.get() == 0) {
                    attachment.setStatus(FileAttachment.Status.CANCELLED);
                }
            } else if (ok) {
                if (offer.activeDownloads.get() == 0) {
                    attachment.setBytesTransferred(offer.fileSize);
                    attachment.setStatus(FileAttachment.Status.COMPLETED);
                }
            } else {
                offer.revoked = true;
                offeredFiles.remove(transferId, offer);
                attachment.setStatus(FileAttachment.Status.FAILED);
            }
        }
    }

    /**
     * Pulls an offered file directly into {@code destination} when requested manually by the user.
     */
    public void downloadOfferedFileAsync(FileAttachment attachment, Path destination,
                                         byte[] groupKey, Consumer<DownloadOutcome> onComplete) {
        if (attachment == null || destination == null || !attachment.canDownloadFromPeer()) {
            if (onComplete != null) {
                onComplete.accept(DownloadOutcome.FAILED);
            }
            return;
        }
        if (attachment.isEncryptedOffer() && groupKey == null) {
            if (onComplete != null) {
                onComplete.accept(DownloadOutcome.FAILED);
            }
            return;
        }
        if (!attachment.resetForTransfer()) {
            return;
        }
        connectionPool.submit(() -> {
            DownloadOutcome outcome = DownloadOutcome.FAILED;
            boolean openedDestination = false;
            try {
                Peer live = peers.get(attachment.getOfferPeerId());
                String host = live != null ? live.getHost() : attachment.getOfferHost();
                int port = live != null ? live.getPort() : attachment.getOfferPort();

                try (Socket socket = new Socket()) {
                    attachment.registerActiveResource(socket);
                    try {
                        socket.connect(new InetSocketAddress(host, port), 3000);
                        socket.setSoTimeout(FILE_SOCKET_TIMEOUT_MS);

                        OutputStream rawOut = socket.getOutputStream();
                        String req = String.join("|",
                                "FGET",
                                myId,
                                String.valueOf(myPort),
                                attachment.getTransferId());
                        rawOut.write((req + "\n").getBytes(StandardCharsets.UTF_8));
                        rawOut.flush();

                        BufferedInputStream netIn = new BufferedInputStream(
                                socket.getInputStream(), FILE_BUFFER_SIZE);
                        String resp = readLineUtf8(netIn, 1024);
                        if (!"OK".equals(resp)) {
                            outcome = "ERR|UNAVAILABLE".equals(resp)
                                    ? DownloadOutcome.UNAVAILABLE
                                    : DownloadOutcome.FAILED;
                        } else if (attachment.isCancelled()) {
                            outcome = DownloadOutcome.CANCELLED;
                        } else {
                            Path parent = destination.getParent();
                            if (parent != null) {
                                Files.createDirectories(parent);
                            }
                            openedDestination = true;
                            boolean ok = attachment.isEncryptedOffer()
                                    ? receiveEncryptedToFile(socket, netIn, destination, groupKey, attachment)
                                    : receivePlainToFile(netIn, destination, attachment.getFileSize(), attachment);
                            if (ok && !attachment.isCancelled()) {
                                outcome = DownloadOutcome.COMPLETED;
                            } else if (attachment.isCancelled()) {
                                outcome = DownloadOutcome.CANCELLED;
                            } else {
                                outcome = DownloadOutcome.FAILED;
                            }
                        }
                    } finally {
                        attachment.unregisterActiveResource(socket);
                    }
                }
            } catch (IOException e) {
                outcome = attachment.isCancelled() ? DownloadOutcome.CANCELLED : DownloadOutcome.FAILED;
            }

            if (outcome == DownloadOutcome.COMPLETED) {
                attachment.setLocalPath(destination);
                attachment.setBytesTransferred(attachment.getFileSize());
                attachment.setStatus(FileAttachment.Status.COMPLETED);
            } else {
                if (openedDestination) {
                    try {
                        Files.deleteIfExists(destination);
                    } catch (IOException ignored) {
                    }
                }
                attachment.setLocalPath(null);
                attachment.setStatus(outcome == DownloadOutcome.CANCELLED
                        ? FileAttachment.Status.CANCELLED
                        : FileAttachment.Status.FAILED);
            }
            if (onComplete != null) {
                onComplete.accept(outcome);
            }
        });
    }

    private boolean sendOfferHeader(Peer peer, String header, FileAttachment attachment) {
        if (attachment.isCancelled()) {
            return false;
        }
        try (Socket socket = new Socket()) {
            attachment.registerActiveResource(socket);
            try {
                socket.connect(new InetSocketAddress(peer.getHost(), peer.getPort()), 2000);
                socket.setSoTimeout(OFFER_ACK_TIMEOUT_MS);

                OutputStream rawOut = socket.getOutputStream();
                rawOut.write((header + "\n").getBytes(StandardCharsets.UTF_8));
                rawOut.flush();

                String ack = readLineUtf8(socket.getInputStream(), 1024);
                return !attachment.isCancelled() && "OK".equals(ack);
            } finally {
                attachment.unregisterActiveResource(socket);
            }
        } catch (IOException e) {
            return false;
        }
    }

    private boolean sendRaw(Peer peer, String payload) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(peer.getHost(), peer.getPort()), 1200);
            PrintWriter out = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            out.println(payload);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static String serializeGroupMembers(ChatGroup group) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : group.getMembers().entrySet()) {
            if (sb.length() > 0) sb.append(',');
            sb.append(entry.getKey()).append(':').append(encode(entry.getValue()));
        }
        return sb.toString();
    }

    public List<Peer> getOnlinePeers() {
        List<Peer> list = new ArrayList<>(peers.values());
        list.sort(Comparator.comparing(Peer::getName, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    public Peer getPeerById(String peerId) {
        return peerId == null ? null : peers.get(peerId);
    }

    private ServerSocket bindFirstAvailablePort() throws IOException {
        for (int port = PORT_START; port <= PORT_END; port++) {
            try {
                return new ServerSocket(port);
            } catch (IOException ignored) {
                // Try next port
            }
        }
        return new ServerSocket(0);
    }

    private void acceptLoop() {
        while (running && !serverSocket.isClosed()) {
            try {
                Socket client = serverSocket.accept();
                connectionPool.submit(() -> handleConnection(client));
            } catch (IOException e) {
                if (running) {
                    // Transient accept error
                }
            }
        }
    }

    private void handleConnection(Socket socket) {
        try (Socket s = socket) {
            BufferedInputStream in = new BufferedInputStream(s.getInputStream(), FILE_BUFFER_SIZE);
            OutputStream rawOut = s.getOutputStream();
            String line = readLineUtf8(in, MAX_HEADER_LINE_BYTES);
            if (line == null) return;

            String remoteHost = s.getInetAddress().getHostAddress();
            if ("PING".equals(line)) {
                rawOut.write((buildHelloPayload() + "\n").getBytes(StandardCharsets.UTF_8));
                rawOut.flush();
                return;
            }

            String[] parts = line.split("\\|", -1);
            if ("MSG".equals(parts[0]) && parts.length >= 5) {
                String senderId = parts[1];
                String senderName = decode(parts[2]);
                int senderPort = Integer.parseInt(parts[3]);
                String text = ChatMessage.truncate(decode(parts[4]));
                String msgId = (parts.length >= 6 && parts[5].matches(ID_PATTERN)) ? parts[5] : null;

                Peer sender = upsertPeer(senderId, senderName, remoteHost, senderPort);
                ChatMessage msg = ChatMessage.incoming(msgId, sender.getId(), sender.getId(), sender.getName(), text);
                listener.onMessageReceived(sender, msg);
            } else if ("MEDIT".equals(parts[0]) && parts.length >= 6) {
                String senderId = parts[1];
                String senderName = decode(parts[2]);
                int senderPort = Integer.parseInt(parts[3]);
                String messageId = parts[4];
                if (!messageId.matches(ID_PATTERN)) return;
                String newText = ChatMessage.truncate(decode(parts[5]));

                Peer sender = upsertPeer(senderId, senderName, remoteHost, senderPort);
                listener.onMessageEdited(sender, messageId, newText);
            } else if ("MDEL".equals(parts[0]) && parts.length >= 5) {
                String senderId = parts[1];
                String senderName = decode(parts[2]);
                int senderPort = Integer.parseInt(parts[3]);
                String messageId = parts[4];
                if (!messageId.matches(ID_PATTERN)) return;

                Peer sender = upsertPeer(senderId, senderName, remoteHost, senderPort);
                listener.onMessageDeleted(sender, messageId);
            } else if ("GMSG".equals(parts[0]) && parts.length >= 11) {
                String senderId = parts[1];
                String senderName = decode(parts[2]);
                int senderPort = Integer.parseInt(parts[3]);
                String groupId = parts[4];
                if (!groupId.matches(ID_PATTERN)) return;

                String groupName = decode(parts[5]);
                String encodedMembers = parts[6];
                String salt = parts[7];
                String check = parts[8];
                String iv = parts[9];
                String payload = parts[10];
                String msgId = (parts.length >= 12 && parts[11].matches(ID_PATTERN)) ? parts[11] : null;

                Peer sender = upsertPeer(senderId, senderName, remoteHost, senderPort);
                ChatGroup group = new ChatGroup(groupId, groupName);
                group.setPasswordData(salt, check, "");
                parseGroupMembers(encodedMembers, group);
                group.addMember(sender.getId(), sender.getName());

                ChatMessage msg;
                if (!salt.isEmpty() && !check.isEmpty()) {
                    msg = ChatMessage.encryptedIncoming(msgId, groupId, sender.getId(), sender.getName(), iv, payload);
                } else {
                    msg = ChatMessage.incoming(msgId, groupId, sender.getId(), sender.getName(),
                            ChatMessage.truncate(decode(payload)));
                }
                listener.onGroupMessageReceived(sender, group, msg);
            } else if ("GMSG".equals(parts[0]) && parts.length >= 8) {
                // Backwards compatibility with older Aero versions that did
                // not support password-protected groups.
                String senderId = parts[1];
                String senderName = decode(parts[2]);
                int senderPort = Integer.parseInt(parts[3]);
                String groupId = parts[4];
                if (!groupId.matches(ID_PATTERN)) return;

                String groupName = decode(parts[5]);
                String encodedMembers = parts[6];
                String text = ChatMessage.truncate(decode(parts[7]));

                Peer sender = upsertPeer(senderId, senderName, remoteHost, senderPort);
                ChatGroup group = new ChatGroup(groupId, groupName);
                parseGroupMembers(encodedMembers, group);
                group.addMember(sender.getId(), sender.getName());

                ChatMessage msg = ChatMessage.incoming(null, groupId, sender.getId(), sender.getName(), text);
                listener.onGroupMessageReceived(sender, group, msg);
            } else if ("GMEDIT".equals(parts[0]) && parts.length >= 10) {
                String senderId = parts[1];
                String senderName = decode(parts[2]);
                int senderPort = Integer.parseInt(parts[3]);
                String groupId = parts[4];
                String messageId = parts[5];
                if (!groupId.matches(ID_PATTERN) || !messageId.matches(ID_PATTERN)) return;
                String salt = parts[6];
                String check = parts[7];
                String iv = parts[8];
                String payload = parts[9];

                Peer sender = upsertPeer(senderId, senderName, remoteHost, senderPort);
                listener.onGroupMessageEdited(sender, groupId, messageId, salt, check, iv, payload);
            } else if ("GMDEL".equals(parts[0]) && parts.length >= 6) {
                String senderId = parts[1];
                String senderName = decode(parts[2]);
                int senderPort = Integer.parseInt(parts[3]);
                String groupId = parts[4];
                String messageId = parts[5];
                if (!groupId.matches(ID_PATTERN) || !messageId.matches(ID_PATTERN)) return;

                Peer sender = upsertPeer(senderId, senderName, remoteHost, senderPort);
                listener.onGroupMessageDeleted(sender, groupId, messageId);
            } else if ("GDEL".equals(parts[0]) && parts.length >= 5) {
                String senderId = parts[1];
                String senderName = decode(parts[2]);
                int senderPort = Integer.parseInt(parts[3]);
                String groupId = parts[4];
                if (!groupId.matches(ID_PATTERN)) return;

                Peer sender = upsertPeer(senderId, senderName, remoteHost, senderPort);
                listener.onGroupDeleted(sender, groupId);
            } else if ("FOFFER".equals(parts[0]) && parts.length >= 9) {
                handleIncomingFileOffer(rawOut, remoteHost, parts);
            } else if ("GOFFER".equals(parts[0]) && parts.length >= 14) {
                handleIncomingGroupFileOffer(rawOut, remoteHost, parts);
            } else if ("FGET".equals(parts[0]) && parts.length >= 4) {
                handleIncomingFileFetch(s, rawOut, parts);
            }
        } catch (Exception ignored) {
        }
    }

    private void handleIncomingFileOffer(OutputStream rawOut, String remoteHost, String[] parts)
            throws IOException {
        String senderId = parts[1];
        String senderName = decode(parts[2]);
        int senderPort = Integer.parseInt(parts[3]);
        String transferId = parts[4];
        if (!transferId.matches(ID_PATTERN)) return;

        boolean isImage = "1".equals(parts[5]);
        String fileName = FileAttachment.sanitizeFileName(decode(parts[6]));
        long fileSize = Long.parseLong(parts[7]);
        if (fileSize < 0) return;
        String caption = ChatMessage.truncate(decode(parts[8]));

        Peer sender = upsertPeer(senderId, senderName, remoteHost, senderPort);
        FileAttachment attachment = FileAttachment.incomingOffer(transferId, fileName, fileSize, isImage);
        attachment.setOfferSource(sender.getId(), remoteHost, senderPort, null, false);
        ChatMessage msg = ChatMessage.incomingWithAttachment(
                transferId, sender.getId(), sender.getId(), sender.getName(), caption, attachment);

        rawOut.write("OK\n".getBytes(StandardCharsets.UTF_8));
        rawOut.flush();
        listener.onFileOffered(sender, msg);
    }

    private void handleIncomingGroupFileOffer(OutputStream rawOut, String remoteHost, String[] parts)
            throws IOException {
        String senderId = parts[1];
        String senderName = decode(parts[2]);
        int senderPort = Integer.parseInt(parts[3]);
        String groupId = parts[4];
        if (!groupId.matches(ID_PATTERN)) return;

        String groupName = decode(parts[5]);
        String encodedMembers = parts[6];
        String salt = parts[7];
        String check = parts[8];
        String transferId = parts[9];
        if (!transferId.matches(ID_PATTERN)) return;

        boolean encrypted = !salt.isEmpty() && !check.isEmpty();
        long fileSize = Long.parseLong(parts[12]);
        if (fileSize < 0) return;

        Peer sender = upsertPeer(senderId, senderName, remoteHost, senderPort);
        ChatGroup group = new ChatGroup(groupId, groupName);
        group.setPasswordData(salt, check, "");
        parseGroupMembers(encodedMembers, group);
        group.addMember(sender.getId(), sender.getName());

        FileAttachment attachment;
        ChatMessage msg;
        if (encrypted) {
            String metaIv = parts[11];
            String metaCipher = parts[13];
            attachment = FileAttachment.encryptedIncomingOffer(transferId, fileSize, metaIv, metaCipher);
            attachment.setOfferSource(sender.getId(), remoteHost, senderPort, groupId, true);
            msg = ChatMessage.encryptedFileIncoming(
                    transferId, groupId, sender.getId(), sender.getName(), attachment);
        } else {
            boolean isImage = "1".equals(parts[10]);
            String fileName = FileAttachment.sanitizeFileName(decode(parts[11]));
            String caption = ChatMessage.truncate(decode(parts[13]));
            attachment = FileAttachment.incomingOffer(transferId, fileName, fileSize, isImage);
            attachment.setOfferSource(sender.getId(), remoteHost, senderPort, groupId, false);
            msg = ChatMessage.incomingWithAttachment(
                    transferId, groupId, sender.getId(), sender.getName(), caption, attachment);
        }

        if (!listener.onGroupFileOffered(sender, group, msg)) {
            return;
        }
        rawOut.write("OK\n".getBytes(StandardCharsets.UTF_8));
        rawOut.flush();
    }

    private void handleIncomingFileFetch(Socket s, OutputStream rawOut, String[] parts) throws IOException {
        String requesterId = parts[1];
        String transferId = parts[3];
        if (!transferId.matches(ID_PATTERN) || !requesterId.matches(ID_PATTERN)) {
            return;
        }

        OutgoingOffer offer = offeredFiles.get(transferId);
        if (offer == null) {
            rawOut.write("ERR|UNAVAILABLE\n".getBytes(StandardCharsets.UTF_8));
            rawOut.flush();
            return;
        }

        synchronized (offer) {
            if (offer.revoked
                    || offer.senderAttachment.isCancelled()
                    || !offer.allowedPeerIds.contains(requesterId)
                    || !Files.isRegularFile(offer.file)) {
                rawOut.write("ERR|UNAVAILABLE\n".getBytes(StandardCharsets.UTF_8));
                rawOut.flush();
                return;
            }
            if (offer.activeDownloads.getAndIncrement() == 0) {
                offer.senderAttachment.resetForTransfer();
            }
        }

        s.setSoTimeout(FILE_SOCKET_TIMEOUT_MS);
        offer.senderAttachment.registerActiveResource(s);
        try {
            OutputStream bufOut = new BufferedOutputStream(rawOut, FILE_BUFFER_SIZE);
            bufOut.write("OK\n".getBytes(StandardCharsets.UTF_8));
            bufOut.flush();

            boolean ok = offer.encrypted
                    ? streamEncryptedBodyToSocket(s, bufOut, offer)
                    : streamPlainBodyToSocket(bufOut, offer);
            if (ok && !offer.senderAttachment.isCancelled()) {
                bufOut.flush();
            }
        } catch (IOException ignored) {
        } finally {
            offer.senderAttachment.unregisterActiveResource(s);
            synchronized (offer) {
                int remaining = offer.activeDownloads.decrementAndGet();
                if (offer.senderAttachment.isCancelled()) {
                    offer.revoked = true;
                    offeredFiles.remove(transferId, offer);
                    offer.senderAttachment.setStatus(FileAttachment.Status.CANCELLED);
                } else if (remaining == 0) {
                    offer.senderAttachment.setBytesTransferred(offer.fileSize);
                    offer.senderAttachment.setStatus(FileAttachment.Status.COMPLETED);
                }
            }
        }
    }

    private boolean streamPlainBodyToSocket(OutputStream out, OutgoingOffer offer) throws IOException {
        byte[] buffer = new byte[FILE_BUFFER_SIZE];
        long totalSent = 0L;
        long lastNotify = 0L;
        try (InputStream fileIn = new BufferedInputStream(Files.newInputStream(offer.file), FILE_BUFFER_SIZE)) {
            while (totalSent < offer.fileSize) {
                if (offer.senderAttachment.isCancelled()) {
                    return false;
                }
                int maxRead = (int) Math.min(buffer.length, offer.fileSize - totalSent);
                int read = fileIn.read(buffer, 0, maxRead);
                if (read == -1) {
                    return false;
                }
                out.write(buffer, 0, read);
                totalSent += read;
                long now = System.currentTimeMillis();
                if (now - lastNotify >= 80 || totalSent == offer.fileSize) {
                    lastNotify = now;
                    offer.senderAttachment.setBytesTransferred(totalSent);
                }
            }
        }
        return !offer.senderAttachment.isCancelled();
    }

    private boolean streamEncryptedBodyToSocket(Socket socket, OutputStream out, OutgoingOffer offer)
            throws IOException {
        long[] lastNotify = {0L};
        try (InputStream fileIn = new BufferedInputStream(Files.newInputStream(offer.file), FILE_BUFFER_SIZE)) {
            GroupCrypto.encryptStream(offer.key, fileIn, out, sent -> {
                if (offer.senderAttachment.isCancelled()) {
                    closeQuietly(socket);
                    return;
                }
                long now = System.currentTimeMillis();
                if (now - lastNotify[0] >= 80 || sent == offer.fileSize) {
                    lastNotify[0] = now;
                    offer.senderAttachment.setBytesTransferred(sent);
                }
            });
            return !offer.senderAttachment.isCancelled();
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    private boolean receivePlainToFile(InputStream in, Path targetFile, long expectedSize,
                                       FileAttachment attachment) {
        byte[] buffer = new byte[FILE_BUFFER_SIZE];
        long totalRead = 0L;
        long lastNotify = 0L;
        try (OutputStream fileOut = new BufferedOutputStream(Files.newOutputStream(targetFile), FILE_BUFFER_SIZE)) {
            while (totalRead < expectedSize) {
                if (attachment.isCancelled()) {
                    return false;
                }
                int maxRead = (int) Math.min(buffer.length, expectedSize - totalRead);
                int read = in.read(buffer, 0, maxRead);
                if (read == -1) {
                    return false;
                }
                fileOut.write(buffer, 0, read);
                totalRead += read;
                long now = System.currentTimeMillis();
                if (now - lastNotify >= 80 || totalRead == expectedSize) {
                    lastNotify = now;
                    attachment.setBytesTransferred(totalRead);
                }
            }
            fileOut.flush();
            return !attachment.isCancelled();
        } catch (IOException e) {
            return false;
        }
    }

    private boolean receiveEncryptedToFile(Socket socket, InputStream netIn, Path destination,
                                           byte[] groupKey, FileAttachment attachment) {
        long[] lastNotify = {0L};
        try (OutputStream fileOut = new BufferedOutputStream(Files.newOutputStream(destination), FILE_BUFFER_SIZE)) {
            GroupCrypto.decryptStream(groupKey, netIn, fileOut, written -> {
                if (attachment.isCancelled()) {
                    closeQuietly(socket);
                    return;
                }
                long now = System.currentTimeMillis();
                if (now - lastNotify[0] >= 80 || written == attachment.getFileSize()) {
                    lastNotify[0] = now;
                    attachment.setBytesTransferred(written);
                }
            });
            fileOut.flush();
            return !attachment.isCancelled();
        } catch (IOException | GeneralSecurityException e) {
            return false;
        }
    }

    private static String readLineUtf8(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(256);
        int b;
        boolean any = false;
        while ((b = in.read()) != -1) {
            any = true;
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                if (baos.size() >= maxBytes) {
                    throw new IOException("Header line too long");
                }
                baos.write(b);
            }
        }
        if (!any && baos.size() == 0) {
            return null;
        }
        return baos.toString(StandardCharsets.UTF_8);
    }

    private void startMulticastListener() {
        try {
            multicastSocket = new MulticastSocket(MULTICAST_PORT);
            multicastSocket.setReuseAddress(true);
            InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
            multicastSocket.joinGroup(new InetSocketAddress(group, MULTICAST_PORT), null);

            Thread mcThread = new Thread(() -> {
                byte[] buf = new byte[1024];
                while (running && !multicastSocket.isClosed()) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buf, buf.length);
                        multicastSocket.receive(packet);
                        String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();
                        handleHello(msg, packet.getAddress().getHostAddress());
                    } catch (IOException e) {
                        if (!running) break;
                    }
                }
            }, "quickchat-multicast");
            mcThread.setDaemon(true);
            mcThread.start();
        } catch (IOException ignored) {
        }
    }

    private void startBroadcastListener() {
        try {
            broadcastSocket = new DatagramSocket(null);
            broadcastSocket.setReuseAddress(true);
            broadcastSocket.setBroadcast(true);
            broadcastSocket.bind(new InetSocketAddress(BROADCAST_PORT));

            Thread bcThread = new Thread(() -> {
                byte[] buf = new byte[1024];
                while (running && !broadcastSocket.isClosed()) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buf, buf.length);
                        broadcastSocket.receive(packet);
                        String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();
                        handleHello(msg, packet.getAddress().getHostAddress());
                    } catch (IOException e) {
                        if (!running) break;
                    }
                }
            }, "quickchat-broadcast");
            bcThread.setDaemon(true);
            bcThread.start();
        } catch (IOException ignored) {
        }
    }

    private void broadcastAndProbe() {
        if (!running) return;
        byte[] data = buildHelloPayload().getBytes(StandardCharsets.UTF_8);

        // 1. UDP Multicast
        if (multicastSocket != null && !multicastSocket.isClosed()) {
            try {
                InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
                multicastSocket.send(new DatagramPacket(data, data.length, group, MULTICAST_PORT));
            } catch (IOException ignored) {
            }
        }

        // 2. UDP Broadcast (global + per-interface subnet broadcast)
        if (broadcastSocket != null && !broadcastSocket.isClosed()) {
            try {
                InetAddress globalBc = InetAddress.getByName("255.255.255.255");
                broadcastSocket.send(new DatagramPacket(data, data.length, globalBc, BROADCAST_PORT));
            } catch (IOException ignored) {
            }
            try {
                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                while (interfaces.hasMoreElements()) {
                    NetworkInterface ni = interfaces.nextElement();
                    if (!ni.isUp() || ni.isLoopback()) continue;
                    for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                        InetAddress bc = ia.getBroadcast();
                        if (bc != null) {
                            try {
                                broadcastSocket.send(new DatagramPacket(data, data.length, bc, BROADCAST_PORT));
                            } catch (IOException ignored) {
                            }
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }

        // 3. Keep manually connected IP endpoints alive
        for (String endpoint : manualEndpoints) {
            String[] parts = endpoint.split(":", 2);
            if (parts.length == 2) {
                connectionPool.submit(() -> probeHostSync(parts[0], Integer.parseInt(parts[1]), 500));
            }
        }

        // 4. Localhost port probe for same-PC testing
        for (int port = PORT_START; port <= PORT_END; port++) {
            if (port == myPort) continue;
            final int targetPort = port;
            connectionPool.submit(() -> probeHostSync("127.0.0.1", targetPort, 150));
        }
    }

    private boolean probeHostSync(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            OutputStream out = socket.getOutputStream();
            out.write("PING\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            String response = readLineUtf8(socket.getInputStream(), MAX_HEADER_LINE_BYTES);
            if (response != null) {
                handleHello(response, host);
                return true;
            }
        } catch (IOException ignored) {
        }
        return false;
    }

    private void handleHello(String payload, String remoteHost) {
        String[] parts = payload.split("\\|", -1);
        if (parts.length < 4 || !"HELLO".equals(parts[0])) return;

        String peerId = parts[1];
        if (myId.equals(peerId)) return;

        try {
            String peerName = decode(parts[2]);
            int peerPort = Integer.parseInt(parts[3]);
            upsertPeer(peerId, peerName, remoteHost, peerPort);
        } catch (NumberFormatException ignored) {
        }
    }

    private static void parseGroupMembers(String encodedMembers, ChatGroup group) {
        if (encodedMembers == null || encodedMembers.isEmpty()) return;
        for (String pair : encodedMembers.split(",")) {
            int separator = pair.indexOf(':');
            if (separator <= 0) continue;
            String memberId = pair.substring(0, separator);
            if (!memberId.matches(ID_PATTERN)) continue;
            try {
                String memberName = decode(pair.substring(separator + 1));
                group.addMember(memberId, memberName);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private Peer upsertPeer(String id, String name, String host, int port) {
        boolean[] changed = {false};
        Peer peer = peers.compute(id, (k, existing) -> {
            if (existing == null) {
                changed[0] = true;
                return new Peer(id, name, host, port);
            }
            if (!existing.getName().equals(name)) {
                existing.setName(name);
                changed[0] = true;
            }
            existing.touch();
            return existing;
        });

        if (changed[0]) {
            listener.onPeersUpdated(getOnlinePeers());
        }
        return peer;
    }

    private void pruneStalePeers() {
        long now = System.currentTimeMillis();
        boolean removed = peers.values().removeIf(p -> (now - p.getLastSeenMs()) > PEER_TIMEOUT_MS);
        if (removed) {
            listener.onPeersUpdated(getOnlinePeers());
        }
    }

    private String buildHelloPayload() {
        return String.join("|", "HELLO", myId, encode(myName), String.valueOf(myPort));
    }

    private static String encode(String raw) {
        return Base64.getEncoder().encodeToString((raw == null ? "" : raw).getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String b64) {
        return new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
    }
}
