-- Ratings: clients rate professionals and vice versa once the service is underway.
CREATE TABLE IF NOT EXISTS ratings (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  job_id VARCHAR(64) NOT NULL,
  rater_id VARCHAR(64) NOT NULL,
  rated_id VARCHAR(64) NOT NULL,
  stars TINYINT NOT NULL,
  comment TEXT NULL,
  created_at BIGINT NOT NULL,
  CONSTRAINT uk_ratings_job_rater UNIQUE (job_id, rater_id),
  INDEX idx_ratings_rated (rated_id),
  CONSTRAINT fk_ratings_job FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
