-- Apply before deploying v1.19.0 to installations using ddl-auto=validate/none.
-- Existing ACCOUNT.ORGANIZATION_ID remains the primary/default shul; no backfill required.
-- This collection stores additional shul assignments only. Clearing assignments does not
-- grant super-admin access: scoped users must retain a primary organization.
CREATE TABLE IF NOT EXISTS ACCOUNT_ORGANIZATION (
    ACCOUNT_ID VARCHAR(255) NOT NULL,
    ORGANIZATION_ID VARCHAR(255) NOT NULL,
    PRIMARY KEY (ACCOUNT_ID, ORGANIZATION_ID)
);

CREATE TABLE IF NOT EXISTS AUTH_AUDIT_EVENT (
    ID VARCHAR(64) NOT NULL PRIMARY KEY,
    ACCOUNT_ID VARCHAR(64) NULL,
    EVENT_TYPE VARCHAR(64) NOT NULL,
    IP_HASH CHAR(64) NULL,
    DETAILS VARCHAR(1000) NULL,
    CREATED_AT DATETIME NOT NULL,
    INDEX IDX_AUTH_AUDIT_ACCOUNT (ACCOUNT_ID),
    INDEX IDX_AUTH_AUDIT_CREATED (CREATED_AT),
    INDEX IDX_AUTH_AUDIT_TYPE (EVENT_TYPE)
);

UPDATE ACCOUNT
SET EMAIL_NORMALIZED = LOWER(TRIM(EMAIL))
WHERE EMAIL IS NOT NULL
  AND (EMAIL_NORMALIZED IS NULL OR EMAIL_NORMALIZED <> LOWER(TRIM(EMAIL)));

-- Review this query during deployment and resolve every returned row before enabling
-- passwordless production sign-in. Duplicate ownership requires an operator decision.
SELECT EMAIL_NORMALIZED, COUNT(*) AS DUPLICATE_COUNT
FROM ACCOUNT
WHERE EMAIL_NORMALIZED IS NOT NULL
GROUP BY EMAIL_NORMALIZED
HAVING COUNT(*) > 1;

-- Run this after the query above returns no rows.
CREATE UNIQUE INDEX IF NOT EXISTS UQ_ACCOUNT_EMAIL_NORMALIZED
    ON ACCOUNT (EMAIL_NORMALIZED);
-- Hibernate manages removal of collection rows when deleting accounts. No new FK is
-- introduced because existing deployments may use different ACCOUNT.ID column types.
