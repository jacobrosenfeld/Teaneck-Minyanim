package com.tbdev.teaneckminyanim.model;

import com.tbdev.teaneckminyanim.minyan.MinyanType;
import lombok.*;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "organization_calendar_entry",
       indexes = {
           @Index(name = "idx_org_date", columnList = "organization_id,date"),
           @Index(name = "idx_org_date_enabled", columnList = "organization_id,date,enabled"),
           @Index(name = "idx_fingerprint", columnList = "fingerprint")
       })
public class OrganizationCalendarEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "ORGANIZATION_ID", nullable = false)
    private String organizationId;

    @Column(name = "DATE", nullable = false)
    private LocalDate date;

    @Column(name = "TITLE", nullable = false, length = 500)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "TYPE")
    private MinyanType type;

    @Column(name = "TIME", nullable = false)
    private LocalTime time;

    @Column(name = "RAW_TEXT", columnDefinition = "TEXT")
    private String rawText;

    @Column(name = "SOURCE_URL", length = 2000)
    private String sourceUrl;

    @Column(name = "FINGERPRINT", nullable = false, unique = true, length = 64)
    private String fingerprint;

    @Column(name = "ENABLED", nullable = false)
    private boolean enabled = true;

    @Column(name = "SCRAPED_AT", nullable = false)
    private LocalDateTime scrapedAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "DEDUPE_REASON", length = 500)
    private String dedupeReason;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (scrapedAt == null) {
            scrapedAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
