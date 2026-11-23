package com.tbdev.teaneckminyanim.service.auth;

import java.security.SecureRandom;
import java.util.Base64;

/** Generates and recognises the raw bearer secrets. Raw values are never stored, only their SHA-256 hash. */
public final class SecureTokens {
    public static final String API_KEY_PREFIX = "tnm_";
    public static final String ACCESS_TOKEN_PREFIX = "tnmapp_";
    public static final String REFRESH_TOKEN_PREFIX = "tnmrt_";

    /** Random part of an API key: 40 base62 characters (~238 bits). */
    public static final int API_KEY_RANDOM_LENGTH = 40;
    /** Random part of app tokens: 32 random bytes, base64url without padding = 43 characters. */
    public static final int APP_TOKEN_RANDOM_LENGTH = 43;
    /** Length of the stored display prefix, e.g. {@code tnm_AbCd1234}. */
    public static final int API_KEY_DISPLAY_PREFIX_LENGTH = 12;

    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final SecureRandom RANDOM = new SecureRandom();

    private SecureTokens() {
    }

    public static String newApiKey() {
        StringBuilder key = new StringBuilder(API_KEY_PREFIX.length() + API_KEY_RANDOM_LENGTH).append(API_KEY_PREFIX);
        for (int i = 0; i < API_KEY_RANDOM_LENGTH; i++) {
            key.append(BASE62.charAt(RANDOM.nextInt(BASE62.length())));
        }
        return key.toString();
    }

    public static String newAccessToken() {
        return ACCESS_TOKEN_PREFIX + randomBase64Url();
    }

    public static String newRefreshToken() {
        return REFRESH_TOKEN_PREFIX + randomBase64Url();
    }

    public static boolean looksLikeApiKey(String raw) {
        if (raw == null || !raw.startsWith(API_KEY_PREFIX)
                || raw.length() != API_KEY_PREFIX.length() + API_KEY_RANDOM_LENGTH) {
            return false;
        }
        for (int i = API_KEY_PREFIX.length(); i < raw.length(); i++) {
            if (BASE62.indexOf(raw.charAt(i)) < 0) return false;
        }
        return true;
    }

    public static boolean looksLikeAccessToken(String raw) {
        return hasBase64UrlBody(raw, ACCESS_TOKEN_PREFIX);
    }

    public static boolean looksLikeRefreshToken(String raw) {
        return hasBase64UrlBody(raw, REFRESH_TOKEN_PREFIX);
    }

    private static boolean hasBase64UrlBody(String raw, String prefix) {
        if (raw == null || !raw.startsWith(prefix) || raw.length() != prefix.length() + APP_TOKEN_RANDOM_LENGTH) {
            return false;
        }
        for (int i = prefix.length(); i < raw.length(); i++) {
            char c = raw.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_';
            if (!ok) return false;
        }
        return true;
    }

    private static String randomBase64Url() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
