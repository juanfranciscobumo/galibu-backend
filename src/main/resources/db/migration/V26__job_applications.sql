-- Postulaciones: varios profesionales pueden enviar su solicitud a un mismo
-- trabajo publicado. Cada postulación es un job hijo (parent_job_id) con su
-- propio hilo de chat; el cliente elige una y el resto se cancela.
SET @col_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'jobs'
      AND COLUMN_NAME = 'parent_job_id'
);
SET @ddl = IF(
    @col_exists = 0,
    'ALTER TABLE jobs ADD COLUMN parent_job_id VARCHAR(64) NULL',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'jobs'
      AND INDEX_NAME = 'idx_jobs_parent_job_id'
);
SET @ddl = IF(
    @idx_exists = 0,
    'CREATE INDEX idx_jobs_parent_job_id ON jobs (parent_job_id)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
