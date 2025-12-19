package com.tbdev.teaneckminyanim.schedule;

import com.tbdev.teaneckminyanim.front.MinyanEvent;
import com.tbdev.teaneckminyanim.model.Location;
import com.tbdev.teaneckminyanim.model.Minyan;
import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.service.LocationService;
import com.tbdev.teaneckminyanim.service.MinyanService;
import com.tbdev.teaneckminyanim.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class RuleBasedProvider implements OrgScheduleProvider {

    private final MinyanService minyanService;
    private final OrganizationService organizationService;
    private final LocationService locationService;

    @Override
    public List<MinyanEvent> getMinyanEvents(String organizationId, LocalDate date) {
        List<Minyan> minyanim = minyanService.findEnabledMatching(organizationId);

        Optional<Organization> orgOpt = organizationService.findById(organizationId);
        if (orgOpt.isEmpty()) {
            return List.of();
        }

        Organization org = orgOpt.get();

        return minyanim.stream()
                .map(minyan -> convertToMinyanEvent(minyan, org, date))
                .filter(event -> event != null && event.getStartTime() != null)
                .collect(Collectors.toList());
    }

    @Override
    public boolean canHandle(String organizationId) {
        // Can always handle - this is the fallback provider
        return true;
    }

    private MinyanEvent convertToMinyanEvent(Minyan minyan, Organization org, LocalDate date) {
        try {
            Date startTime = minyan.getStartDate(date);
            if (startTime == null) {
                return null;
            }

            Location location = locationService.findById(minyan.getLocationId());
            String locationName = location != null ? location.getName() : null;

            String dynamicTimeString = null;
            if (minyan.getMinyanTime(date) != null) {
                dynamicTimeString = minyan.getMinyanTime(date).dynamicDisplayName();
            }

            return new MinyanEvent(
                    minyan.getId(),
                    minyan.getType(),
                    org.getName(),
                    org.getNusach(),
                    org.getId(),
                    locationName,
                    startTime,
                    dynamicTimeString,
                    minyan.getNusach(),
                    minyan.getNotes(),
                    org.getOrgColor(),
                    minyan.getWhatsapp()
            );
        } catch (Exception e) {
            log.error("Failed to convert minyan to event: {}", minyan.getId(), e);
            return null;
        }
    }
}
