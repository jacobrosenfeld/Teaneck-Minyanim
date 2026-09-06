package com.tbdev.teaneckminyanim.service.calendar;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcOperations;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CalendarEventSchemaInitializerTest {

    @Test
    void ensureSchemaDoesNotRunDdlForCompleteEnum() {
        JdbcOperations jdbcOperations = mock(JdbcOperations.class);
        when(jdbcOperations.queryForList(CalendarEventSchemaInitializer.MINYAN_TYPE_COLUMN_METADATA_SQL))
                .thenReturn(List.of(Map.of(
                        "DATA_TYPE", "enum",
                        "COLUMN_TYPE", "enum('SHACHARIS','MINCHA','MAARIV','MINCHA_MAARIV','SELICHOS',"
                                + "'SELICHOS_SHACHARIS','MEGILA_READING','NON_MINYAN','OTHER','MINYAN')"
                )));
        CalendarEventSchemaInitializer initializer = new CalendarEventSchemaInitializer(jdbcOperations);

        initializer.ensureSchema();
        initializer.ensureSchema();

        verify(jdbcOperations, times(1))
                .queryForList(CalendarEventSchemaInitializer.MINYAN_TYPE_COLUMN_METADATA_SQL);
        verify(jdbcOperations, never()).execute(anyString());
    }

    @Test
    void ensureSchemaUpdatesNativeEnumColumnMissingSelichosShacharis() {
        JdbcOperations jdbcOperations = mock(JdbcOperations.class);
        when(jdbcOperations.queryForList(CalendarEventSchemaInitializer.MINYAN_TYPE_COLUMN_METADATA_SQL))
                .thenReturn(List.of(Map.of(
                        "DATA_TYPE", "enum",
                        "COLUMN_TYPE", "enum('SHACHARIS','MINCHA','MAARIV','MINCHA_MAARIV','SELICHOS')"
                )));
        CalendarEventSchemaInitializer initializer = new CalendarEventSchemaInitializer(jdbcOperations);

        initializer.ensureSchema();

        verify(jdbcOperations).execute(CalendarEventSchemaInitializer.UPDATE_MINYAN_TYPE_ENUM_SQL);
    }

    @Test
    void ensureSchemaConvertsVarcharColumnBackToEnum() {
        JdbcOperations jdbcOperations = mock(JdbcOperations.class);
        when(jdbcOperations.queryForList(CalendarEventSchemaInitializer.MINYAN_TYPE_COLUMN_METADATA_SQL))
                .thenReturn(List.of(Map.of(
                        "DATA_TYPE", "varchar",
                        "COLUMN_TYPE", "varchar(64)"
                )));
        CalendarEventSchemaInitializer initializer = new CalendarEventSchemaInitializer(jdbcOperations);

        initializer.ensureSchema();

        verify(jdbcOperations).execute(CalendarEventSchemaInitializer.UPDATE_MINYAN_TYPE_ENUM_SQL);
    }

    @Test
    void ensureSchemaSkipsMissingCalendarEventsColumn() {
        JdbcOperations jdbcOperations = mock(JdbcOperations.class);
        when(jdbcOperations.queryForList(CalendarEventSchemaInitializer.MINYAN_TYPE_COLUMN_METADATA_SQL))
                .thenReturn(List.of());
        CalendarEventSchemaInitializer initializer = new CalendarEventSchemaInitializer(jdbcOperations);

        initializer.ensureSchema();

        verify(jdbcOperations, never()).execute(anyString());
    }

    @Test
    void needsMinyanTypeMigrationRecognizesSafeColumn() {
        String completeEnum = "enum('SHACHARIS','MINCHA','MAARIV','MINCHA_MAARIV','SELICHOS',"
                + "'SELICHOS_SHACHARIS','MEGILA_READING','NON_MINYAN','OTHER','MINYAN')";

        assertFalse(CalendarEventSchemaInitializer.needsMinyanTypeMigration("ENUM", completeEnum));
        assertTrue(CalendarEventSchemaInitializer.needsMinyanTypeMigration("enum", "enum('SHACHARIS')"));
        assertTrue(CalendarEventSchemaInitializer.needsMinyanTypeMigration("varchar", "varchar(64)"));
    }
}
