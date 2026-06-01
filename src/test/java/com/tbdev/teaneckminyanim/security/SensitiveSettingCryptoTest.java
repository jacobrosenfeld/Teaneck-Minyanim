package com.tbdev.teaneckminyanim.security;

import com.tbdev.teaneckminyanim.security.SensitiveSettingCrypto.SensitiveSettingCryptoException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveSettingCryptoTest {
    private static final String TEST_KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    @Test
    void encrypt_returnsPrefixedCiphertextThatDecryptsToOriginalValue() {
        SensitiveSettingCrypto crypto = SensitiveSettingCrypto.fromBase64Key(TEST_KEY);

        String encrypted = crypto.encrypt("smtp-secret");

        assertTrue(encrypted.startsWith(SensitiveSettingCrypto.ENCRYPTED_PREFIX));
        assertNotEquals("smtp-secret", encrypted);
        assertFalse(encrypted.contains("smtp-secret"));
        assertEquals("smtp-secret", crypto.decrypt(encrypted));
    }

    @Test
    void encrypt_usesRandomIvForEachValue() {
        SensitiveSettingCrypto crypto = SensitiveSettingCrypto.fromBase64Key(TEST_KEY);

        String first = crypto.encrypt("smtp-secret");
        String second = crypto.encrypt("smtp-secret");

        assertNotEquals(first, second);
        assertEquals("smtp-secret", crypto.decrypt(first));
        assertEquals("smtp-secret", crypto.decrypt(second));
    }

    @Test
    void decrypt_requiresConfiguredKeyForEncryptedValues() {
        SensitiveSettingCrypto crypto = SensitiveSettingCrypto.fromBase64Key(TEST_KEY);
        String encrypted = crypto.encrypt("smtp-secret");

        assertThrows(
                SensitiveSettingCryptoException.class,
                () -> SensitiveSettingCrypto.disabled().decrypt(encrypted));
    }

    @Test
    void decrypt_returnsPlainValuesUnchanged() {
        assertEquals("plain", SensitiveSettingCrypto.disabled().decrypt("plain"));
        assertEquals("", SensitiveSettingCrypto.disabled().decrypt(""));
    }
}
