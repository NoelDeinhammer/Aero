package com.quickchat.security;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

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
