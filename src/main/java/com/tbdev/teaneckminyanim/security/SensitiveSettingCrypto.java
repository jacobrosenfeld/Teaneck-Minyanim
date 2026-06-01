package com.tbdev.teaneckminyanim.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Component
public class SensitiveSettingCrypto {
    public static final String ENCRYPTED_PREFIX = "ENC:v1:";

    private static final String KEY_PREFIX = "base64:";
    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SecretKeySpec secretKey;

    public SensitiveSettingCrypto(@Value("${app.settings.encryption-key:}") String encryptionKey) {
        this.secretKey = parseKey(encryptionKey);
    }

    private SensitiveSettingCrypto(SecretKeySpec secretKey) {
        this.secretKey = secretKey;
    }

    public static SensitiveSettingCrypto disabled() {
        return new SensitiveSettingCrypto((SecretKeySpec) null);
    }

    public static SensitiveSettingCrypto fromBase64Key(String base64Key) {
        return new SensitiveSettingCrypto(KEY_PREFIX + base64Key);
    }

    public boolean isConfigured() {
        return secretKey != null;
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(ENCRYPTED_PREFIX);
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return plaintext;
        }
        requireConfigured();

        try {
            byte[] iv = new byte[IV_BYTES];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer payload = ByteBuffer.allocate(iv.length + ciphertext.length);
            payload.put(iv);
            payload.put(ciphertext);
            return ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(payload.array());
        } catch (Exception e) {
            throw new SensitiveSettingCryptoException("Sensitive setting could not be encrypted.", e);
        }
    }

    public String decrypt(String storedValue) {
        if (storedValue == null || storedValue.isBlank() || !isEncrypted(storedValue)) {
            return storedValue;
        }
        requireConfigured();

        try {
            byte[] payload = Base64.getDecoder().decode(storedValue.substring(ENCRYPTED_PREFIX.length()));
            if (payload.length <= IV_BYTES) {
                throw new SensitiveSettingCryptoException("Sensitive setting ciphertext is invalid.");
            }

            byte[] iv = Arrays.copyOfRange(payload, 0, IV_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(payload, IV_BYTES, payload.length);

            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (SensitiveSettingCryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new SensitiveSettingCryptoException("Sensitive setting could not be decrypted.", e);
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new SensitiveSettingCryptoException(
                    "Sensitive settings encryption key is not configured. Set APP_SETTINGS_ENCRYPTION_KEY.");
        }
    }

    private SecretKeySpec parseKey(String configuredKey) {
        if (configuredKey == null || configuredKey.isBlank()) {
            return null;
        }

        String normalized = configuredKey.trim();
        byte[] keyBytes;

        if (normalized.startsWith(KEY_PREFIX)) {
            keyBytes = decodeBase64Key(normalized.substring(KEY_PREFIX.length()));
        } else {
            keyBytes = decodeBase64Key(normalized);
        }

        validateAesKeyLength(keyBytes);
        return new SecretKeySpec(keyBytes, "AES");
    }

    private byte[] decodeBase64Key(String base64Key) {
        try {
            return Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException e) {
            throw new SensitiveSettingCryptoException("APP_SETTINGS_ENCRYPTION_KEY is not valid base64.", e);
        }
    }

    private void validateAesKeyLength(byte[] keyBytes) {
        int length = keyBytes == null ? 0 : keyBytes.length;
        if (length != 16 && length != 24 && length != 32) {
            throw new SensitiveSettingCryptoException(
                    "APP_SETTINGS_ENCRYPTION_KEY must be base64-encoded 16, 24, or 32 byte AES key material.");
        }
    }

    public static class SensitiveSettingCryptoException extends RuntimeException {
        public SensitiveSettingCryptoException(String message) {
            super(message);
        }

        public SensitiveSettingCryptoException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
