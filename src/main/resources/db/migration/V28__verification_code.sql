-- Código de verificación de 6 dígitos para iniciar el servicio.
-- El cliente lo ve en el detalle de la solicitud y el profesional debe
-- proporcionarlo al tocar "Iniciar servicio".
SET @col_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'jobs'
      AND COLUMN_NAME = 'verification_code'
);
SET @ddl = IF(
    @col_exists = 0,
    'ALTER TABLE jobs ADD COLUMN verification_code VARCHAR(6) NULL',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
