package com.tbdev.teaneckminyanim.repo;

import com.tbdev.teaneckminyanim.model.TNMSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TNMSettingsRepository extends JpaRepository<TNMSettings, String> {
    Optional<TNMSettings> findBySetting(String setting);
}
