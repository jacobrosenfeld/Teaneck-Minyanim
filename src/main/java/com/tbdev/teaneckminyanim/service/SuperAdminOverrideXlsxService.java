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
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
public class SuperAdminOverrideXlsxService {

    // Super-admin template (organization_name + common override fields)
    private static final int SUPER_COL_ORG_NAME = 0;
    private static final int SUPER_COL_DATE = 1;
    private static final int SUPER_COL_TIME = 2;
    private static final int SUPER_COL_MINYAN_TYPE = 3;
    private static final int SUPER_COL_OVERRIDE_MODE = 4;
    private static final int SUPER_COL_LOCATION = 5;
    private static final int SUPER_COL_NOTES = 6;
    private static final int SUPER_COL_NUSACH = 7;
    private static final int SUPER_COL_ENABLED = 8;

    // Org template (same fields, no organization column)
    private static final int ORG_COL_DATE = 0;
    private static final int ORG_COL_END_DATE = 1;
    private static final int ORG_COL_TIME = 2;
    private static final int ORG_COL_MINYAN_TYPE = 3;
    private static final int ORG_COL_OVERRIDE_MODE = 4;
    private static final int ORG_COL_LOCATION = 5;
    private static final int ORG_COL_NOTES = 6;
    private static final int ORG_COL_NUSACH = 7;
    private static final int ORG_COL_ENABLED = 8;

    private static final String MODE_ADDITIVE = "ADDITIVE";
    private static final String MODE_FULL_DAY_REPLACE = "FULL_DAY_REPLACE";
    private static final String MANUAL_ADDITIVE_SOURCE_REF_PREFIX = "manual:ADDITIVE";

    private static final String[] MINYAN_TYPE_VALUES = {"SHACHARIS", "MINCHA", "MAARIV", "MINCHA/MAARIV"};
    private static final String[] OVERRIDE_MODE_VALUES = {"ADDITIVE", "FULL_DAY_REPLACE"};
    private static final String[] ENABLED_VALUES = {"true", "false"};

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

    // ---------------------------------------------------------------------
    // Public template APIs
    // ---------------------------------------------------------------------

    /**
     * Backward-compatible wrapper for existing callers.
     */
    public byte[] buildTemplate(List<Organization> organizations) throws IOException {
        return buildSuperAdminTemplate(organizations);
    }

    public byte[] buildSuperAdminTemplate(List<Organization> organizations) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Overrides");
            Sheet lists = workbook.createSheet("Lists");

            createSuperHeaderRow(workbook, sheet);
            createSuperListSheet(lists, organizations);
            createSuperDataValidations(workbook, sheet, organizations.size());
            formatColumns(
                    sheet,
                    workbook,
                    new int[]{5200, 4200, 3400, 4800, 5000, 7000, 10000, 4500, 3200},
                    SUPER_COL_DATE,
                    SUPER_COL_TIME
            );
            seedSuperExampleRows(sheet, workbook, organizations);

            workbook.setSheetHidden(workbook.getSheetIndex(lists), true);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    public byte[] buildOrganizationTemplate(Organization organization, List<Location> locations) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Overrides");
            Sheet lists = workbook.createSheet("Lists");

            createOrgHeaderRow(workbook, sheet);
            createOrgListSheet(lists, locations == null ? List.of() : locations);
            createOrgDataValidations(workbook, sheet, locations == null ? 0 : locations.size());
            formatColumns(
                    sheet,
                    workbook,
                    new int[]{4200, 4200, 3400, 4800, 5000, 7000, 10000, 4500, 3200},
                    ORG_COL_DATE,
                    ORG_COL_TIME
            );
            seedOrgExampleRows(sheet, workbook, organization);

            workbook.setSheetHidden(workbook.getSheetIndex(lists), true);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    // ---------------------------------------------------------------------
    // Public import APIs
    // ---------------------------------------------------------------------

    /**
     * Backward-compatible wrapper for existing callers.
     */
    @Transactional
    public ImportResult importWorkbook(MultipartFile file, String username) {
        return importSuperAdminWorkbook(file, username);
    }

