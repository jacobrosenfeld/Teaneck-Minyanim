package com.tbdev.teaneckminyanim.repo;

import com.tbdev.teaneckminyanim.model.OrganizationCalendarEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrganizationCalendarEntryRepository extends JpaRepository<OrganizationCalendarEntry, Long> {

    /**
     * Find all enabled entries for an organization on a specific date
     */
    List<OrganizationCalendarEntry> findByOrganizationIdAndDateAndEnabledTrue(
            String organizationId, LocalDate date);

    /**
     * Find all entries (enabled and disabled) for an organization on a specific date
     */
    List<OrganizationCalendarEntry> findByOrganizationIdAndDate(
            String organizationId, LocalDate date);

    /**
     * Find all enabled entries for an organization within a date range
     */
    @Query("SELECT e FROM OrganizationCalendarEntry e WHERE e.organizationId = :orgId " +
            "AND e.date BETWEEN :startDate AND :endDate AND e.enabled = true ORDER BY e.date, e.startTime")
    List<OrganizationCalendarEntry> findEnabledEntriesInRange(
            @Param("orgId") String organizationId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Find all entries for an organization within a date range
     */
    @Query("SELECT e FROM OrganizationCalendarEntry e WHERE e.organizationId = :orgId " +
            "AND e.date BETWEEN :startDate AND :endDate ORDER BY e.date, e.startTime")
    List<OrganizationCalendarEntry> findEntriesInRange(
            @Param("orgId") String organizationId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Find entry by fingerprint
     */
    Optional<OrganizationCalendarEntry> findByFingerprint(String fingerprint);

    /**
     * Find all entries for an organization
     */
    List<OrganizationCalendarEntry> findByOrganizationIdOrderByDateDesc(String organizationId);

    /**
     * Delete all entries for an organization older than a specific date
     */
    void deleteByOrganizationIdAndDateBefore(String organizationId, LocalDate date);

    /**
     * Count enabled entries for an organization
     */
    long countByOrganizationIdAndEnabledTrue(String organizationId);

    /**
     * Count all entries for an organization
     */
    long countByOrganizationId(String organizationId);
}
