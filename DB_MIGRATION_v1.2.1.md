# Database Migration for v1.2.1 - Calendar Import Feature

## Overview
This migration adds support for importing calendar entries from CSV exports to override rule-based minyan generation.

## Changes Required

### 1. Add column to ORGANIZATION table
```sql
ALTER TABLE organization 
ADD COLUMN USE_SCRAPED_CALENDAR BOOLEAN DEFAULT FALSE NOT NULL;
```

### 2. Create ORGANIZATION_CALENDAR_ENTRY table
```sql
CREATE TABLE organization_calendar_entry (
    ID BIGINT AUTO_INCREMENT PRIMARY KEY,
    organization_id VARCHAR(255) NOT NULL,
    date DATE NOT NULL,
    start_time TIME,
    start_datetime DATETIME,
    end_time TIME,
    end_datetime DATETIME,
    title VARCHAR(255) NOT NULL,
    type VARCHAR(255),
    name VARCHAR(255),
    location VARCHAR(255),
    description TEXT,
    hebrew_date VARCHAR(255),
    raw_text TEXT,
    source_url TEXT,
    fingerprint VARCHAR(255) NOT NULL UNIQUE,
    enabled BOOLEAN DEFAULT TRUE NOT NULL,
    duplicate_reason VARCHAR(255),
    imported_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    
    INDEX idx_org_date (organization_id, date),
    INDEX idx_org_enabled_date (organization_id, enabled, date),
    CONSTRAINT uk_fingerprint UNIQUE (fingerprint),
    CONSTRAINT fk_org_calendar_entry FOREIGN KEY (organization_id) 
        REFERENCES organization(ID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## Manual Execution Steps

1. **Backup your database first:**
   ```bash
   mysqldump -u root -p minyanim > backup_before_v1.2.1_$(date +%Y%m%d).sql
   ```

2. **Connect to MariaDB:**
   ```bash
   mysql -u root -p minyanim
   ```

3. **Run the ALTER TABLE statement** for the organization table.

4. **Run the CREATE TABLE statement** for organization_calendar_entry.

5. **Verify the changes:**
   ```sql
   SHOW COLUMNS FROM organization LIKE 'USE_SCRAPED_CALENDAR';
   DESCRIBE organization_calendar_entry;
   ```

6. **Expected output:**
   - organization table should show the new USE_SCRAPED_CALENDAR column
   - organization_calendar_entry table should exist with all specified columns

## Rollback (if needed)

```sql
-- Drop the new table
DROP TABLE IF EXISTS organization_calendar_entry;

-- Remove the column from organization
ALTER TABLE organization DROP COLUMN USE_SCRAPED_CALENDAR;
```

## Notes

- The Hibernate auto-update feature (`spring.jpa.hibernate.ddl-auto=update`) may automatically create the table and add the column when the application starts.
- However, for production environments, it's recommended to run these migrations manually.
- The fingerprint column ensures no duplicate entries are imported.
- The enabled column allows admin users to disable specific imported entries without deleting them.
- Foreign key constraint ensures referential integrity with the organization table.
