package com.tbdev.teaneckminyanim.model;

import com.tbdev.teaneckminyanim.enums.MinyanClassification;
import lombok.*;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Represents a calendar entry imported from an organization's calendar CSV export.
 * These entries override rule-based minyan generation when enabled.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "organization_calendar_entry",
        indexes = {
                @Index(name = "idx_org_date", columnList = "organization_id,date"),
                @Index(name = "idx_org_enabled_date", columnList = "organization_id,enabled,date")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_fingerprint", columnNames = {"fingerprint"})
        })
public class OrganizationCalendarEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private String organizationId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "start_datetime")
    private LocalDateTime startDatetime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "end_datetime")
    private LocalDateTime endDatetime;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "type")
    private String type;

    @Column(name = "name")
    private String name;

    @Column(name = "location")
    private String location;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "hebrew_date")
    private String hebrewDate;

    @Column(name = "raw_text", columnDefinition = "TEXT")
    private String rawText;

    @Column(name = "source_url", columnDefinition = "TEXT")
    private String sourceUrl;

    /**
     * Fingerprint for deduplication: hash of org_id + date + normalized_title + normalized_start_time
     */
    @Column(name = "fingerprint", nullable = false, unique = true)
    private String fingerprint;

    @Column(name = "enabled", nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE")
    private boolean enabled = true;

    @Column(name = "duplicate_reason")
    private String duplicateReason;

    /**
     * Classification of this entry (MINYAN, MINCHA_MAARIV, NON_MINYAN, OTHER)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "classification")
    private MinyanClassification classification;

    /**
     * Explanation of why this entry was classified as it was
     */
    @Column(name = "classification_reason", columnDefinition = "TEXT")
    private String classificationReason;

    /**
     * Additional notes (e.g., Shkiya time for Mincha/Maariv events)
     */
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "imported_at", nullable = false)
    private LocalDateTime importedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "scraped_at", nullable = false)
    private LocalDateTime scrapedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        importedAt = now;
        updatedAt = now;
        scrapedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