    @Transactional
    public ImportResult importSuperAdminWorkbook(MultipartFile file, String username) {
        ImportResult result = new ImportResult();
        if (file == null || file.isEmpty()) {
            result.addError("No XLSX file uploaded.");
            return result;
        }

        List<Organization> organizations = organizationService.getAll();
        Map<String, List<Organization>> orgsByNameLower = organizations.stream()
                .collect(Collectors.groupingBy(o -> normalizeOrgName(o.getName())));
        Map<String, Map<String, Location>> locationsByOrgAndNameLower = buildLocationLookupByOrg(organizations);

        List<ParsedRow> parsedRows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheet("Overrides");
            if (sheet == null) {
                sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            }
            if (sheet == null) {
                result.addError("Workbook is empty.");
                return result;
            }

            int lastRow = sheet.getLastRowNum();
            for (int i = 1; i <= lastRow; i++) {
                Row row = sheet.getRow(i);
                if (row == null || isRowBlank(row, formatter, SUPER_COL_ORG_NAME, SUPER_COL_ENABLED)) {
                    continue;
                }

                result.incrementRowsRead();
                try {
                    ParsedRow parsedRow = parseSuperRow(row, formatter, orgsByNameLower, locationsByOrgAndNameLower);
                    parsedRows.add(parsedRow);
                } catch (RowParseException e) {
                    result.addError("Row " + (i + 1) + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed importing super-admin override workbook", e);
            result.addError("Could not read XLSX file.");
            return result;
        }

        applyFullDayDeletes(parsedRows, result);
        applyUpserts(parsedRows, username, result);
        return finalizeResult(parsedRows, result, "No valid rows found in workbook.");
    }

    @Transactional
    public ImportResult importOrganizationWorkbook(String orgId, MultipartFile file, String username) {
        ImportResult result = new ImportResult();
        if (file == null || file.isEmpty()) {
            result.addError("No XLSX file uploaded.");
            return result;
        }

        Optional<Organization> orgOpt = organizationService.findById(orgId);
        if (orgOpt.isEmpty()) {
            result.addError("Organization not found.");
            return result;
        }
        Organization organization = orgOpt.get();

        Map<String, Location> orgLocationsByLowerName = locationService.findMatching(orgId).stream()
                .filter(l -> l.getName() != null && !l.getName().trim().isEmpty())
                .collect(Collectors.toMap(
                        l -> l.getName().trim().toLowerCase(Locale.US),
                        l -> l,
                        (a, b) -> a));

        List<ParsedRow> parsedRows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheet("Overrides");
            if (sheet == null) {
                sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            }
            if (sheet == null) {
                result.addError("Workbook is empty.");
                return result;
            }

            int lastRow = sheet.getLastRowNum();
            for (int i = 1; i <= lastRow; i++) {
                Row row = sheet.getRow(i);
                if (row == null || isRowBlank(row, formatter, ORG_COL_DATE, ORG_COL_ENABLED)) {
                    continue;
                }

                result.incrementRowsRead();
                try {
                    ParsedOrgRow parsedRow = parseOrgRow(row, formatter, organization, orgLocationsByLowerName);
                    for (LocalDate d = parsedRow.startDate(); !d.isAfter(parsedRow.endDate()); d = d.plusDays(1)) {
                        parsedRows.add(new ParsedRow(
                                parsedRow.rowNumber(),
                                parsedRow.organizationId(),
                                d,
                                parsedRow.startTime(),
                                parsedRow.minyanType(),
                                parsedRow.overrideMode(),
                                parsedRow.locationId(),
                                parsedRow.locationName(),
                                parsedRow.notes(),
                                parsedRow.nusach(),
                                parsedRow.enabled()
                        ));
                    }
                } catch (RowParseException e) {
                    result.addError("Row " + (i + 1) + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed importing organization override workbook for {}", orgId, e);
            result.addError("Could not read XLSX file.");
            return result;
        }

        applyFullDayDeletes(parsedRows, result);
        applyUpserts(parsedRows, username, result);
        return finalizeResult(parsedRows, result, "No valid rows found in workbook.");
    }

    // ---------------------------------------------------------------------
    // Manual upsert API (used by form-based create flows)
    // ---------------------------------------------------------------------

    @Transactional
    public boolean upsertManualEvent(
            String organizationId,
            LocalDate date,
            LocalTime startTime,
            MinyanType minyanType,
            String overrideMode,
            String locationId,
            String locationName,
            String notes,
            Nusach nusach,
            boolean enabled,
            String username) {

        Optional<CalendarEvent> existing = calendarEventRepository
                .findFirstByOrganizationIdAndDateAndMinyanTypeAndStartTimeAndSource(
                        organizationId, date, minyanType, startTime, EventSource.MANUAL);

        String sourceRefPrefix = MODE_FULL_DAY_REPLACE.equals(overrideMode)
                ? EffectiveScheduleService.MANUAL_FULL_DAY_SOURCE_REF_PREFIX
                : MANUAL_ADDITIVE_SOURCE_REF_PREFIX;

        CalendarEvent event = existing.orElseGet(() -> CalendarEvent.builder()
                .organizationId(organizationId)
                .date(date)
                .minyanType(minyanType)
                .startTime(startTime)
                .source(EventSource.MANUAL)
                .build());

        event.setLocationId(locationId);
        event.setLocationName(locationName);
        event.setNotes(notes);
        event.setNusach(nusach);
        event.setEnabled(enabled);
        event.setSource(EventSource.MANUAL);
        event.setSourceRef(sourceRefPrefix + ":" + UUID.randomUUID());
        event.setManuallyEdited(true);
        event.setEditedBy(username);
        event.setEditedAt(LocalDateTime.now());
        event.setDynamicTimeString(null);
        event.setWhatsapp(null);

        calendarEventRepository.save(event);
        return existing.isPresent();
    }

    // ---------------------------------------------------------------------
    // Shared import helpers
    // ---------------------------------------------------------------------

    private ImportResult finalizeResult(List<ParsedRow> parsedRows, ImportResult result, String noRowsMessage) {
        if (parsedRows.isEmpty() && !result.hasErrors()) {
            result.addError(noRowsMessage);
        }
        return result;
    }

    private void applyFullDayDeletes(List<ParsedRow> parsedRows, ImportResult result) {
        Map<String, Set<LocalDate>> fullReplaceMap = new HashMap<>();
        for (ParsedRow row : parsedRows) {
            if (MODE_FULL_DAY_REPLACE.equals(row.overrideMode())) {
                fullReplaceMap.computeIfAbsent(row.organizationId(), k -> new HashSet<>()).add(row.date());
            }
        }

        long deletedCount = 0;
        for (Map.Entry<String, Set<LocalDate>> entry : fullReplaceMap.entrySet()) {
            for (LocalDate date : entry.getValue()) {
                deletedCount += calendarEventRepository.deleteByOrganizationIdAndDateAndSource(
                        entry.getKey(), date, EventSource.MANUAL);
            }
        }
        result.setDeletedManualCount(deletedCount);
    }

    private void applyUpserts(List<ParsedRow> parsedRows, String username, ImportResult result) {
        for (ParsedRow row : parsedRows) {
            try {
                boolean updated = upsertManualEvent(
                        row.organizationId(),
                        row.date(),
                        row.startTime(),
                        row.minyanType(),
                        row.overrideMode(),
                        row.locationId(),
                        row.locationName(),
                        row.notes(),
                        row.nusach(),
                        row.enabled(),
                        username
                );

                if (updated) {
                    result.incrementUpdatedCount();
                } else {
                    result.incrementCreatedCount();
                }
            } catch (Exception e) {
                result.addError("Row " + row.rowNumber() + ": failed to save (" + e.getMessage() + ")");
            }
        }
    }

    private ParsedRow parseSuperRow(
            Row row,
            DataFormatter formatter,
            Map<String, List<Organization>> orgsByNameLower,
            Map<String, Map<String, Location>> locationsByOrgAndNameLower) {

        String orgNameRaw = readRequiredCell(row, SUPER_COL_ORG_NAME, formatter, "organization_name");
        Organization org = resolveOrganizationByName(orgNameRaw, orgsByNameLower);

        CommonParsedFields fields = parseCommonFields(
                row,
                formatter,
                org.getNusach(),
                locationsByOrgAndNameLower.getOrDefault(org.getId(), Map.of()),
                SUPER_COL_DATE,
                SUPER_COL_TIME,
                SUPER_COL_MINYAN_TYPE,
                SUPER_COL_OVERRIDE_MODE,
                SUPER_COL_LOCATION,
                SUPER_COL_NOTES,
                SUPER_COL_NUSACH,
                SUPER_COL_ENABLED
        );

        return new ParsedRow(
                row.getRowNum() + 1L,
                org.getId(),
                fields.date(),
                fields.startTime(),
                fields.minyanType(),
                fields.overrideMode(),
                fields.locationId(),
                fields.locationName(),
                fields.notes(),
                fields.nusach(),
                fields.enabled()
        );
    }

    private ParsedOrgRow parseOrgRow(
            Row row,
            DataFormatter formatter,
            Organization org,
            Map<String, Location> orgLocationsByLowerName) {

        CommonParsedFields fields = parseCommonFields(
                row,
                formatter,
                org.getNusach(),
                orgLocationsByLowerName,
                ORG_COL_DATE,
                ORG_COL_TIME,
                ORG_COL_MINYAN_TYPE,
                ORG_COL_OVERRIDE_MODE,
                ORG_COL_LOCATION,
                ORG_COL_NOTES,
                ORG_COL_NUSACH,
                ORG_COL_ENABLED
        );

        LocalDate endDate = parseOptionalDateCell(row.getCell(ORG_COL_END_DATE), formatter, fields.date());
        if (endDate.isBefore(fields.date())) {
            throw new RowParseException("end_date cannot be before date.");
        }

        return new ParsedOrgRow(
                row.getRowNum() + 1L,
                org.getId(),
                fields.date(),
                endDate,
                fields.startTime(),
                fields.minyanType(),
                fields.overrideMode(),
                fields.locationId(),
                fields.locationName(),
                fields.notes(),
                fields.nusach(),
                fields.enabled()
        );
    }

    private CommonParsedFields parseCommonFields(
            Row row,
            DataFormatter formatter,
            Nusach defaultNusach,
            Map<String, Location> orgLocationsByLowerName,
            int colDate,
            int colTime,
            int colMinyanType,
            int colOverrideMode,
            int colLocation,
            int colNotes,
            int colNusach,
            int colEnabled) {

        LocalDate date = parseDateCell(row.getCell(colDate), formatter);
        LocalTime time = parseTimeCell(row.getCell(colTime), formatter);
        MinyanType minyanType = parseMinyanType(readRequiredCell(row, colMinyanType, formatter, "minyan_type"));
        String overrideMode = parseOverrideMode(readCellAsString(row, colOverrideMode, formatter));
        String locationRaw = trimToNull(readCellAsString(row, colLocation, formatter));
        String notes = trimToNull(readCellAsString(row, colNotes, formatter));
        Nusach nusach = parseNusach(readCellAsString(row, colNusach, formatter), defaultNusach);
        boolean enabled = parseEnabled(readCellAsString(row, colEnabled, formatter));

        String locationId = null;
        String locationName = null;
        if (locationRaw != null) {
            Location match = orgLocationsByLowerName.get(locationRaw.toLowerCase(Locale.US));
            if (match != null) {
                locationId = match.getId();
                locationName = match.getName();
            } else {
                locationName = locationRaw;
            }
        }

        return new CommonParsedFields(
                date,
                time,
                minyanType,
                overrideMode,
                locationId,
                locationName,
                notes,
                nusach,
                enabled
        );
    }

    // ---------------------------------------------------------------------
    // Template builders
    // ---------------------------------------------------------------------

    private void createSuperHeaderRow(Workbook workbook, Sheet sheet) {
        createHeaderRow(workbook, sheet, new String[]{
                "organization_name", "date", "start_time", "minyan_type",
                "override_mode", "location", "notes", "nusach", "enabled"
        });
    }

    private void createOrgHeaderRow(Workbook workbook, Sheet sheet) {
        createHeaderRow(workbook, sheet, new String[]{
                "date", "end_date", "start_time", "minyan_type", "override_mode",
                "location", "notes", "nusach", "enabled"
        });
    }

    private void createHeaderRow(Workbook workbook, Sheet sheet, String[] headers) {
        Row header = sheet.createRow(0);

        CellStyle headerStyle = workbook.createCellStyle();
        Font boldFont = workbook.createFont();
        boldFont.setBold(true);
        headerStyle.setFont(boldFont);

        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private void createSuperListSheet(Sheet listSheet, List<Organization> organizations) {
        List<Organization> sortedOrgs = new ArrayList<>(organizations);
        sortedOrgs.sort(Comparator.comparing(Organization::getName, String.CASE_INSENSITIVE_ORDER));

        for (int i = 0; i < sortedOrgs.size(); i++) {
            Row row = listSheet.getRow(i);
            if (row == null) row = listSheet.createRow(i);
            row.createCell(0).setCellValue(sortedOrgs.get(i).getName());
        }

        writeCommonLists(listSheet, 1, 2, 3, 4);
    }

    private void createOrgListSheet(Sheet listSheet, List<Location> locations) {
        writeCommonLists(listSheet, 0, 1, 2, 3);

        List<Location> sortedLocations = new ArrayList<>(locations);
        sortedLocations.sort(Comparator.comparing(Location::getName, String.CASE_INSENSITIVE_ORDER));
        for (int i = 0; i < sortedLocations.size(); i++) {
            Row row = listSheet.getRow(i);
            if (row == null) row = listSheet.createRow(i);
            row.createCell(4).setCellValue(sortedLocations.get(i).getName());
        }
    }

    private void writeCommonLists(
            Sheet listSheet,
            int minyanTypeCol,
            int overrideModeCol,
            int enabledCol,
            int nusachCol) {

        for (int i = 0; i < MINYAN_TYPE_VALUES.length; i++) {
            Row row = listSheet.getRow(i);
            if (row == null) row = listSheet.createRow(i);
            row.createCell(minyanTypeCol).setCellValue(MINYAN_TYPE_VALUES[i]);
        }

        for (int i = 0; i < OVERRIDE_MODE_VALUES.length; i++) {
            Row row = listSheet.getRow(i);
            if (row == null) row = listSheet.createRow(i);
            row.createCell(overrideModeCol).setCellValue(OVERRIDE_MODE_VALUES[i]);
        }

        for (int i = 0; i < ENABLED_VALUES.length; i++) {
            Row row = listSheet.getRow(i);
            if (row == null) row = listSheet.createRow(i);
            row.createCell(enabledCol).setCellValue(ENABLED_VALUES[i]);
        }

        Nusach[] nusachValues = Nusach.values();
        for (int i = 0; i < nusachValues.length; i++) {
            Row row = listSheet.getRow(i);
            if (row == null) row = listSheet.createRow(i);
            row.createCell(nusachCol).setCellValue(nusachValues[i].name());
        }
    }

    private void createSuperDataValidations(Workbook workbook, Sheet sheet, int orgCount) {
        int maxRows = 1000;
        DataValidationHelper helper = sheet.getDataValidationHelper();

        String orgNameRange = "Lists!$A$1:$A$" + Math.max(orgCount, 1);
        String minyanTypeRange = "Lists!$B$1:$B$" + MINYAN_TYPE_VALUES.length;
        String overrideModeRange = "Lists!$C$1:$C$" + OVERRIDE_MODE_VALUES.length;
        String enabledRange = "Lists!$D$1:$D$" + ENABLED_VALUES.length;
        String nusachRange = "Lists!$E$1:$E$" + Nusach.values().length;

        createNamedRange(workbook, "SuperOrgNames", orgNameRange);
        createNamedRange(workbook, "MinyanTypes", minyanTypeRange);
        createNamedRange(workbook, "OverrideModes", overrideModeRange);
        createNamedRange(workbook, "EnabledValues", enabledRange);
        createNamedRange(workbook, "NusachValues", nusachRange);

        addDropDownValidation(helper, sheet, "SuperOrgNames", SUPER_COL_ORG_NAME, 1, maxRows);
        addDropDownValidation(helper, sheet, "MinyanTypes", SUPER_COL_MINYAN_TYPE, 1, maxRows);
        addDropDownValidation(helper, sheet, "OverrideModes", SUPER_COL_OVERRIDE_MODE, 1, maxRows);
        addDropDownValidation(helper, sheet, "EnabledValues", SUPER_COL_ENABLED, 1, maxRows);
        addDropDownValidation(helper, sheet, "NusachValues", SUPER_COL_NUSACH, 1, maxRows);
    }

    private void createOrgDataValidations(Workbook workbook, Sheet sheet, int locationCount) {
        int maxRows = 1000;
        DataValidationHelper helper = sheet.getDataValidationHelper();

        String minyanTypeRange = "Lists!$A$1:$A$" + MINYAN_TYPE_VALUES.length;
        String overrideModeRange = "Lists!$B$1:$B$" + OVERRIDE_MODE_VALUES.length;
        String enabledRange = "Lists!$C$1:$C$" + ENABLED_VALUES.length;
        String nusachRange = "Lists!$D$1:$D$" + Nusach.values().length;
        String locationsRange = "Lists!$E$1:$E$" + Math.max(locationCount, 1);

        createNamedRange(workbook, "OrgMinyanTypes", minyanTypeRange);
        createNamedRange(workbook, "OrgOverrideModes", overrideModeRange);
        createNamedRange(workbook, "OrgEnabledValues", enabledRange);
        createNamedRange(workbook, "OrgNusachValues", nusachRange);
        createNamedRange(workbook, "OrgLocationNames", locationsRange);

        addDropDownValidation(helper, sheet, "OrgMinyanTypes", ORG_COL_MINYAN_TYPE, 1, maxRows);
        addDropDownValidation(helper, sheet, "OrgOverrideModes", ORG_COL_OVERRIDE_MODE, 1, maxRows);
        addDropDownValidation(helper, sheet, "OrgEnabledValues", ORG_COL_ENABLED, 1, maxRows);
        addDropDownValidation(helper, sheet, "OrgNusachValues", ORG_COL_NUSACH, 1, maxRows);
        addDropDownValidation(helper, sheet, "OrgLocationNames", ORG_COL_LOCATION, 1, maxRows);
    }

    private void seedSuperExampleRows(Sheet sheet, Workbook workbook, List<Organization> organizations) {
        if (organizations.isEmpty()) return;

        CreationHelper creationHelper = workbook.getCreationHelper();
        CellStyle dateStyle = workbook.createCellStyle();
        dateStyle.setDataFormat(creationHelper.createDataFormat().getFormat("yyyy-mm-dd"));
        CellStyle timeStyle = workbook.createCellStyle();
        timeStyle.setDataFormat(creationHelper.createDataFormat().getFormat("hh:mm"));

        Organization org = organizations.get(0);

        Row row1 = sheet.createRow(1);
        row1.createCell(SUPER_COL_ORG_NAME).setCellValue(org.getName());
        Cell dateCell1 = row1.createCell(SUPER_COL_DATE);
        dateCell1.setCellValue(java.sql.Date.valueOf(LocalDate.now().plusDays(1)));
        dateCell1.setCellStyle(dateStyle);
        Cell timeCell1 = row1.createCell(SUPER_COL_TIME);
        timeCell1.setCellValue(java.sql.Time.valueOf(LocalTime.of(7, 0)));
        timeCell1.setCellStyle(timeStyle);
        row1.createCell(SUPER_COL_MINYAN_TYPE).setCellValue("SHACHARIS");
        row1.createCell(SUPER_COL_OVERRIDE_MODE).setCellValue("ADDITIVE");
        row1.createCell(SUPER_COL_LOCATION).setCellValue("");
        row1.createCell(SUPER_COL_NOTES).setCellValue("Example additional minyan");
        row1.createCell(SUPER_COL_NUSACH).setCellValue("UNSPECIFIED");
        row1.createCell(SUPER_COL_ENABLED).setCellValue("true");

        Row row2 = sheet.createRow(2);
        row2.createCell(SUPER_COL_ORG_NAME).setCellValue(org.getName());
        Cell dateCell2 = row2.createCell(SUPER_COL_DATE);
        dateCell2.setCellValue(java.sql.Date.valueOf(LocalDate.now().plusDays(2)));
        dateCell2.setCellStyle(dateStyle);
        Cell timeCell2 = row2.createCell(SUPER_COL_TIME);
        timeCell2.setCellValue(java.sql.Time.valueOf(LocalTime.of(13, 30)));
        timeCell2.setCellStyle(timeStyle);
        row2.createCell(SUPER_COL_MINYAN_TYPE).setCellValue("MINCHA");
        row2.createCell(SUPER_COL_OVERRIDE_MODE).setCellValue("FULL_DAY_REPLACE");
        row2.createCell(SUPER_COL_LOCATION).setCellValue("Main Sanctuary");
        row2.createCell(SUPER_COL_NOTES).setCellValue("Example full-day replacement");
        row2.createCell(SUPER_COL_NUSACH).setCellValue("ASHKENAZ");
        row2.createCell(SUPER_COL_ENABLED).setCellValue("true");
    }

    private void seedOrgExampleRows(Sheet sheet, Workbook workbook, Organization organization) {
        CreationHelper creationHelper = workbook.getCreationHelper();
        CellStyle dateStyle = workbook.createCellStyle();
        dateStyle.setDataFormat(creationHelper.createDataFormat().getFormat("yyyy-mm-dd"));
        CellStyle timeStyle = workbook.createCellStyle();
        timeStyle.setDataFormat(creationHelper.createDataFormat().getFormat("hh:mm"));

        Row row1 = sheet.createRow(1);
        Cell dateCell1 = row1.createCell(ORG_COL_DATE);
        dateCell1.setCellValue(java.sql.Date.valueOf(LocalDate.now().plusDays(1)));
        dateCell1.setCellStyle(dateStyle);
        Cell endDateCell1 = row1.createCell(ORG_COL_END_DATE);
        endDateCell1.setCellValue(java.sql.Date.valueOf(LocalDate.now().plusDays(1)));
        endDateCell1.setCellStyle(dateStyle);
        Cell timeCell1 = row1.createCell(ORG_COL_TIME);
        timeCell1.setCellValue(java.sql.Time.valueOf(LocalTime.of(7, 0)));
        timeCell1.setCellStyle(timeStyle);
        row1.createCell(ORG_COL_MINYAN_TYPE).setCellValue("SHACHARIS");
        row1.createCell(ORG_COL_OVERRIDE_MODE).setCellValue("ADDITIVE");
        row1.createCell(ORG_COL_LOCATION).setCellValue("");
        row1.createCell(ORG_COL_NOTES).setCellValue("Example additional minyan");
        row1.createCell(ORG_COL_NUSACH).setCellValue(
                organization != null && organization.getNusach() != null ? organization.getNusach().name() : "UNSPECIFIED");
        row1.createCell(ORG_COL_ENABLED).setCellValue("true");

        Row row2 = sheet.createRow(2);
        Cell dateCell2 = row2.createCell(ORG_COL_DATE);
        dateCell2.setCellValue(java.sql.Date.valueOf(LocalDate.now().plusDays(2)));
        dateCell2.setCellStyle(dateStyle);
        Cell endDateCell2 = row2.createCell(ORG_COL_END_DATE);
        endDateCell2.setCellValue(java.sql.Date.valueOf(LocalDate.now().plusDays(4)));
        endDateCell2.setCellStyle(dateStyle);
        Cell timeCell2 = row2.createCell(ORG_COL_TIME);
        timeCell2.setCellValue(java.sql.Time.valueOf(LocalTime.of(13, 30)));
        timeCell2.setCellStyle(timeStyle);
        row2.createCell(ORG_COL_MINYAN_TYPE).setCellValue("MINCHA");
        row2.createCell(ORG_COL_OVERRIDE_MODE).setCellValue("FULL_DAY_REPLACE");
        row2.createCell(ORG_COL_LOCATION).setCellValue("");
        row2.createCell(ORG_COL_NOTES).setCellValue("Example full-day replacement");
        row2.createCell(ORG_COL_NUSACH).setCellValue("UNSPECIFIED");
        row2.createCell(ORG_COL_ENABLED).setCellValue("true");
    }

    private void createNamedRange(Workbook workbook, String name, String formula) {
        Name namedRange = workbook.createName();
        namedRange.setNameName(name);
        namedRange.setRefersToFormula(formula);
    }

    private void addDropDownValidation(DataValidationHelper helper, Sheet sheet, String formulaName, int column, int firstRow, int lastRow) {
        DataValidationConstraint constraint = helper.createFormulaListConstraint(formulaName);
        CellRangeAddressList addressList = new CellRangeAddressList(firstRow, lastRow, column, column);
        DataValidation validation = helper.createValidation(constraint, addressList);
        validation.setShowErrorBox(true);
        validation.createErrorBox("Invalid value", "Please choose a value from the dropdown list.");
        sheet.addValidationData(validation);
    }

    private void formatColumns(Sheet sheet, Workbook workbook, int[] widths, int dateCol, int timeCol) {
        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, widths[i]);
        }

        CreationHelper creationHelper = workbook.getCreationHelper();
        CellStyle dateStyle = workbook.createCellStyle();
        dateStyle.setDataFormat(creationHelper.createDataFormat().getFormat("yyyy-mm-dd"));
        CellStyle timeStyle = workbook.createCellStyle();
        timeStyle.setDataFormat(creationHelper.createDataFormat().getFormat("hh:mm"));

        sheet.setDefaultColumnStyle(dateCol, dateStyle);
        if (dateCol == ORG_COL_DATE && widths.length > ORG_COL_END_DATE) {
            sheet.setDefaultColumnStyle(ORG_COL_END_DATE, dateStyle);
        }
        sheet.setDefaultColumnStyle(timeCol, timeStyle);

        sheet.createFreezePane(0, 1);
    }

    // ---------------------------------------------------------------------
    // Low-level parsing helpers
    // ---------------------------------------------------------------------

    private Organization resolveOrganizationByName(
            String orgNameRaw,
            Map<String, List<Organization>> orgsByNameLower) {

        String normalized = normalizeOrgName(orgNameRaw);
        List<Organization> matches = orgsByNameLower.get(normalized);
        if (matches == null || matches.isEmpty()) {
            throw new RowParseException("Unknown organization_name: " + orgNameRaw);
        }
        if (matches.size() > 1) {
            throw new RowParseException("organization_name is ambiguous: " + orgNameRaw);
        }
        return matches.get(0);
    }

    private Map<String, Map<String, Location>> buildLocationLookupByOrg(List<Organization> organizations) {
        Map<String, Map<String, Location>> lookup = new HashMap<>();
        for (Organization organization : organizations) {
            List<Location> locations = locationService.findMatching(organization.getId());
            Map<String, Location> byNameLower = locations.stream()
                    .filter(l -> l.getName() != null && !l.getName().trim().isEmpty())
                    .collect(Collectors.toMap(
                            l -> l.getName().trim().toLowerCase(Locale.US),
                            l -> l,
                            (a, b) -> a));
            lookup.put(organization.getId(), byNameLower);
        }
        return lookup;
    }

    private String readRequiredCell(Row row, int col, DataFormatter formatter, String fieldName) {
        String value = trimToNull(readCellAsString(row, col, formatter));
        if (value == null) throw new RowParseException("Missing required field: " + fieldName);
        return value;
    }

    private String readCellAsString(Row row, int col, DataFormatter formatter) {
        if (row == null) return null;
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        String raw = formatter.formatCellValue(cell);
        return raw == null ? null : raw.trim();
    }

    private LocalDate parseDateCell(Cell cell, DataFormatter formatter) {
        if (cell != null && cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            LocalDateTime ldt = cell.getLocalDateTimeCellValue();
            return ldt.toLocalDate();
        }
        String raw = trimToNull(cell == null ? null : formatter.formatCellValue(cell));
        if (raw == null) throw new RowParseException("Missing required field: date");
        for (DateTimeFormatter formatterDate : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(raw, formatterDate);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        throw new RowParseException("Invalid date: " + raw + ". Use YYYY-MM-DD.");
    }

    private LocalDate parseOptionalDateCell(Cell cell, DataFormatter formatter, LocalDate defaultValue) {
        if (cell == null) return defaultValue;
        String raw = trimToNull(formatter.formatCellValue(cell));
        if (raw == null) return defaultValue;
        return parseDateCell(cell, formatter);
    }

    private LocalTime parseTimeCell(Cell cell, DataFormatter formatter) {
        if (cell != null && cell.getCellType() == CellType.NUMERIC) {
            double value = cell.getNumericCellValue();
            if (DateUtil.isCellDateFormatted(cell) || (value >= 0 && value < 1)) {
                int totalSeconds = (int) Math.round((value % 1) * 24 * 60 * 60);
                totalSeconds = ((totalSeconds % 86400) + 86400) % 86400;
                return LocalTime.ofSecondOfDay(totalSeconds);
            }
        }
        String raw = trimToNull(cell == null ? null : formatter.formatCellValue(cell));
        if (raw == null) throw new RowParseException("Missing required field: start_time");
        for (DateTimeFormatter formatterTime : TIME_FORMATTERS) {
            try {
                return LocalTime.parse(raw, formatterTime);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        throw new RowParseException("Invalid start_time: " + raw + ". Use HH:mm or h:mm AM/PM.");
    }

    private MinyanType parseMinyanType(String raw) {
        String normalized = raw.trim().toUpperCase(Locale.US)
                .replace('-', '_')
                .replace('/', '_')
                .replace(' ', '_');
        normalized = normalized.replaceAll("_+", "_");
        if ("MINCHA_MARIV".equals(normalized)) {
            normalized = "MINCHA_MAARIV";
        }
        if ("MINCHA_MAARIV".equals(normalized)) return MinyanType.MINCHA_MAARIV;
        if ("SHACHARIS".equals(normalized)) return MinyanType.SHACHARIS;
        if ("MINCHA".equals(normalized)) return MinyanType.MINCHA;
        if ("MAARIV".equals(normalized)) return MinyanType.MAARIV;
        throw new RowParseException("Invalid minyan_type: " + raw + ". Allowed: SHACHARIS, MINCHA, MAARIV, MINCHA/MAARIV.");
    }

    private String parseOverrideMode(String raw) {
        String value = trimToNull(raw);
        if (value == null) return MODE_ADDITIVE;
        String normalized = value.toUpperCase(Locale.US).replace('-', '_').replace(' ', '_');
        if ("FULLDAY".equals(normalized) || "FULL_DAY".equals(normalized) || "REPLACE".equals(normalized)) {
            return MODE_FULL_DAY_REPLACE;
        }
        if (MODE_FULL_DAY_REPLACE.equals(normalized)) return MODE_FULL_DAY_REPLACE;
        if (MODE_ADDITIVE.equals(normalized)) return MODE_ADDITIVE;
        throw new RowParseException("Invalid override_mode: " + raw + ". Use ADDITIVE or FULL_DAY_REPLACE.");
    }

    private Nusach parseNusach(String raw, Nusach defaultNusach) {
        String value = trimToNull(raw);
        if (value == null) return defaultNusach;
        String normalized = value.toUpperCase(Locale.US).replace('-', '_').replace(' ', '_');
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

    private boolean isRowBlank(Row row, DataFormatter formatter, int firstCol, int lastCol) {
        for (int c = firstCol; c <= lastCol; c++) {
            String value = trimToNull(readCellAsString(row, c, formatter));
            if (value != null) return false;
        }
        return true;
    }

    private String normalizeOrgName(String name) {
        if (name == null) return "";
        return name.trim().toLowerCase(Locale.US);
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record CommonParsedFields(
            LocalDate date,
            LocalTime startTime,
            MinyanType minyanType,
            String overrideMode,
            String locationId,
            String locationName,
            String notes,
            Nusach nusach,
            boolean enabled
    ) {}

    private record ParsedRow(
            long rowNumber,
            String organizationId,
            LocalDate date,
            LocalTime startTime,
            MinyanType minyanType,
            String overrideMode,
            String locationId,
            String locationName,
            String notes,
            Nusach nusach,
            boolean enabled
    ) {}

    private record ParsedOrgRow(
            long rowNumber,
            String organizationId,
            LocalDate startDate,
            LocalDate endDate,
            LocalTime startTime,
            MinyanType minyanType,
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
            rowsRead++;
        }

        public void incrementCreatedCount() {
            createdCount++;
        }

        public void incrementUpdatedCount() {
            updatedCount++;
        }

        public void setDeletedManualCount(long deletedManualCount) {
            this.deletedManualCount = deletedManualCount;
        }

        public void addError(String error) {
            errors.add(error);
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }
}
