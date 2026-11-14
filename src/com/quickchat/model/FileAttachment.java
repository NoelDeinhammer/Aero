package com.quickchat.model;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Metadata and local cache state for an image/screenshot or file attached to a chat message.
 */
public class FileAttachment {
    public enum Status {
        AVAILABLE,
        TRANSFERRING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "gif", "bmp", "wbmp"
    );

    private final String transferId;
    private volatile String fileName;
    private volatile long fileSize;
    private volatile boolean image;
    private volatile Path localPath;
    private volatile String metaIv;
    private volatile String metaCipherText;
    private volatile boolean encryptedPendingUnlock;
    private volatile Status status;
    private volatile long bytesTransferred;
    private volatile boolean cancelled;
    private final List<Closeable> activeResources = new ArrayList<>();
    private volatile boolean saving;
    private volatile boolean saveCancelled;
    private volatile long saveBytesCopied;
    private volatile Runnable changeListener;
    private volatile String offerPeerId;
    private volatile String offerHost;
    private volatile int offerPort;
    private volatile String offerGroupId;
    private volatile boolean encryptedOffer;

    public FileAttachment(String transferId, String fileName, long fileSize,
                          boolean image, Path localPath, Status status) {
        this.transferId = (transferId == null || transferId.isBlank())
                ? UUID.randomUUID().toString().replace("-", "").substring(0, 24)
                : transferId;
        this.fileName = sanitizeFileName(fileName);
        this.fileSize = Math.max(0L, fileSize);
        this.image = image;
        this.localPath = localPath;
        this.status = status;
    }

    public static FileAttachment incomingOffer(String transferId, String fileName,
                                               long fileSize, boolean image) {
        return new FileAttachment(transferId, fileName, fileSize, image, null, Status.AVAILABLE);
    }

    public static FileAttachment encryptedIncomingOffer(String transferId, long fileSize,
                                                        String metaIv, String metaCipherText) {
        FileAttachment att = new FileAttachment(transferId, "encrypted.dat", fileSize,
                false, null, Status.AVAILABLE);
        att.metaIv = metaIv;
        att.metaCipherText = metaCipherText;
        att.encryptedPendingUnlock = true;
        att.encryptedOffer = true;
        return att;
    }

    public void setOfferSource(String peerId, String host, int port,
                               String groupId, boolean encryptedOffer) {
        this.offerPeerId = peerId;
        this.offerHost = host;
        this.offerPort = port;
        this.offerGroupId = groupId;
        this.encryptedOffer = encryptedOffer;
    }

    public String getOfferPeerId() {
        return offerPeerId;
    }

    public String getOfferHost() {
        return offerHost;
    }

    public int getOfferPort() {
        return offerPort;
    }

    public String getOfferGroupId() {
        return offerGroupId;
    }

    public boolean isEncryptedOffer() {
        return encryptedOffer;
    }

    public boolean canDownloadFromPeer() {
        return !encryptedPendingUnlock
                && offerPeerId != null
                && !offerPeerId.isEmpty()
                && offerHost != null
                && offerPort > 0;
    }

    public String getTransferId() {
        return transferId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = sanitizeFileName(fileName);
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = Math.max(0L, fileSize);
    }

    public boolean isImage() {
        return image;
    }

    public void setImage(boolean image) {
        this.image = image;
    }

    public Path getLocalPath() {
        return localPath;
    }

    public void setLocalPath(Path localPath) {
        this.localPath = localPath;
    }

    public String getMetaIv() {
        return metaIv;
    }

    public String getMetaCipherText() {
        return metaCipherText;
    }

    public boolean isEncryptedPendingUnlock() {
        return encryptedPendingUnlock;
    }

    public void setEncryptedPendingUnlock(boolean encryptedPendingUnlock) {
        this.encryptedPendingUnlock = encryptedPendingUnlock;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
        notifyListener();
    }

    public long getBytesTransferred() {
        return bytesTransferred;
    }

    public void setBytesTransferred(long bytesTransferred) {
        this.bytesTransferred = Math.max(0L, bytesTransferred);
        notifyListener();
    }

