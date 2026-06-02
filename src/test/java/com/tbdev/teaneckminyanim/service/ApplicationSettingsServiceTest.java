package com.tbdev.teaneckminyanim.service;

import com.tbdev.teaneckminyanim.enums.SettingKey;
import com.tbdev.teaneckminyanim.model.ApplicationSettings;
import com.tbdev.teaneckminyanim.repo.ApplicationSettingsRepository;
import com.tbdev.teaneckminyanim.security.SensitiveSettingCrypto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

class ApplicationSettingsServiceTest {
    private static final String TEST_KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    private ApplicationSettingsRepository repository;
    private SensitiveSettingCrypto crypto;

    @BeforeEach
    void setUp() {
        repository = mock(ApplicationSettingsRepository.class);
        crypto = SensitiveSettingCrypto.fromBase64Key(TEST_KEY);
    }

    @Test
    void extractIosAppId_fromStandardAppStoreUrl() {
        String url = "https://apps.apple.com/us/app/teaneck-minyanim/id1234567890";
        assertEquals("1234567890", ApplicationSettingsService.extractIosAppId(url));
    }

    @Test
    void extractIosAppId_fromAppStoreUrlWithQueryParameters() {
        String url = "https://apps.apple.com/app/id987654321?mt=8";
        assertEquals("987654321", ApplicationSettingsService.extractIosAppId(url));
    }

    @Test
    void extractIosAppId_fromRawNumericId() {
        assertEquals("2468135790", ApplicationSettingsService.extractIosAppId("2468135790"));
    }

    @Test
    void extractIosAppId_returnsNullForInvalidInput() {
        assertNull(ApplicationSettingsService.extractIosAppId("https://apps.apple.com/us/app/teaneck-minyanim"));
        assertNull(ApplicationSettingsService.extractIosAppId("not-a-url"));
        assertNull(ApplicationSettingsService.extractIosAppId(""));
        assertNull(ApplicationSettingsService.extractIosAppId(null));
    }

    @Test
    void buildAppleSmartAppBannerContent_returnsExpectedValue() {
        String url = "https://apps.apple.com/us/app/teaneck-minyanim/id1234567890";
        assertEquals("app-id=1234567890", ApplicationSettingsService.buildAppleSmartAppBannerContent(url));
        assertNull(ApplicationSettingsService.buildAppleSmartAppBannerContent("https://apps.apple.com/us/app/no-id"));
    }

    @Test
    void updateSetting_encryptsSensitiveValuesBeforeSaving() throws Exception {
        ApplicationSettings setting = setting(SettingKey.EMAIL_SMTP_PASSWORD, "");
        when(repository.findBySettingKey(SettingKey.EMAIL_SMTP_PASSWORD.getKey()))
                .thenReturn(Optional.of(setting));
        when(repository.save(any(ApplicationSettings.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ApplicationSettingsService service = new ApplicationSettingsService(repository, crypto);
        service.updateSetting(SettingKey.EMAIL_SMTP_PASSWORD, "smtp-secret");

        ArgumentCaptor<ApplicationSettings> captor = ArgumentCaptor.forClass(ApplicationSettings.class);
        verify(repository).save(captor.capture());

        String storedValue = captor.getValue().getSettingValue();
        assertTrue(storedValue.startsWith(SensitiveSettingCrypto.ENCRYPTED_PREFIX));
        assertFalse(storedValue.contains("smtp-secret"));
        assertEquals("smtp-secret", crypto.decrypt(storedValue));
        assertEquals("smtp-secret", service.getString(SettingKey.EMAIL_SMTP_PASSWORD));
    }

    @Test
    void updateSetting_rejectsSensitiveValuesWhenEncryptionKeyIsMissing() {
        ApplicationSettings setting = setting(SettingKey.EMAIL_SMTP_PASSWORD, "");
        when(repository.findBySettingKey(SettingKey.EMAIL_SMTP_PASSWORD.getKey()))
                .thenReturn(Optional.of(setting));

        ApplicationSettingsService service = new ApplicationSettingsService(
                repository,
                SensitiveSettingCrypto.disabled());

        ApplicationSettingsService.ValidationException exception = assertThrows(
                ApplicationSettingsService.ValidationException.class,
                () -> service.updateSetting(SettingKey.EMAIL_SMTP_PASSWORD, "smtp-secret"));

        assertTrue(exception.getMessage().contains("APP_SETTINGS_ENCRYPTION_KEY"));
        verify(repository, never()).save(any(ApplicationSettings.class));
    }

    @Test
    void initializeDefaults_migratesExistingPlaintextSensitiveValuesWhenKeyIsConfigured() {
        ApplicationSettings smtpPassword = setting(SettingKey.EMAIL_SMTP_PASSWORD, "smtp-secret");
        stubExistingSettingsWith(smtpPassword);
        when(repository.save(any(ApplicationSettings.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ApplicationSettingsService service = new ApplicationSettingsService(repository, crypto);
        service.initializeDefaults();

        assertTrue(smtpPassword.getSettingValue().startsWith(SensitiveSettingCrypto.ENCRYPTED_PREFIX));
        assertEquals("smtp-secret", crypto.decrypt(smtpPassword.getSettingValue()));
        assertEquals("smtp-secret", service.getString(SettingKey.EMAIL_SMTP_PASSWORD));
        verify(repository).save(smtpPassword);
    }

    @Test
    void initializeDefaults_failsForPlaintextSensitiveValuesWhenEncryptionKeyIsMissing() {
        ApplicationSettings smtpPassword = setting(SettingKey.EMAIL_SMTP_PASSWORD, "smtp-secret");
        stubExistingSettingsWith(smtpPassword);

        ApplicationSettingsService service = new ApplicationSettingsService(
                repository,
                SensitiveSettingCrypto.disabled());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                service::initializeDefaults);

        assertTrue(exception.getMessage().contains("plaintext"));
        assertTrue(exception.getMessage().contains("APP_SETTINGS_ENCRYPTION_KEY"));
        verify(repository, never()).save(any(ApplicationSettings.class));
    }

    private void stubExistingSettingsWith(ApplicationSettings override) {
        when(repository.findBySettingKey(anyString())).thenAnswer(invocation -> {
            String keyString = invocation.getArgument(0);
            if (override.getSettingKey().equals(keyString)) {
                return Optional.of(override);
            }
            SettingKey key = SettingKey.fromKey(keyString);
            return Optional.of(setting(key, key.getDefaultValue()));
        });
    }

    private ApplicationSettings setting(SettingKey key, String value) {
        return new ApplicationSettings(key.getKey(), value, key.getType().name());
    }
}
