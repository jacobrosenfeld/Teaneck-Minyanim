package com.tbdev.teaneckminyanim.service.calendar;

import com.tbdev.teaneckminyanim.model.CalendarEvent;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
public class CalendarEventSchemaInitializer {
    static final String MINYAN_TYPE_COLUMN_METADATA_SQL = """
            SELECT DATA_TYPE, COLUMN_TYPE
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'calendar_events'
              AND COLUMN_NAME = 'minyan_type'
            """;

    static final String UPDATE_MINYAN_TYPE_ENUM_SQL = """
            ALTER TABLE calendar_events
            MODIFY COLUMN minyan_type %s NOT NULL
            """.formatted(CalendarEvent.MINYAN_TYPE_COLUMN_DEFINITION);

    static final List<String> REQUIRED_MINYAN_TYPE_VALUES = List.of(
            "SHACHARIS",
            "MINCHA",
            "MAARIV",
            "MINCHA_MAARIV",
            "SELICHOS",
            "SELICHOS_SHACHARIS",
            "MEGILA_READING",
            "NON_MINYAN",
            "OTHER",
            "MINYAN"
    );

    private static final String MANUAL_MIGRATION_PATH =
            "docs/migrations/MIGRATION_v1.17.1_calendar_event_minyan_type.sql";

    private final JdbcOperations jdbcOperations;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    public CalendarEventSchemaInitializer(JdbcOperations jdbcOperations) {
        this.jdbcOperations = jdbcOperations;
    }

    @PostConstruct
    public void initialize() {
        ensureSchema();
    }

    public void ensureSchema() {
        if (!initialized.compareAndSet(false, true)) {
            return;
        }

        try {
            List<Map<String, Object>> rows = jdbcOperations.queryForList(MINYAN_TYPE_COLUMN_METADATA_SQL);
            if (rows.isEmpty()) {
                return;
            }

            String dataType = Objects.toString(metadataValue(rows.get(0), "DATA_TYPE"), "");
            String columnType = Objects.toString(metadataValue(rows.get(0), "COLUMN_TYPE"), "");
            if (needsMinyanTypeMigration(dataType, columnType)) {
                jdbcOperations.execute(UPDATE_MINYAN_TYPE_ENUM_SQL);
                log.info("Updated calendar_events.minyan_type enum values");
            }
        } catch (DataAccessException e) {
            initialized.set(false);
            log.warn("Could not verify calendar_events.minyan_type schema. "
                    + "Manual override imports using SELICHOS_SHACHARIS may fail until {} is applied.",
                    MANUAL_MIGRATION_PATH,
                    e);
        }
    }

    static boolean needsMinyanTypeMigration(String dataType, String columnType) {
        String normalizedType = dataType == null ? "" : dataType.trim().toLowerCase(Locale.US);
        if (!"enum".equals(normalizedType)) {
            return true;
        }
        String normalizedColumnType = columnType == null ? "" : columnType.toUpperCase(Locale.US);
        for (String requiredValue : REQUIRED_MINYAN_TYPE_VALUES) {
            if (!normalizedColumnType.contains("'" + requiredValue + "'")) {
                return true;
            }
        }
        return false;
    }

    private Object metadataValue(Map<String, Object> row, String key) {
        if (row.containsKey(key)) {
            return row.get(key);
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (key.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }
}
