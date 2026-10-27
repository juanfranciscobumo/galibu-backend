-- Origen de la solicitud (PUBLISH/DIRECTORY). Idempotente: la columna puede
-- existir ya si se creó por fuera de Flyway.
SET @col_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'jobs'
      AND COLUMN_NAME = 'source'
);
SET @ddl = IF(
    @col_exists = 0,
    'ALTER TABLE jobs ADD COLUMN source VARCHAR(20) NULL',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
