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

    private static final int COL_ORG_ID = 0;
    private static final int COL_ORG_NAME = 1;
    private static final int COL_DATE = 2;
    private static final int COL_TIME = 3;
    private static final int COL_MINYAN_TYPE = 4;
    private static final int COL_OVERRIDE_MODE = 5;
    private static final int COL_LOCATION = 6;
    private static final int COL_NOTES = 7;
    private static final int COL_NUSACH = 8;
    private static final int COL_ENABLED = 9;

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

    public byte[] buildTemplate(List<Organization> organizations) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Overrides");
            Sheet lists = workbook.createSheet("Lists");

            createHeaderRow(workbook, sheet);
            createListSheet(lists, organizations);
            createDataValidations(workbook, sheet, organizations.size());
            formatColumns(sheet, workbook);
            seedExampleRows(sheet, workbook, organizations);

            workbook.setSheetHidden(workbook.getSheetIndex(lists), true);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    @Transactional
    public ImportResult importWorkbook(MultipartFile file, String username) {
        ImportResult result = new ImportResult();
        if (file == null || file.isEmpty()) {
            result.addError("No XLSX file uploaded.");
            return result;
        }

        List<Organization> organizations = organizationService.getAll();
        Map<String, Organization> orgById = organizations.stream()
                .collect(Collectors.toMap(Organization::getId, o -> o));
        Map<String, List<Organization>> orgsByNameLower = organizations.stream()
                .collect(Collectors.groupingBy(o -> o.getName().trim().toLowerCase(Locale.US)));
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
                if (row == null || isRowBlank(row, formatter)) continue;

                result.incrementRowsRead();
                try {
                    ParsedRow parsedRow = parseRow(
                            row, formatter, orgById, orgsByNameLower, locationsByOrgAndNameLower);
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

        if (parsedRows.isEmpty()) {
            if (!result.hasErrors()) {
                result.addError("No valid rows found in workbook.");
            }
            return result;
        }

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

        for (ParsedRow row : parsedRows) {
            try {
                upsertRow(row, username, result);
            } catch (Exception e) {
                result.addError("Row " + row.rowNumber() + ": failed to save (" + e.getMessage() + ")");
            }
        }

        return result;
    }

    private void createHeaderRow(Workbook workbook, Sheet sheet) {
        Row header = sheet.createRow(0);
        String[] headers = {
                "organization_id", "organization_name", "date", "start_time",
                "minyan_type", "override_mode", "location", "notes", "nusach", "enabled"
        };

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

    private void createListSheet(Sheet listSheet, List<Organization> organizations) {
        List<Organization> sortedOrgs = new ArrayList<>(organizations);
        sortedOrgs.sort(Comparator.comparing(Organization::getName, String.CASE_INSENSITIVE_ORDER));

        for (int i = 0; i < sortedOrgs.size(); i++) {
            Row row = listSheet.getRow(i);
            if (row == null) row = listSheet.createRow(i);
            row.createCell(0).setCellValue(sortedOrgs.get(i).getId());
            row.createCell(1).setCellValue(sortedOrgs.get(i).getName());
        }

        for (int i = 0; i < MINYAN_TYPE_VALUES.length; i++) {
            Row row = listSheet.getRow(i);
            if (row == null) row = listSheet.createRow(i);
            row.createCell(2).setCellValue(MINYAN_TYPE_VALUES[i]);
        }

        for (int i = 0; i < OVERRIDE_MODE_VALUES.length; i++) {
            Row row = listSheet.getRow(i);
            if (row == null) row = listSheet.createRow(i);
            row.createCell(3).setCellValue(OVERRIDE_MODE_VALUES[i]);
        }

        for (int i = 0; i < ENABLED_VALUES.length; i++) {
            Row row = listSheet.getRow(i);
            if (row == null) row = listSheet.createRow(i);
            row.createCell(4).setCellValue(ENABLED_VALUES[i]);
        }

        Nusach[] nusachValues = Nusach.values();
        for (int i = 0; i < nusachValues.length; i++) {
            Row row = listSheet.getRow(i);
            if (row == null) row = listSheet.createRow(i);
            row.createCell(5).setCellValue(nusachValues[i].name());
        }
    }

    private void createDataValidations(Workbook workbook, Sheet sheet, int orgCount) {
        int maxRows = 1000;
        DataValidationHelper helper = sheet.getDataValidationHelper();

        String orgIdRange = "Lists!$A$1:$A$" + Math.max(orgCount, 1);
        String orgNameRange = "Lists!$B$1:$B$" + Math.max(orgCount, 1);
        String minyanTypeRange = "Lists!$C$1:$C$" + MINYAN_TYPE_VALUES.length;
        String overrideModeRange = "Lists!$D$1:$D$" + OVERRIDE_MODE_VALUES.length;
        String enabledRange = "Lists!$E$1:$E$" + ENABLED_VALUES.length;
        String nusachRange = "Lists!$F$1:$F$" + Nusach.values().length;

        createNamedRange(workbook, "OrgIds", orgIdRange);
        createNamedRange(workbook, "OrgNames", orgNameRange);
        createNamedRange(workbook, "MinyanTypes", minyanTypeRange);
        createNamedRange(workbook, "OverrideModes", overrideModeRange);
        createNamedRange(workbook, "EnabledValues", enabledRange);
        createNamedRange(workbook, "NusachValues", nusachRange);

        addDropDownValidation(helper, sheet, "OrgIds", COL_ORG_ID, 1, maxRows);
        addDropDownValidation(helper, sheet, "OrgNames", COL_ORG_NAME, 1, maxRows);
        addDropDownValidation(helper, sheet, "MinyanTypes", COL_MINYAN_TYPE, 1, maxRows);
        addDropDownValidation(helper, sheet, "OverrideModes", COL_OVERRIDE_MODE, 1, maxRows);
        addDropDownValidation(helper, sheet, "EnabledValues", COL_ENABLED, 1, maxRows);
        addDropDownValidation(helper, sheet, "NusachValues", COL_NUSACH, 1, maxRows);
    }

    private void addDropDownValidation(DataValidationHelper helper, Sheet sheet, String formulaName, int column, int firstRow, int lastRow) {
        DataValidationConstraint constraint = helper.createFormulaListConstraint(formulaName);
        CellRangeAddressList addressList = new CellRangeAddressList(firstRow, lastRow, column, column);
        DataValidation validation = helper.createValidation(constraint, addressList);
        validation.setShowErrorBox(true);
        validation.createErrorBox("Invalid value", "Please choose a value from the dropdown list.");
        sheet.addValidationData(validation);
    }

    private void createNamedRange(Workbook workbook, String name, String formula) {
        Name namedRange = workbook.createName();
        namedRange.setNameName(name);
        namedRange.setRefersToFormula(formula);
    }

    private void formatColumns(Sheet sheet, Workbook workbook) {
        sheet.setColumnWidth(COL_ORG_ID, 5200);
        sheet.setColumnWidth(COL_ORG_NAME, 9000);
        sheet.setColumnWidth(COL_DATE, 4200);
        sheet.setColumnWidth(COL_TIME, 3400);
        sheet.setColumnWidth(COL_MINYAN_TYPE, 4800);
        sheet.setColumnWidth(COL_OVERRIDE_MODE, 5000);
        sheet.setColumnWidth(COL_LOCATION, 7000);
        sheet.setColumnWidth(COL_NOTES, 10000);
        sheet.setColumnWidth(COL_NUSACH, 4500);
        sheet.setColumnWidth(COL_ENABLED, 3200);

        CreationHelper creationHelper = workbook.getCreationHelper();
        CellStyle dateStyle = workbook.createCellStyle();
        dateStyle.setDataFormat(creationHelper.createDataFormat().getFormat("yyyy-mm-dd"));
        CellStyle timeStyle = workbook.createCellStyle();
        timeStyle.setDataFormat(creationHelper.createDataFormat().getFormat("hh:mm"));

        sheet.setDefaultColumnStyle(COL_DATE, dateStyle);
        sheet.setDefaultColumnStyle(COL_TIME, timeStyle);
        sheet.createFreezePane(0, 1);
    }

    private void seedExampleRows(Sheet sheet, Workbook workbook, List<Organization> organizations) {
        if (organizations.isEmpty()) return;

        CreationHelper creationHelper = workbook.getCreationHelper();
        CellStyle dateStyle = workbook.createCellStyle();
        dateStyle.setDataFormat(creationHelper.createDataFormat().getFormat("yyyy-mm-dd"));
        CellStyle timeStyle = workbook.createCellStyle();
        timeStyle.setDataFormat(creationHelper.createDataFormat().getFormat("hh:mm"));

        Organization org = organizations.get(0);

        Row row1 = sheet.createRow(1);
        row1.createCell(COL_ORG_ID).setCellValue(org.getId());
        row1.createCell(COL_ORG_NAME).setCellValue(org.getName());
        Cell dateCell1 = row1.createCell(COL_DATE);
        dateCell1.setCellValue(java.sql.Date.valueOf(LocalDate.now().plusDays(1)));
        dateCell1.setCellStyle(dateStyle);
        Cell timeCell1 = row1.createCell(COL_TIME);
        timeCell1.setCellValue(java.sql.Time.valueOf(LocalTime.of(7, 0)));
        timeCell1.setCellStyle(timeStyle);
        row1.createCell(COL_MINYAN_TYPE).setCellValue("SHACHARIS");
        row1.createCell(COL_OVERRIDE_MODE).setCellValue("ADDITIVE");
        row1.createCell(COL_LOCATION).setCellValue("");
        row1.createCell(COL_NOTES).setCellValue("Example additional minyan");
        row1.createCell(COL_NUSACH).setCellValue("UNSPECIFIED");
        row1.createCell(COL_ENABLED).setCellValue("true");

        Row row2 = sheet.createRow(2);
        row2.createCell(COL_ORG_ID).setCellValue(org.getId());
        row2.createCell(COL_ORG_NAME).setCellValue(org.getName());
        Cell dateCell2 = row2.createCell(COL_DATE);
        dateCell2.setCellValue(java.sql.Date.valueOf(LocalDate.now().plusDays(2)));
        dateCell2.setCellStyle(dateStyle);
        Cell timeCell2 = row2.createCell(COL_TIME);
        timeCell2.setCellValue(java.sql.Time.valueOf(LocalTime.of(13, 30)));
        timeCell2.setCellStyle(timeStyle);
        row2.createCell(COL_MINYAN_TYPE).setCellValue("MINCHA");
        row2.createCell(COL_OVERRIDE_MODE).setCellValue("FULL_DAY_REPLACE");
        row2.createCell(COL_LOCATION).setCellValue("Main Sanctuary");
        row2.createCell(COL_NOTES).setCellValue("Example full-day replacement");
        row2.createCell(COL_NUSACH).setCellValue("ASHKENAZ");
        row2.createCell(COL_ENABLED).setCellValue("true");
    }

    private ParsedRow parseRow(
            Row row,
            DataFormatter formatter,
            Map<String, Organization> orgById,
            Map<String, List<Organization>> orgsByNameLower,
            Map<String, Map<String, Location>> locationsByOrgAndNameLower) {

        String orgIdRaw = trimToNull(readCellAsString(row, COL_ORG_ID, formatter));
        String orgNameRaw = trimToNull(readCellAsString(row, COL_ORG_NAME, formatter));
        Organization org = resolveOrganization(orgIdRaw, orgNameRaw, orgById, orgsByNameLower);

        LocalDate date = parseDateCell(row.getCell(COL_DATE), formatter);
        LocalTime time = parseTimeCell(row.getCell(COL_TIME), formatter);
        MinyanType minyanType = parseMinyanType(readRequiredCell(row, COL_MINYAN_TYPE, formatter, "minyan_type"));
        String overrideMode = parseOverrideMode(readCellAsString(row, COL_OVERRIDE_MODE, formatter));
        String locationRaw = trimToNull(readCellAsString(row, COL_LOCATION, formatter));
        String notes = trimToNull(readCellAsString(row, COL_NOTES, formatter));
        Nusach nusach = parseNusach(readCellAsString(row, COL_NUSACH, formatter), org.getNusach());
        boolean enabled = parseEnabled(readCellAsString(row, COL_ENABLED, formatter));

        String locationId = null;
        String locationName = null;
        if (locationRaw != null) {
            Map<String, Location> orgLocations = locationsByOrgAndNameLower.getOrDefault(org.getId(), Map.of());
            Location match = orgLocations.get(locationRaw.toLowerCase(Locale.US));
            if (match != null) {
                locationId = match.getId();
                locationName = match.getName();
            } else {
                locationName = locationRaw;
            }
        }

        return new ParsedRow(
                row.getRowNum() + 1L,
                org.getId(),
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

    private Organization resolveOrganization(
            String orgIdRaw,
            String orgNameRaw,
            Map<String, Organization> orgById,
            Map<String, List<Organization>> orgsByNameLower) {
        if (orgIdRaw != null) {
            Organization org = orgById.get(orgIdRaw);
            if (org != null) {
                if (orgNameRaw != null && !org.getName().trim().equalsIgnoreCase(orgNameRaw.trim())) {
                    throw new RowParseException("organization_id and organization_name do not match.");
                }
                return org;
            }
            throw new RowParseException("Unknown organization_id: " + orgIdRaw);
        }

        if (orgNameRaw != null) {
            List<Organization> matches = orgsByNameLower.get(orgNameRaw.toLowerCase(Locale.US));
            if (matches == null || matches.isEmpty()) {
                throw new RowParseException("Unknown organization_name: " + orgNameRaw);
            }
            if (matches.size() > 1) {
                throw new RowParseException("organization_name is ambiguous. Use organization_id.");
            }
            return matches.get(0);
        }

        throw new RowParseException("Missing organization_id or organization_name.");
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

    private void upsertRow(ParsedRow row, String username, ImportResult result) {
        Optional<CalendarEvent> existing = calendarEventRepository
                .findFirstByOrganizationIdAndDateAndMinyanTypeAndStartTimeAndSource(
                        row.organizationId(), row.date(), row.minyanType(), row.startTime(), EventSource.MANUAL);

        String sourceRefPrefix = MODE_FULL_DAY_REPLACE.equals(row.overrideMode())
                ? EffectiveScheduleService.MANUAL_FULL_DAY_SOURCE_REF_PREFIX
                : MANUAL_ADDITIVE_SOURCE_REF_PREFIX;

        CalendarEvent event = existing.orElseGet(() -> CalendarEvent.builder()
                .organizationId(row.organizationId())
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

    private boolean isRowBlank(Row row, DataFormatter formatter) {
        for (int c = COL_ORG_ID; c <= COL_ENABLED; c++) {
            String value = trimToNull(readCellAsString(row, c, formatter));
            if (value != null) return false;
        }
        return true;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

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
