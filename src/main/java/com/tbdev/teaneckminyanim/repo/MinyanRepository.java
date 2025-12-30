package com.tbdev.teaneckminyanim.repo;

import com.tbdev.teaneckminyanim.model.Minyan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MinyanRepository extends JpaRepository<Minyan, String> {
    List<Minyan> findByOrganizationId(String organizationId);
    @Query("SELECT m FROM Minyan m WHERE m.enabled = true")
    List<Minyan> findByEnabled();

    @Query("SELECT m FROM Minyan m WHERE m.enabled = true AND m.organizationId=:organizationId")
    List<Minyan> findByOrganizationIdAndEnabled(String organizationId);
}
