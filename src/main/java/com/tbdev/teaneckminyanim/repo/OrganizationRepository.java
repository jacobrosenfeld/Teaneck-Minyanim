package com.tbdev.teaneckminyanim.repo;

import com.tbdev.teaneckminyanim.model.Account;
import com.tbdev.teaneckminyanim.model.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface OrganizationRepository extends JpaRepository<Organization, String> {

    Optional<Organization> findByName(String name);
    @Query("SELECT a FROM Account a WHERE a.organizationId = :organizationId")
    List<Account> findAccountsByOrganizationId(@Param("organizationId") String organizationId);
}
