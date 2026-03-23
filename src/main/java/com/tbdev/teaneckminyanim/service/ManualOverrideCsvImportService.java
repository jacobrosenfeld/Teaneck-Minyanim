package com.tbdev.teaneckminyanim.service;

import com.tbdev.teaneckminyanim.enums.EventSource;
import com.tbdev.teaneckminyanim.enums.Nusach;
import com.tbdev.teaneckminyanim.minyan.MinyanType;
import com.tbdev.teaneckminyanim.model.CalendarEvent;
import com.tbdev.teaneckminyanim.model.Location;
import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.repo.CalendarEventRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManualOverrideCsvImportService {

    private static final String MODE_ADDITIVE = "ADDITIVE";
    private static final String MODE_FULL_DAY_REPLACE = "FULL_DAY_REPLACE";
    private static final String MANUAL_ADDITIVE_SOURCE_REF_PREFIX = "manual:ADDITIVE";

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("M/d/uuuu"),
            DateTimeFormatter.ofPattern("MM/dd/uuuu"),
            DateTimeFormatter.ofPattern("M-d-uuuu"),
            DateTimeFormatter.ofPattern("MM-dd-uuuu")
    );

    private static final List<DateTimeFormatter> TIME_FORMATTERS = List.of(
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("H:mm").toFormatter(Locale.US),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("HH:mm").toFormatter(Locale.US),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("h:mm a").toFormatter(Locale.US),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("h:mma").toFormatter(Locale.US)
    );

    private static final Set<String> TRUE_VALUES = Set.of("true", "1", "yes", "y");
    private static final Set<String> FALSE_VALUES = Set.of("false", "0", "no", "n");

    private final CalendarEventRepository calendarEventRepository;
    private final OrganizationService organizationService;
    private final LocationService locationService;

    @Transactional
    public ImportResult importCsv(String orgId, MultipartFile file, String username) {
        ImportResult result = new ImportResult();
        if (file == null || file.isEmpty()) {
            result.addError("No CSV file uploaded.");
            return result;
        }

        Optional<Organization> orgOpt = organizationService.findById(orgId);
        if (orgOpt.isEmpty()) {
            result.addError("Organization not found.");
            return result;
        }
        Organization org = orgOpt.get();

        List<Location> orgLocations = locationService.findMatching(orgId);
        Map<String, Location> locationsByLowerName = orgLocations.stream()
                .collect(Collectors.toMap(
                        l -> l.getName().trim().toLowerCase(Locale.US),
                        l -> l,
                        (a, b) -> a
                ));

        List<ParsedRow> parsedRows = new ArrayList<>();
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreHeaderCase(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            for (CSVRecord record : parser) {
                result.incrementRowsRead();
                try {
                    ParsedRow row = parseRow(record, org, locationsByLowerName);
                    parsedRows.add(row);
                } catch (RowParseException e) {
                    result.addError("Row " + record.getRecordNumber() + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("Failed reading manual override CSV for org {}", orgId, e);
            result.addError("Could not read CSV file.");
            return result;
        }

        if (parsedRows.isEmpty()) {
            if (!result.hasErrors()) {
                result.addError("CSV file contains no valid rows.");
            }
            return result;
        }

        // Full-day replace rows should replace all MANUAL rows on those dates.
        Set<LocalDate> fullDayReplaceDates = parsedRows.stream()
                .filter(row -> MODE_FULL_DAY_REPLACE.equals(row.overrideMode()))
                .map(ParsedRow::date)
                .collect(Collectors.toSet());

        long deletedCount = 0;
        for (LocalDate date : fullDayReplaceDates) {
            deletedCount += calendarEventRepository.deleteByOrganizationIdAndDateAndSource(
                    orgId, date, EventSource.MANUAL);
        }
        result.setDeletedManualCount(deletedCount);

        for (ParsedRow row : parsedRows) {
            try {
                upsertRow(orgId, username, row, result);
            } catch (Exception e) {
                result.addError("Row " + row.rowNumber() + ": failed to save (" + e.getMessage() + ")");
            }
        }

        return result;
    }

    private void upsertRow(String orgId, String username, ParsedRow row, ImportResult result) {
        Optional<CalendarEvent> existing = calendarEventRepository
                .findFirstByOrganizationIdAndDateAndMinyanTypeAndStartTimeAndSource(
                        orgId, row.date(), row.minyanType(), row.startTime(), EventSource.MANUAL);

        String sourceRefPrefix = MODE_FULL_DAY_REPLACE.equals(row.overrideMode())
                ? EffectiveScheduleService.MANUAL_FULL_DAY_SOURCE_REF_PREFIX
                : MANUAL_ADDITIVE_SOURCE_REF_PREFIX;

        CalendarEvent event = existing.orElseGet(() -> CalendarEvent.builder()
                .organizationId(orgId)
                .date(row.date())
                .minyanType(row.minyanType())
                .startTime(row.startTime())
                .source(EventSource.MANUAL)
                .build());

        event.setLocationId(row.locationId());
        event.setLocationName(row.locationName());
        event.setNotes(row.notes());
        event.setNusach(row.nusach());
        event.setEnabled(row.enabled());
        event.setSource(EventSource.MANUAL);
        event.setSourceRef(sourceRefPrefix + ":" + UUID.randomUUID());
        event.setManuallyEdited(true);
        event.setEditedBy(username);
        event.setEditedAt(LocalDateTime.now());
        event.setDynamicTimeString(null);
        event.setWhatsapp(null);

        calendarEventRepository.save(event);

        if (existing.isPresent()) {
            result.incrementUpdatedCount();
        } else {
            result.incrementCreatedCount();
        }
    }

    private ParsedRow parseRow(CSVRecord record, Organization org, Map<String, Location> locationsByLowerName) {
        String dateRaw = require(record, "date");
        String typeRaw = require(record, "minyan_type");
        String startTimeRaw = require(record, "start_time");

        LocalDate date = parseDate(dateRaw);
        MinyanType minyanType = parseMinyanType(typeRaw);
        LocalTime startTime = parseTime(startTimeRaw);

        String overrideMode = parseOverrideMode(get(record, "override_mode"));
        String notes = trimToNull(get(record, "notes"));
        Nusach nusach = parseNusach(get(record, "nusach"), org.getNusach());
        boolean enabled = parseEnabled(get(record, "enabled"));

        String locationRaw = trimToNull(get(record, "location"));
        String locationId = null;
        String locationName = null;
        if (locationRaw != null) {
            Location location = locationsByLowerName.get(locationRaw.toLowerCase(Locale.US));
            if (location != null) {
                locationId = location.getId();
                locationName = location.getName();
            } else {
                // Keep custom location text even if it doesn't match a known location record.
                locationName = locationRaw;
            }
        }

        return new ParsedRow(
                record.getRecordNumber(),
                date,
                minyanType,
                startTime,
                overrideMode,
                locationId,
                locationName,
                notes,
                nusach,
                enabled
        );
    }

    private String require(CSVRecord record, String key) {
        String value = trimToNull(get(record, key));
        if (value == null) {
            throw new RowParseException("Missing required column value: " + key);
        }
        return value;
    }

    private String get(CSVRecord record, String key) {
        if (!record.isMapped(key)) {
            return null;
        }
        return record.get(key);
    }

    private LocalDate parseDate(String raw) {
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(raw, formatter);
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        throw new RowParseException("Invalid date: " + raw + ". Use YYYY-MM-DD.");
    }

    private LocalTime parseTime(String raw) {
        for (DateTimeFormatter formatter : TIME_FORMATTERS) {
            try {
                return LocalTime.parse(raw, formatter);
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        throw new RowParseException("Invalid start_time: " + raw + ". Use HH:mm or h:mm AM/PM.");
    }

    private MinyanType parseMinyanType(String raw) {
        String normalized = raw.trim().toUpperCase(Locale.US)
                .replace('-', '_')
                .replace('/', '_')
                .replace(' ', '_');

        if ("MINCHA_MARIV".equals(normalized)) {
            normalized = "MINCHA_MAARIV";
        }
        if ("MEGILLA_READING".equals(normalized)) {
            normalized = "MEGILA_READING";
        }

        try {
            return MinyanType.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new RowParseException("Invalid minyan_type: " + raw);
        }
    }

    private Nusach parseNusach(String raw, Nusach defaultNusach) {
        String value = trimToNull(raw);
        if (value == null) return defaultNusach;

        String normalized = value.trim().toUpperCase(Locale.US)
                .replace('-', '_')
                .replace(' ', '_');
        try {
            return Nusach.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new RowParseException("Invalid nusach: " + raw);
        }
    }

    private boolean parseEnabled(String raw) {
        String value = trimToNull(raw);
        if (value == null) return true;

        String normalized = value.toLowerCase(Locale.US);
        if (TRUE_VALUES.contains(normalized)) return true;
        if (FALSE_VALUES.contains(normalized)) return false;
        throw new RowParseException("Invalid enabled value: " + raw + ". Use true/false.");
    }

    private String parseOverrideMode(String raw) {
        String value = trimToNull(raw);
        if (value == null) return MODE_ADDITIVE;

        String normalized = value.toUpperCase(Locale.US)
                .replace('-', '_')
                .replace(' ', '_');

        if ("FULLDAY".equals(normalized) || "FULL_DAY".equals(normalized) || "REPLACE".equals(normalized)) {
            return MODE_FULL_DAY_REPLACE;
        }
        if (MODE_FULL_DAY_REPLACE.equals(normalized)) return MODE_FULL_DAY_REPLACE;
        if (MODE_ADDITIVE.equals(normalized)) return MODE_ADDITIVE;

        throw new RowParseException("Invalid override_mode: " + raw + ". Use ADDITIVE or FULL_DAY_REPLACE.");
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record ParsedRow(
            long rowNumber,
            LocalDate date,
            MinyanType minyanType,
            LocalTime startTime,
            String overrideMode,
            String locationId,
            String locationName,
            String notes,
            Nusach nusach,
            boolean enabled
    ) {}

    private static class RowParseException extends RuntimeException {
        RowParseException(String message) {
            super(message);
        }
    }

    @Getter
    public static class ImportResult {
        private int rowsRead;
        private int createdCount;
        private int updatedCount;
        private long deletedManualCount;
        private final List<String> errors = new ArrayList<>();

        public void incrementRowsRead() {
            this.rowsRead++;
        }

        public void incrementCreatedCount() {
            this.createdCount++;
        }

        public void incrementUpdatedCount() {
            this.updatedCount++;
        }

        public void setDeletedManualCount(long deletedManualCount) {
            this.deletedManualCount = deletedManualCount;
        }

        public void addError(String error) {
            this.errors.add(error);
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }
}
