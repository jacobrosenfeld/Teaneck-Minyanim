package com.tbdev.teaneckminyanim.repo;

import com.tbdev.teaneckminyanim.structure.model.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LocationRepository extends JpaRepository<Location, String> {
    Location findByName(String name);
    List<Location> findByOrganizationId(String organizationId);
}
