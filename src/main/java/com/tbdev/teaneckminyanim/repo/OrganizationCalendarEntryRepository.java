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

    List<OrganizationCalendarEntry> findByOrganizationIdAndDateAndEnabled(String organizationId, LocalDate date, boolean enabled);

    List<OrganizationCalendarEntry> findByOrganizationIdAndDateBetween(String organizationId, LocalDate startDate, LocalDate endDate);

    List<OrganizationCalendarEntry> findByOrganizationIdAndDateBetweenAndEnabled(String organizationId, LocalDate startDate, LocalDate endDate, boolean enabled);

    Optional<OrganizationCalendarEntry> findByFingerprint(String fingerprint);

    @Query("SELECT e FROM OrganizationCalendarEntry e WHERE e.organizationId = :orgId AND e.date >= :startDate ORDER BY e.date, e.time")
    List<OrganizationCalendarEntry> findUpcomingEntriesForOrg(@Param("orgId") String orgId, @Param("startDate") LocalDate startDate);

    @Query("SELECT e FROM OrganizationCalendarEntry e WHERE e.organizationId = :orgId AND e.enabled = true AND e.date >= :startDate ORDER BY e.date, e.time")
    List<OrganizationCalendarEntry> findEnabledUpcomingEntriesForOrg(@Param("orgId") String orgId, @Param("startDate") LocalDate startDate);

    void deleteByOrganizationIdAndDateBefore(String organizationId, LocalDate date);
}
