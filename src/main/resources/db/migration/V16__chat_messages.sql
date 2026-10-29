CREATE TABLE IF NOT EXISTS chat_messages (
  id VARCHAR(64) NOT NULL PRIMARY KEY,
  job_id VARCHAR(64) NOT NULL,
  sender_id VARCHAR(64) NOT NULL,
  sender_name VARCHAR(120) NOT NULL DEFAULT '',
  text TEXT NULL,
  timestamp BIGINT NOT NULL,
  type VARCHAR(20) NOT NULL DEFAULT 'text',
  is_read TINYINT(1) NOT NULL DEFAULT 0,
  CONSTRAINT fk_chat_job FOREIGN KEY (job_id) REFERENCES jobs (id) ON DELETE CASCADE,
  INDEX idx_chat_job_timestamp (job_id, timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;