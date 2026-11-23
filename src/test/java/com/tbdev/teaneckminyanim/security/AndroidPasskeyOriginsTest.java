package com.tbdev.teaneckminyanim.security;

import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import com.webauthn4j.data.client.Origin;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AndroidPasskeyOriginsTest {
    private static final String FINGERPRINT =
            "14:6D:E9:83:C5:73:06:50:D8:EE:B9:95:2F:34:FC:64:16:A0:83:42:E6:1D:BE:A8:8A:04:96:B2:3F:CF:44:E5";

    @Test
    void fingerprintBecomesApkKeyHashOrigin() {
        byte[] bytes = HexFormat.of().parseHex(FINGERPRINT.replace(":", ""));
        String expected = "android:apk-key-hash:" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        assertEquals(Set.of(expected), WebSecurityConfiguration.androidApkKeyHashOrigins(FINGERPRINT));
        assertEquals("android:apk-key-hash:FG3pg8VzBlDY7rmVLzT8ZBagg0LmHb6oigSWsj_PROU", expected);
        // WebAuthn4J (used by Spring Security's relying party) accepts this origin form.
        Origin parsed = new Origin(expected);
        assertEquals("android", parsed.getScheme());
        assertEquals(expected, parsed.toString());
    }

    @Test
    void multipleBlankAndInvalidFingerprints() {
        assertTrue(WebSecurityConfiguration.androidApkKeyHashOrigins("").isEmpty());
        assertTrue(WebSecurityConfiguration.androidApkKeyHashOrigins(null).isEmpty());
        assertTrue(WebSecurityConfiguration.androidApkKeyHashOrigins("zz:not-hex").isEmpty());
        assertTrue(WebSecurityConfiguration.androidApkKeyHashOrigins("AB:CD").isEmpty(), "must be 32 bytes");
        String second = FINGERPRINT.toLowerCase().replace("14:6d", "15:6d");
        assertEquals(2, WebSecurityConfiguration.androidApkKeyHashOrigins(FINGERPRINT + " , " + second + ",").size());
    }

    @Test
    void allowedOriginsIncludeConfiguredAndroidApps() {
        WebSecurityConfiguration configuration = new WebSecurityConfiguration();
        ReflectionTestUtils.setField(configuration, "androidSha256Fingerprints", FINGERPRINT);
        ApplicationSettingsService settings = mock(ApplicationSettingsService.class);
        when(settings.getSiteRootUrl()).thenReturn("https://teaneckminyanim.com");

        Set<String> origins = configuration.webAuthnAllowedOrigins(settings);

        assertTrue(origins.contains(WebSecurityConfiguration.PASSKEY_PROD_ORIGIN));
        assertTrue(origins.contains(WebSecurityConfiguration.PASSKEY_DEV_ORIGIN));
        assertTrue(origins.contains("android:apk-key-hash:FG3pg8VzBlDY7rmVLzT8ZBagg0LmHb6oigSWsj_PROU"));
        assertEquals(3, origins.size());
    }
}
