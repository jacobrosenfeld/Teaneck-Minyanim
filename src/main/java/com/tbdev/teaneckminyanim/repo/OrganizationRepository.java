package com.tbdev.teaneckminyanim.repo;

import com.tbdev.teaneckminyanim.model.Account;
import com.tbdev.teaneckminyanim.model.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;


@Repository
public interface OrganizationRepository extends JpaRepository<Organization, String> {

    Optional<Organization> findByName(String name);

    Optional<Organization> findByUrlSlug(String urlSlug);

    long countByEnabled(Boolean enabled);

    @Query("SELECT a FROM Account a WHERE a.organizationId = :organizationId")
    List<Account> findAccountsByOrganizationId(@Param("organizationId") String organizationId);

    @Modifying
    @Transactional
    @Query("UPDATE Organization o SET o.latitude = :lat, o.longitude = :lng WHERE o.id = :id")
    int updateGeocode(@Param("id") String id, @Param("lat") Double lat, @Param("lng") Double lng);
}
