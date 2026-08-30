package com.tbdev.teaneckminyanim.service;

import com.tbdev.teaneckminyanim.enums.EventSource;
import com.tbdev.teaneckminyanim.enums.Nusach;
import com.tbdev.teaneckminyanim.minyan.MinyanType;
import com.tbdev.teaneckminyanim.model.CalendarEvent;
import com.tbdev.teaneckminyanim.model.Location;
import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.repo.CalendarEventRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuperAdminOverrideXlsxServiceTest {

    private static final String ORG_ID = "org-1";
    private static final String ORG_NAME = "Congregation Test";
    private static final LocalDate DATE = LocalDate.of(2026, 6, 7);
    private static final LocalTime TIME = LocalTime.of(8, 0);

    @Mock
    private CalendarEventRepository calendarEventRepository;

    @Mock
    private OrganizationService organizationService;

    @Mock
    private LocationService locationService;

    @InjectMocks
    private SuperAdminOverrideXlsxService service;

    @Test
    void buildTemplates_includeSelichosInMinyanTypeDropdownLists() throws Exception {
        Organization organization = organization();
        Location location = new Location("Ogden Lower Level", ORG_ID);

        try (Workbook superWorkbook = WorkbookFactory.create(new ByteArrayInputStream(
                service.buildSuperAdminTemplate(List.of(organization))));
             Workbook orgWorkbook = WorkbookFactory.create(new ByteArrayInputStream(
                     service.buildOrganizationTemplate(organization, List.of(location))))) {

            assertTrue(columnValues(superWorkbook.getSheet("Lists"), 1).contains("SELICHOS"));
            assertTrue(columnValues(orgWorkbook.getSheet("Lists"), 0).contains("SELICHOS"));
        }
    }

    @Test
    void importOrganizationWorkbook_acceptsSelichosFullDayReplaceRows() throws Exception {
        Organization organization = organization();
        when(organizationService.findById(ORG_ID)).thenReturn(Optional.of(organization));
        when(locationService.findMatching(ORG_ID)).thenReturn(List.of(new Location("Ogden Lower Level", ORG_ID)));
        when(calendarEventRepository.deleteByOrganizationIdAndDateAndSource(ORG_ID, DATE, EventSource.MANUAL))
                .thenReturn(1L);
        when(calendarEventRepository.findByOrganizationIdAndDateAndMinyanTypeAndStartTimeAndSourceOrderByIdAsc(
                any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        SuperAdminOverrideXlsxService.ImportResult result = service.importOrganizationWorkbook(
                ORG_ID,
                multipartFile(orgWorkbook(
                        new String[]{"2026-06-07", "", "5:05 AM", "SELICHOS", "FULL_DAY_REPLACE", "Ogden Lower Level", "Sephardic Selichot", "SEFARD", "true"},
                        new String[]{"2026-06-07", "", "6:20 AM", "SHACHARIS", "FULL_DAY_REPLACE", "Main Sanctuary", "", "ASHKENAZ", "true"})),
                "tester");

        ArgumentCaptor<CalendarEvent> captor = ArgumentCaptor.forClass(CalendarEvent.class);
        verify(calendarEventRepository, times(2)).save(captor.capture());

        List<CalendarEvent> savedEvents = captor.getAllValues();
        CalendarEvent selichos = savedEvents.stream()
                .filter(event -> event.getMinyanType() == MinyanType.SELICHOS)
                .findFirst()
                .orElseThrow();

        assertFalse(result.hasErrors());
        assertEquals(2, result.getRowsRead());
        assertEquals(2, result.getCreatedCount());
        assertEquals(1L, result.getDeletedManualCount());
        assertEquals(LocalTime.of(5, 5), selichos.getStartTime());
        assertEquals("Ogden Lower Level", selichos.getLocationName());
        assertEquals("Sephardic Selichot", selichos.getNotes());
        assertEquals(Nusach.SEFARD, selichos.getNusach());
    }

    @Test
    void importSuperAdminWorkbook_acceptsSelichosRows() throws Exception {
        Organization organization = organization();
        when(organizationService.getAll()).thenReturn(List.of(organization));
        when(locationService.findMatching(ORG_ID)).thenReturn(List.of(new Location("Ogden Lower Level", ORG_ID)));
        when(calendarEventRepository.findByOrganizationIdAndDateAndMinyanTypeAndStartTimeAndSourceOrderByIdAsc(
                any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        SuperAdminOverrideXlsxService.ImportResult result = service.importSuperAdminWorkbook(
                multipartFile(superWorkbook(
                        new String[]{ORG_NAME, "2026-06-07", "5:05 AM", "SELICHOS", "ADDITIVE", "Ogden Lower Level", "Sephardic Selichot", "SEFARD", "true"})),
                "tester");

        ArgumentCaptor<CalendarEvent> captor = ArgumentCaptor.forClass(CalendarEvent.class);
        verify(calendarEventRepository).save(captor.capture());

        CalendarEvent saved = captor.getValue();
        assertFalse(result.hasErrors());
        assertEquals(1, result.getRowsRead());
        assertEquals(1, result.getCreatedCount());
        assertEquals(MinyanType.SELICHOS, saved.getMinyanType());
        assertEquals(LocalTime.of(5, 5), saved.getStartTime());
        assertEquals("Ogden Lower Level", saved.getLocationName());
        assertEquals("Sephardic Selichot", saved.getNotes());
        assertEquals(Nusach.SEFARD, saved.getNusach());
    }

    @Test
    void upsertManualEvent_createsSecondRowWhenSameTimeExistingRowHasDifferentLocation() {
        CalendarEvent existingOldMain = manualEvent(41L, "Old Main", null, Nusach.ASHKENAZ);

        when(calendarEventRepository.findByOrganizationIdAndDateAndMinyanTypeAndStartTimeAndSourceOrderByIdAsc(
                ORG_ID, DATE, MinyanType.SHACHARIS, TIME, EventSource.MANUAL))
                .thenReturn(List.of(existingOldMain));

        boolean updated = service.upsertManualEvent(
                ORG_ID,
                DATE,
                TIME,
                MinyanType.SHACHARIS,
                "FULL_DAY_REPLACE",
                null,
                "Ogden Lower Level",
                "Deliberately Paced Minyan",
                Nusach.ASHKENAZ,
                true,
                "tester");

        ArgumentCaptor<CalendarEvent> captor = ArgumentCaptor.forClass(CalendarEvent.class);
        verify(calendarEventRepository).save(captor.capture());

        CalendarEvent saved = captor.getValue();
        assertFalse(updated);
        assertNull(saved.getId());
        assertEquals("Ogden Lower Level", saved.getLocationName());
        assertEquals("Deliberately Paced Minyan", saved.getNotes());
        assertEquals("Old Main", existingOldMain.getLocationName());
        assertNull(existingOldMain.getNotes());
    }

    @Test
    void upsertManualEvent_updatesMatchingLocationAmongSameTimeDuplicates() {
        CalendarEvent oldMain = manualEvent(41L, "Old Main", null, Nusach.ASHKENAZ);
        CalendarEvent ogden = manualEvent(42L, "Ogden Lower Level", "Old Label", Nusach.ASHKENAZ);

        when(calendarEventRepository.findByOrganizationIdAndDateAndMinyanTypeAndStartTimeAndSourceOrderByIdAsc(
                ORG_ID, DATE, MinyanType.SHACHARIS, TIME, EventSource.MANUAL))
                .thenReturn(List.of(oldMain, ogden));

        boolean updated = service.upsertManualEvent(
                ORG_ID,
                DATE,
                TIME,
                MinyanType.SHACHARIS,
                "FULL_DAY_REPLACE",
                null,
                "Ogden Lower Level",
                "Deliberately Paced Minyan",
                Nusach.ASHKENAZ,
                true,
                "tester");

        ArgumentCaptor<CalendarEvent> captor = ArgumentCaptor.forClass(CalendarEvent.class);
        verify(calendarEventRepository).save(captor.capture());

        CalendarEvent saved = captor.getValue();
        assertTrue(updated);
        assertSame(ogden, saved);
        assertEquals("Deliberately Paced Minyan", ogden.getNotes());
        assertEquals("Old Main", oldMain.getLocationName());
    }

    private CalendarEvent manualEvent(Long id, String locationName, String notes, Nusach nusach) {
        return CalendarEvent.builder()
                .id(id)
                .organizationId(ORG_ID)
                .date(DATE)
                .minyanType(MinyanType.SHACHARIS)
                .startTime(TIME)
                .locationName(locationName)
                .notes(notes)
                .nusach(nusach)
                .source(EventSource.MANUAL)
                .enabled(true)
                .build();
    }

    private Organization organization() {
        return Organization.builder()
                .id(ORG_ID)
                .name(ORG_NAME)
                .nusach(Nusach.ASHKENAZ)
                .orgColor("#123456")
                .build();
    }

    private byte[] superWorkbook(String[]... rows) throws IOException {
        return workbook(
                new String[]{"organization_name", "date", "start_time", "minyan_type", "override_mode", "location", "notes", "nusach", "enabled"},
                rows);
    }

    private byte[] orgWorkbook(String[]... rows) throws IOException {
        return workbook(
                new String[]{"date", "end_date", "start_time", "minyan_type", "override_mode", "location", "notes", "nusach", "enabled"},
                rows);
    }

    private byte[] workbook(String[] headers, String[]... rows) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Overrides");
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            for (int rowIndex = 0; rowIndex < rows.length; rowIndex++) {
                Row row = sheet.createRow(rowIndex + 1);
                for (int cellIndex = 0; cellIndex < rows[rowIndex].length; cellIndex++) {
                    row.createCell(cellIndex).setCellValue(rows[rowIndex][cellIndex]);
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private List<String> columnValues(Sheet sheet, int columnIndex) {
        List<String> values = new ArrayList<>();
        for (Row row : sheet) {
            if (row.getCell(columnIndex) != null) {
                values.add(row.getCell(columnIndex).getStringCellValue());
            }
        }
        return values;
    }

    private MultipartFile multipartFile(byte[] bytes) {
        return new MultipartFile() {
            @Override
            public String getName() {
                return "file";
            }

            @Override
            public String getOriginalFilename() {
                return "overrides.xlsx";
            }

            @Override
            public String getContentType() {
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            }

            @Override
            public boolean isEmpty() {
                return bytes.length == 0;
            }

            @Override
            public long getSize() {
                return bytes.length;
            }

            @Override
            public byte[] getBytes() {
                return bytes;
            }

            @Override
            public InputStream getInputStream() {
                return new ByteArrayInputStream(bytes);
            }

            @Override
            public void transferTo(File dest) {
                throw new UnsupportedOperationException("Not needed for tests");
            }
        };
    }
}
