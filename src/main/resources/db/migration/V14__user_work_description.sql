-- Descripción del portafolio del profesional. Idempotente: la columna puede
-- existir ya si se creó por fuera de Flyway.
SET @col_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'work_description'
);
SET @ddl = IF(
    @col_exists = 0,
    'ALTER TABLE users ADD COLUMN work_description TEXT NULL',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
