package com.tbdev.teaneckminyanim.repo;

import com.tbdev.teaneckminyanim.model.ApplicationSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ApplicationSettingsRepository extends JpaRepository<ApplicationSettings, String> {
    Optional<ApplicationSettings> findBySettingKey(String settingKey);
}
