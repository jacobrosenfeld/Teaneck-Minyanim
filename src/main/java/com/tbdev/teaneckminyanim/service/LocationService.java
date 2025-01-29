package com.tbdev.teaneckminyanim.service;

import com.tbdev.teaneckminyanim.repo.LocationRepository;
import com.tbdev.teaneckminyanim.model.Location;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
public class LocationService {

    private final LocationRepository locationRepository;

    public Location findByName(String name) {
        return locationRepository.findByName(name);
    }

    public Location findById(String id) {
        return locationRepository.findById(id).orElse(null);
    }

    public List<Location> findMatching(String organizationId) {
        return locationRepository.findByOrganizationId(organizationId);
    }

    public List<Location> getAll() {
        return locationRepository.findAll();
    }

    public boolean save(Location location) {
        try {
            locationRepository.save(location);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public boolean delete(Location location) {
        try {
            locationRepository.delete(location);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public boolean update(Location location) {
        try {
            locationRepository.save(location);
        } catch (Exception e) {
            return false;
        }
        return true;
    }
}
