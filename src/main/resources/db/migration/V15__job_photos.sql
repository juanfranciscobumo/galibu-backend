-- Fotos adjuntas a la solicitud (pipe-separated CSV de URLs/data URIs).
SET @col_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'jobs'
      AND COLUMN_NAME = 'photos'
);
SET @ddl = IF(
    @col_exists = 0,
    'ALTER TABLE jobs ADD COLUMN photos TEXT NULL',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
