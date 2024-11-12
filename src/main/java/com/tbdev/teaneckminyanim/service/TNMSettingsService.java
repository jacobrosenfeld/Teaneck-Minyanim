package com.tbdev.teaneckminyanim.service;

import com.tbdev.teaneckminyanim.repo.TNMSettingsRepository;
import com.tbdev.teaneckminyanim.model.TNMSettings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TNMSettingsService {

    private final TNMSettingsRepository repository;

    @Autowired
    public TNMSettingsService(TNMSettingsRepository repository) {
        this.repository = repository;
    }

    public TNMSettings findByName(String setting) {
        return repository.findBySetting(setting).orElse(null);
    }

    public TNMSettings findById(String id) {
        return repository.findById(id).orElse(null);
    }

    public List<TNMSettings> getAll() {
        return repository.findAll();
    }

    public TNMSettings save(TNMSettings setting) {
        return repository.save(setting);
    }

    public void delete(TNMSettings setting) {
        repository.delete(setting);
    }

    public boolean update(TNMSettings setting) {
        try {
            repository.save(setting);
        } catch (Exception e) {
            return false;
        }
        return true;
    }
}
