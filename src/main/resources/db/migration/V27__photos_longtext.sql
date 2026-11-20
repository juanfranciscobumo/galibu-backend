-- Las fotos se guardan como data URIs base64 separadas por '|', lo que supera
-- el limite de TEXT (~65 KB). Se amplia a LONGTEXT.
SET @col_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'jobs'
      AND COLUMN_NAME = 'photos'
);
SET @col_type = (
    SELECT DATA_TYPE
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'jobs'
      AND COLUMN_NAME = 'photos'
);
SET @ddl = IF(
    @col_exists = 0,
    'ALTER TABLE jobs ADD COLUMN photos LONGTEXT NULL',
    IF(@col_type <> 'longtext', 'ALTER TABLE jobs MODIFY COLUMN photos LONGTEXT NULL', 'SELECT 1')
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
