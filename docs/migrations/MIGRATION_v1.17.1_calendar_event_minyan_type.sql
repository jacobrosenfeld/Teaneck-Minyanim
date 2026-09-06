-- Migration SQL for v1.17.1: allow linked Selichos/Shacharis calendar event overrides.
-- Required when MariaDB created calendar_events.minyan_type as a native ENUM
-- before SELICHOS_SHACHARIS existed.

ALTER TABLE calendar_events
MODIFY COLUMN minyan_type ENUM(
    'SHACHARIS',
    'MINCHA',
    'MAARIV',
    'MINCHA_MAARIV',
    'SELICHOS',
    'SELICHOS_SHACHARIS',
    'MEGILA_READING',
    'NON_MINYAN',
    'OTHER',
    'MINYAN'
) NOT NULL;
