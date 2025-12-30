package com.tbdev.teaneckminyanim.repo;

import com.tbdev.teaneckminyanim.enums.EventSource;
import com.tbdev.teaneckminyanim.minyan.MinyanType;
import com.tbdev.teaneckminyanim.model.CalendarEvent;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

    /**
     * Find all enabled events for an organization on a specific date
     */
    List<CalendarEvent> findByOrganizationIdAndDateAndEnabledTrue(
            String organizationId, LocalDate date);

    /**
     * Find all events (enabled and disabled) for an organization on a specific date
     */
    List<CalendarEvent> findByOrganizationIdAndDate(
            String organizationId, LocalDate date);

    /**
     * Find all enabled events for an organization within a date range, ordered by date and time
     */
    @Query("SELECT e FROM CalendarEvent e WHERE e.organizationId = :orgId " +
            "AND e.date BETWEEN :startDate AND :endDate AND e.enabled = true " +
            "ORDER BY e.date, e.startTime")
    List<CalendarEvent> findEnabledEventsInRange(
            @Param("orgId") String organizationId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Find all events for an organization within a date range
     */
    @Query("SELECT e FROM CalendarEvent e WHERE e.organizationId = :orgId " +
            "AND e.date BETWEEN :startDate AND :endDate " +
            "ORDER BY e.date, e.startTime")
    List<CalendarEvent> findEventsInRange(
            @Param("orgId") String organizationId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Check if any IMPORTED events exist for org+date (for precedence logic)
     */
    @Query("SELECT COUNT(e) > 0 FROM CalendarEvent e WHERE e.organizationId = :orgId " +
            "AND e.date = :date AND e.source = :source AND e.enabled = true")
    boolean existsByOrganizationIdAndDateAndSourceAndEnabledTrue(
            @Param("orgId") String organizationId,
            @Param("date") LocalDate date,
            @Param("source") EventSource source);

    /**
     * Get effective events for a date (applying precedence: imported overrides rules)
     * Returns IMPORTED events if any exist, otherwise RULES events
     */
    @Query("SELECT e FROM CalendarEvent e WHERE e.organizationId = :orgId " +
            "AND e.date = :date AND e.enabled = true " +
            "AND e.source = CASE " +
            "  WHEN EXISTS(SELECT 1 FROM CalendarEvent imp " +
            "              WHERE imp.organizationId = :orgId " +
            "              AND imp.date = :date " +
            "              AND imp.source = 'IMPORTED' " +
            "              AND imp.enabled = true) " +
            "  THEN 'IMPORTED' " +
            "  ELSE e.source " +
            "END " +
            "ORDER BY e.startTime")
    List<CalendarEvent> findEffectiveEventsForDate(
            @Param("orgId") String organizationId,
            @Param("date") LocalDate date);

    /**
     * Find all events by source and date range
     */
    List<CalendarEvent> findBySourceAndDateBetween(
            EventSource source, LocalDate startDate, LocalDate endDate);

    /**
     * Find events by organization, source, and date range
     */
    List<CalendarEvent> findByOrganizationIdAndSourceAndDateBetween(
            String organizationId, EventSource source, LocalDate startDate, LocalDate endDate);

    /**
     * Delete all RULES events for an organization in a date range
     * (used for delete+rebuild strategy)
     */
    @Modifying
    @Query("DELETE FROM CalendarEvent e WHERE e.organizationId = :orgId " +
            "AND e.source = 'RULES' " +
            "AND e.date BETWEEN :startDate AND :endDate")
    void deleteRulesEventsInRange(
            @Param("orgId") String organizationId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Delete all events older than a specific date (cleanup)
     */
    @Modifying
    @Query("DELETE FROM CalendarEvent e WHERE e.date < :cutoffDate")
    void deleteEventsBeforeDate(@Param("cutoffDate") LocalDate cutoffDate);

    /**
     * Find events by organization with sorting
     */
    List<CalendarEvent> findByOrganizationId(String organizationId, Sort sort);

    /**
     * Find events by organization and source with sorting
     */
    List<CalendarEvent> findByOrganizationIdAndSource(
            String organizationId, EventSource source, Sort sort);

    /**
     * Find events by organization and minyan type with sorting
     */
    List<CalendarEvent> findByOrganizationIdAndMinyanType(
            String organizationId, MinyanType minyanType, Sort sort);

    /**
     * Find events by organization, filtering by enabled status
     */
    List<CalendarEvent> findByOrganizationIdAndEnabled(
            String organizationId, boolean enabled, Sort sort);

    /**
     * Count events by organization and date range
     */
    @Query("SELECT COUNT(e) FROM CalendarEvent e WHERE e.organizationId = :orgId " +
            "AND e.date BETWEEN :startDate AND :endDate")
    long countEventsInRange(
            @Param("orgId") String organizationId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Count enabled events by organization
     */
    long countByOrganizationIdAndEnabledTrue(String organizationId);

    /**
     * Count events by organization and source
     */
    long countByOrganizationIdAndSource(String organizationId, EventSource source);
}