    public int getProgressPercent() {
        long total = fileSize;
        if (total <= 0L) {
            return status == Status.COMPLETED ? 100 : 0;
        }
        return (int) Math.min(100L, Math.max(0L, (bytesTransferred * 100L) / total));
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void registerActiveResource(Closeable resource) {
        if (resource == null) {
            return;
        }
        boolean closeNow = false;
        synchronized (activeResources) {
            if (cancelled) {
                closeNow = true;
            } else {
                activeResources.add(resource);
            }
        }
        if (closeNow) {
            closeQuietly(resource);
        }
    }

    public void unregisterActiveResource(Closeable resource) {
        if (resource == null) {
            return;
        }
        synchronized (activeResources) {
            activeResources.remove(resource);
        }
    }

    /**
     * Cancels an active network upload or incoming download and closes open sockets.
     */
    public void cancel() {
        List<Closeable> toClose;
        synchronized (activeResources) {
            if (cancelled || status != Status.TRANSFERRING) {
                return;
            }
            cancelled = true;
            status = Status.CANCELLED;
            toClose = new ArrayList<>(activeResources);
            activeResources.clear();
        }
        for (Closeable c : toClose) {
            closeQuietly(c);
        }
        notifyListener();
    }

    /**
     * Resets cancellation/progress state before starting a manual download or active upload.
     */
    public boolean resetForTransfer() {
        synchronized (activeResources) {
            if (status == Status.TRANSFERRING) {
                return false;
            }
            cancelled = false;
            bytesTransferred = 0L;
            activeResources.clear();
            status = Status.TRANSFERRING;
        }
        notifyListener();
        return true;
    }

    public boolean isSaving() {
        return saving;
    }

    public boolean isSaveCancelled() {
        return saveCancelled;
    }

    public int getSaveProgressPercent() {
        long total = fileSize;
        if (total <= 0L) {
            return saving ? 0 : 100;
        }
        return (int) Math.min(100L, Math.max(0L, (saveBytesCopied * 100L) / total));
    }

    public synchronized boolean startSaving() {
        if (saving) {
            return false;
        }
        saving = true;
        saveCancelled = false;
        saveBytesCopied = 0L;
        notifyListener();
        return true;
    }

    public void updateSaveProgress(long bytesCopied) {
        this.saveBytesCopied = Math.max(0L, bytesCopied);
        notifyListener();
    }

    public synchronized void cancelSaving() {
        if (!saving) {
            return;
        }
        saveCancelled = true;
        notifyListener();
    }

    public synchronized void finishSaving() {
        saving = false;
        saveCancelled = false;
        saveBytesCopied = 0L;
        notifyListener();
    }

    private static void closeQuietly(Closeable c) {
        try {
            c.close();
        } catch (IOException ignored) {
        }
    }

    public void setChangeListener(Runnable changeListener) {
        this.changeListener = changeListener;
    }

    public void notifyListener() {
        Runnable listener = this.changeListener;
        if (listener != null) {
            listener.run();
        }
    }

    public static boolean isImageFileName(String name) {
        if (name == null) {
            return false;
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return false;
        }
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return IMAGE_EXTENSIONS.contains(ext);
    }

    public static String sanitizeFileName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "file";
        }
        String base = raw.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        try {
            Path p = Paths.get(base).getFileName();
            if (p != null) {
                base = p.toString();
            }
        } catch (Exception ignored) {
        }
        String cleaned = base.replaceAll("[<>:\"/\\\\|?*\\x00-\\x1F]", "_").trim();
        while (cleaned.startsWith(".")) {
            cleaned = cleaned.substring(1).trim();
        }
        if (cleaned.isEmpty() || ".".equals(cleaned) || "..".equals(cleaned)) {
            return "file";
        }
        if (cleaned.length() > 180) {
            int dot = cleaned.lastIndexOf('.');
            if (dot > 0 && cleaned.length() - dot <= 16) {
                String ext = cleaned.substring(dot);
                cleaned = cleaned.substring(0, 180 - ext.length()) + ext;
            } else {
                cleaned = cleaned.substring(0, 180);
            }
        }
        return cleaned;
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double value = bytes;
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unit = 0;
        while (value >= 1024.0 && unit < units.length - 1) {
            value /= 1024.0;
            unit++;
        }
        if (value >= 100.0 || unit == 0) {
            return String.format(Locale.US, "%.0f %s", value, units[unit]);
        }
        if (value >= 10.0) {
            return String.format(Locale.US, "%.1f %s", value, units[unit]);
        }
        return String.format(Locale.US, "%.2f %s", value, units[unit]);
    }
}
