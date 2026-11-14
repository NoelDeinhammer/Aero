package com.quickchat.net;

import com.quickchat.model.ChatGroup;
import com.quickchat.model.ChatMessage;
import com.quickchat.model.Peer;
import com.quickchat.security.GroupCrypto;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

    public interface Listener {
        void onPeersUpdated(List<Peer> peers);
        void onMessageReceived(Peer sender, ChatMessage message);

        default void onGroupMessageReceived(Peer sender, ChatGroup group, ChatMessage message) {
        }

        default void onGroupDeleted(Peer sender, String groupId) {
        }
    }

    private final String myId;
    private volatile String myName;
    private final Listener listener;

    private final Map<String, Peer> peers = new ConcurrentHashMap<>();
    private final Set<String> manualEndpoints = ConcurrentHashMap.newKeySet();
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
        scheduler.shutdownNow();
        connectionPool.shutdownNow();
        closeQuietly(serverSocket);
        closeQuietly(multicastSocket);
        closeQuietly(broadcastSocket);
    }

    public boolean sendMessage(Peer peer, String text) {
        if (peer == null || text == null || text.isEmpty()
                || text.length() > ChatMessage.MAX_TEXT_LENGTH) {
            return false;
        }
        String payload = String.join("|",
                "MSG",
                myId,
                encode(myName),
                String.valueOf(myPort),
                encode(text));
        return sendRaw(peer, payload);
    }

    /**
     * Sends a message to every member of the group that is currently online.
     * Returns the number of members the message was delivered to.
     */
    public int sendGroupMessage(ChatGroup group, String text) {
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
                payload);

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

    /**
     * Tells all currently online members that the group was deleted.
     * Returns the number of members that received the notice.
     */
    public int sendGroupDeletion(ChatGroup group) {
        if (group == null) {
            return 0;
        }
        String payload = buildGroupDeletionPayload(group.getId());
        int delivered = 0;
        for (String memberId : group.getMemberIds()) {
            if (myId.equals(memberId)) continue;
            Peer peer = peers.get(memberId);
            if (peer != null && sendRaw(peer, payload)) {
                delivered++;
            }
        }
        return delivered;
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
        try (Socket s = socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(s.getOutputStream(), true, StandardCharsets.UTF_8)) {
            String line = in.readLine();
            if (line == null) return;

            String remoteHost = s.getInetAddress().getHostAddress();
            if ("PING".equals(line)) {
                out.println(buildHelloPayload());
                return;
            }

            String[] parts = line.split("\\|", -1);
            if ("MSG".equals(parts[0]) && parts.length >= 5) {
                String senderId = parts[1];
                String senderName = decode(parts[2]);
                int senderPort = Integer.parseInt(parts[3]);
                String text = ChatMessage.truncate(decode(parts[4]));

                Peer sender = upsertPeer(senderId, senderName, remoteHost, senderPort);
                ChatMessage msg = new ChatMessage(sender.getId(), sender.getName(), text, true);
                listener.onMessageReceived(sender, msg);
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

                Peer sender = upsertPeer(senderId, senderName, remoteHost, senderPort);
                ChatGroup group = new ChatGroup(groupId, groupName);
                group.setPasswordData(salt, check, "");
                parseGroupMembers(encodedMembers, group);
                group.addMember(sender.getId(), sender.getName());

                ChatMessage msg;
                if (!salt.isEmpty() && !check.isEmpty()) {
                    msg = ChatMessage.encryptedIncoming(groupId, sender.getName(), iv, payload);
                } else {
                    msg = new ChatMessage(groupId, sender.getName(),
                            ChatMessage.truncate(decode(payload)), true);
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

                ChatMessage msg = new ChatMessage(groupId, sender.getName(), text, true);
                listener.onGroupMessageReceived(sender, group, msg);
            } else if ("GDEL".equals(parts[0]) && parts.length >= 5) {
                String senderId = parts[1];
                String senderName = decode(parts[2]);
                int senderPort = Integer.parseInt(parts[3]);
                String groupId = parts[4];
                if (!groupId.matches(ID_PATTERN)) return;

                Peer sender = upsertPeer(senderId, senderName, remoteHost, senderPort);
                listener.onGroupDeleted(sender, groupId);
            }
        } catch (Exception ignored) {
        }
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
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out.println("PING");
            String response = in.readLine();
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
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
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
