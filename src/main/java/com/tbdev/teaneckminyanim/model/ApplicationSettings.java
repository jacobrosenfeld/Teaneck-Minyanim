package com.tbdev.teaneckminyanim.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;

/**
 * Application-wide settings entity.
 * Stores configuration values that affect the entire application.
 * Use ApplicationSettingsService for type-safe access to settings.
 */
@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "APPLICATION_SETTINGS")
public class ApplicationSettings {

    @Id
    @Column(name = "SETTING_KEY", nullable = false, unique = true, length = 100)
    private String settingKey;

    @Column(name = "SETTING_VALUE", nullable = false, columnDefinition = "TEXT")
    private String settingValue;

    @Column(name = "SETTING_TYPE", nullable = false, length = 50)
    private String settingType;

    @Column(name = "DESCRIPTION", columnDefinition = "TEXT")
    private String description;

    @Column(name = "CATEGORY", length = 100)
    private String category;

    @Column(name = "VERSION", nullable = true)
    private Long version;

    /**
     * Constructor for creating a new setting.
     */
    public ApplicationSettings(String key, String value, String type) {
        this.settingKey = key;
        this.settingValue = value;
        this.settingType = type;
    }
}
