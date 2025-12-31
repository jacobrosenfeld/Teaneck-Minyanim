package com.tbdev.teaneckminyanim.model;

import com.tbdev.teaneckminyanim.enums.EventSource;
import com.tbdev.teaneckminyanim.enums.Nusach;
import com.tbdev.teaneckminyanim.minyan.MinyanType;
import lombok.*;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Materialized calendar event - the single source of truth for displaying minyanim.
 * Events can come from three sources:
 * - IMPORTED: From external calendar imports (OrganizationCalendarEntry)
 * - RULES: Generated from rule-based minyan schedules (Minyan entity)
 * - MANUAL: Manually created/overridden by admin (future feature)
 * 
 * Precedence (day-level override):
 * - If any IMPORTED events exist for org+date, only IMPORTED events are shown
 * - Otherwise, RULES events are shown
 * - MANUAL events (future) will override both
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "calendar_events",
        indexes = {
                @Index(name = "idx_org_date", columnList = "organization_id,date"),
                @Index(name = "idx_org_date_type_time", columnList = "organization_id,date,minyan_type,start_time"),
                @Index(name = "idx_source_date", columnList = "source,date"),
                @Index(name = "idx_enabled", columnList = "enabled")
        })
public class CalendarEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private String organizationId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(name = "minyan_type", nullable = false)
    private MinyanType minyanType;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /**
     * Location ID reference (nullable for imported events without location mapping)
     */
    @Column(name = "location_id")
    private String locationId;

    /**
     * Location name (for display, especially for imported events)
     */
    @Column(name = "location_name")
    private String locationName;

    @Builder.Default
    @Column(name = "enabled", nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE")
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private EventSource source;

    /**
     * Reference to source entity:
     * - For IMPORTED: OrganizationCalendarEntry ID
     * - For RULES: Minyan ID
     * - For MANUAL: override record ID (future)
     */
    @Column(name = "source_ref")
    private String sourceRef;

    /**
     * Nusach for this specific event (may differ from organization default)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "nusach")
    private Nusach nusach;

    /**
     * WhatsApp group link for this minyan (from rule-based minyanim)
     */
    @Column(name = "whatsapp")
    private String whatsapp;

    /**
     * Dynamic time display string (e.g., "NETZ+5min") for rule-based events
     */
    @Column(name = "dynamic_time_string")
    private String dynamicTimeString;

    /**
     * Flag indicating if this event was manually edited after materialization
     */
    @Builder.Default
    @Column(name = "manually_edited", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean manuallyEdited = false;

    /**
     * User who last manually edited this event
     */
    @Column(name = "edited_by")
    private String editedBy;

    /**
     * Timestamp of last manual edit
     */
    @Column(name = "edited_at")
    private LocalDateTime editedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
