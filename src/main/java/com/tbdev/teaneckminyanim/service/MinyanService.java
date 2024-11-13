package com.tbdev.teaneckminyanim.service;

import com.tbdev.teaneckminyanim.enums.Nusach;
import com.tbdev.teaneckminyanim.repo.LocationRepository;
import com.tbdev.teaneckminyanim.repo.MinyanRepository;
import com.tbdev.teaneckminyanim.minyan.Schedule;
import com.tbdev.teaneckminyanim.model.Location;
import com.tbdev.teaneckminyanim.model.Minyan;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MinyanService {

    private final MinyanRepository minyanRepository;
    private final LocationRepository locationRepository;

    public Minyan findById(String id) {
        return minyanRepository.findById(id).orElse(null);
    }

    public List<Minyan> getAll() {
        return minyanRepository.findAll();
    }

    public List<Minyan> getEnabled() {
        List<Minyan> enabledMinyans = minyanRepository.findByEnabled();
        setupMinyanObjs(enabledMinyans);
        return enabledMinyans;
    }

    public Minyan save(Minyan minyan) {
        return minyanRepository.save(minyan);
    }

    public void delete(Minyan minyan) {
        minyanRepository.delete(minyan);
    }

    public Minyan update(Minyan minyan) {
        return minyanRepository.save(minyan);
    }

    public List<Minyan> findMatching(String organizationId) {
        List<Minyan> byOrganizationId = minyanRepository.findByOrganizationId(organizationId);
        setupMinyanObjs(byOrganizationId);
        return byOrganizationId;
    }

    public List<Minyan> findEnabledMatching(String organizationId) {
        List<Minyan> byOrgIdAndEnabled = minyanRepository.findByOrganizationIdAndEnabled(organizationId);
        setupMinyanObjs(byOrgIdAndEnabled);
        return byOrgIdAndEnabled;

    }

    public Location getMinyanLocationById(Minyan minyan) {
        return locationRepository.getById(minyan.getLocationId());
    }

    private void setupMinyanObjs(List<Minyan> minyans) {
        for (Minyan minyan : minyans) {
            minyan.setOrgColor("#275ed8");
            minyan.setNusach(Nusach.fromString(minyan.getNusachString()));
            minyan.setSchedule(new Schedule(minyan.getStartTime1(), minyan.getStartTime2(), minyan.getStartTime3(), minyan.getStartTime4(), minyan.getStartTime5(), minyan.getStartTime6(), minyan.getStartTime7(), minyan.getStartTimeRC(), minyan.getStartTimeYT(), minyan.getStartTimeCH(), minyan.getStartTimeCHRC()));
        }
    }
}

