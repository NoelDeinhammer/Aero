package com.quickchat.security;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.function.LongConsumer;

/**
 * Password protection for group chats.
 *
 * <p>A password is turned into a 256-bit AES key with PBKDF2-HMAC-SHA256 and a
 * random salt. Messages are encrypted with AES-GCM, so nobody without the
 * password can read them. Only the salt and a password verifier are shared
 * with group members; the password itself is never sent over the network.</p>
 */
public final class GroupCrypto {
    private static final String KDF = "PBKDF2WithHmacSHA256";
    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final String VERIFIER_CONTEXT = "Aero group password verifier v1";
    private static final int ITERATIONS = 210_000;
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int STREAM_CHUNK_SIZE = 65_536;
    private static final byte[] STREAM_MAGIC = new byte[]{'A', 'F', 'S', '1'};
    private static final SecureRandom RANDOM = new SecureRandom();

    private GroupCrypto() {
    }

    /**
     * Creates fresh password metadata for a new group.
     */
    public static KeyMaterial create(String password) throws GeneralSecurityException {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] key = deriveKey(password, salt);
        return new KeyMaterial(encode(salt), verifier(key), encode(key));
    }

    /**
     * Derives the AES key for an existing group from its stored salt.
     */
    public static byte[] deriveKey(String password, String saltBase64) throws GeneralSecurityException {
        if (saltBase64 == null || saltBase64.isEmpty()) {
            throw new GeneralSecurityException("Missing password salt");
        }
        byte[] salt = Base64.getDecoder().decode(saltBase64);
        return deriveKey(password, salt);
    }

    private static byte[] deriveKey(String password, byte[] salt) throws GeneralSecurityException {
        char[] chars = password == null ? new char[0] : password.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars, salt, ITERATIONS, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance(KDF).generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    /**
     * A verifier lets members check a password without exposing the key.
     */
    public static String verifier(byte[] key) throws GeneralSecurityException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(VERIFIER_CONTEXT.getBytes(StandardCharsets.UTF_8));
        digest.update(key);
        return encode(digest.digest());
    }

    public static boolean verify(byte[] key, String expectedVerifier) {
        if (key == null || expectedVerifier == null || expectedVerifier.isEmpty()) {
            return false;
        }
        try {
            byte[] expected = Base64.getDecoder().decode(expectedVerifier);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(VERIFIER_CONTEXT.getBytes(StandardCharsets.UTF_8));
            digest.update(key);
            return MessageDigest.isEqual(expected, digest.digest());
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    public static Encrypted encrypt(byte[] key, String plaintext) throws GeneralSecurityException {
        byte[] iv = new byte[IV_BYTES];
        RANDOM.nextBytes(iv);
        Cipher cipher = Cipher.getInstance(CIPHER);
        cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(TAG_BITS, iv));
        byte[] cipherText = cipher.doFinal(
                (plaintext == null ? "" : plaintext).getBytes(StandardCharsets.UTF_8));
        return new Encrypted(encode(iv), encode(cipherText));
    }

    public static String decrypt(byte[] key, String ivBase64, String cipherTextBase64)
            throws GeneralSecurityException {
        byte[] iv = Base64.getDecoder().decode(ivBase64);
        byte[] cipherText = Base64.getDecoder().decode(cipherTextBase64);
        Cipher cipher = Cipher.getInstance(CIPHER);
        cipher.init(Cipher.DECRYPT_MODE,
                new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(TAG_BITS, iv));
        byte[] plaintext = cipher.doFinal(cipherText);
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    /**
     * Encrypts an arbitrarily large stream in 64 KB authenticated AES-GCM chunks.
     */
    public static void encryptStream(byte[] key, InputStream in, OutputStream out,
                                     LongConsumer onPlainBytesRead)
            throws IOException, GeneralSecurityException {
        if (key == null || key.length != KEY_BITS / 8) {
            throw new GeneralSecurityException("Invalid AES key");
        }
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance(CIPHER);
        DataOutputStream dos = new DataOutputStream(out);
        dos.write(STREAM_MAGIC);

        byte[] buffer = new byte[STREAM_CHUNK_SIZE];
        long chunkIndex = 0L;
        long totalRead = 0L;

        while (true) {
            int filled = 0;
            while (filled < buffer.length) {
                int read = in.read(buffer, filled, buffer.length - filled);
                if (read == -1) {
                    break;
                }
                filled += read;
            }
            if (filled == 0) {
                break;
            }

            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(chunkAad(chunkIndex, false));
            byte[] encrypted = cipher.doFinal(buffer, 0, filled);

            dos.writeInt(filled);
            dos.write(iv);
            dos.write(encrypted);

            chunkIndex++;
            totalRead += filled;
            if (onPlainBytesRead != null) {
                onPlainBytesRead.accept(totalRead);
            }
        }

        // Authenticated EOF marker prevents silent truncation.
        byte[] eofIv = new byte[IV_BYTES];
        RANDOM.nextBytes(eofIv);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, eofIv));
        cipher.updateAAD(chunkAad(chunkIndex, true));
        byte[] eofTag = cipher.doFinal();

        dos.writeInt(0);
        dos.write(eofIv);
        dos.write(eofTag);
        dos.flush();
    }

    /**
     * Decrypts a chunked AES-GCM stream produced by {@link #encryptStream}.
     */
    public static void decryptStream(byte[] key, InputStream in, OutputStream out,
                                     LongConsumer onPlainBytesWritten)
            throws IOException, GeneralSecurityException {
        if (key == null || key.length != KEY_BITS / 8) {
            throw new GeneralSecurityException("Invalid AES key");
        }
        DataInputStream dis = new DataInputStream(in);
        byte[] magic = new byte[STREAM_MAGIC.length];
        dis.readFully(magic);
        if (!Arrays.equals(magic, STREAM_MAGIC)) {
            throw new GeneralSecurityException("Invalid encrypted stream header");
        }

        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance(CIPHER);
        long chunkIndex = 0L;
        long totalWritten = 0L;
        int tagBytes = TAG_BITS / 8;

        while (true) {
            int plainLen = dis.readInt();
            if (plainLen < 0 || plainLen > STREAM_CHUNK_SIZE) {
                throw new GeneralSecurityException("Invalid encrypted chunk size");
            }
            byte[] iv = new byte[IV_BYTES];
            dis.readFully(iv);

            if (plainLen == 0) {
                byte[] tag = new byte[tagBytes];
                dis.readFully(tag);
                cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
                cipher.updateAAD(chunkAad(chunkIndex, true));
                cipher.doFinal(tag);
                out.flush();
                break;
            }

            byte[] enc = new byte[plainLen + tagBytes];
            dis.readFully(enc);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(chunkAad(chunkIndex, false));
            byte[] plain = cipher.doFinal(enc);
            out.write(plain);

            chunkIndex++;
            totalWritten += plain.length;
            if (onPlainBytesWritten != null) {
                onPlainBytesWritten.accept(totalWritten);
            }
        }
    }

    private static byte[] chunkAad(long chunkIndex, boolean eof) {
        ByteBuffer buf = ByteBuffer.allocate(eof ? 11 : 8);
        buf.putLong(chunkIndex);
        if (eof) {
            buf.put((byte) 'E').put((byte) 'O').put((byte) 'F');
        }
        return buf.array();
    }

    public static String encodeKey(byte[] key) {
        return encode(key);
    }

    /**
     * Decodes a stored Base64 key and rejects values of the wrong length.
     */
    public static byte[] decodeKey(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return null;
        }
        try {
            byte[] key = Base64.getDecoder().decode(encoded);
            return key.length == KEY_BITS / 8 ? key : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static final class KeyMaterial {
        private final String salt;
        private final String check;
        private final String keyBase64;

        KeyMaterial(String salt, String check, String keyBase64) {
            this.salt = salt;
            this.check = check;
            this.keyBase64 = keyBase64;
        }

        public String getSalt() {
            return salt;
        }

        public String getCheck() {
            return check;
        }

        public String getKeyBase64() {
            return keyBase64;
        }
    }

    public static final class Encrypted {
        private final String iv;
        private final String cipherText;

        Encrypted(String iv, String cipherText) {
            this.iv = iv;
            this.cipherText = cipherText;
        }

        public String getIv() {
            return iv;
        }

        public String getCipherText() {
            return cipherText;
        }
    }
}
