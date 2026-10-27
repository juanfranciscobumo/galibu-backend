-- Galibu backend baseline: mirrors the Firestore data model.
-- String PKs preserve Firestore document ids (chats keep working unchanged).

CREATE TABLE IF NOT EXISTS users (
  id VARCHAR(64) NOT NULL PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  email VARCHAR(160) NOT NULL,
  phone VARCHAR(40) NULL,
  role VARCHAR(20) NOT NULL,
  is_verified TINYINT(1) NOT NULL DEFAULT 1,
  rating FLOAT NOT NULL DEFAULT 5.0,
  completed_jobs INT NOT NULL DEFAULT 0,
  balance DOUBLE NOT NULL DEFAULT 0,
  document_type VARCHAR(60) NULL,
  document_photo_url TEXT NULL,
  work_category VARCHAR(80) NULL,
  verification_status VARCHAR(20) NOT NULL DEFAULT 'VERIFIED',
  password_hash VARCHAR(100) NULL,
  auth_provider VARCHAR(10) NOT NULL DEFAULT 'LOCAL',
  profile_photo_url TEXT NULL,
  department VARCHAR(80) NULL,
  municipality VARCHAR(80) NULL,
  address VARCHAR(255) NULL,
  work_photos TEXT NULL,
  hourly_rate DOUBLE NULL,
  accepted_terms TINYINT(1) NOT NULL DEFAULT 0,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  CONSTRAINT uq_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS jobs (
  id VARCHAR(64) NOT NULL PRIMARY KEY,
  category VARCHAR(80) NOT NULL DEFAULT '',
  title VARCHAR(200) NOT NULL DEFAULT '',
  description TEXT NULL,
  budget_min DOUBLE NOT NULL DEFAULT 0,
  address VARCHAR(255) NULL,
  latitude DOUBLE NOT NULL DEFAULT 0,
  longitude DOUBLE NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  client_id VARCHAR(64) NOT NULL,
  client_name VARCHAR(120) NOT NULL DEFAULT '',
  professional_id VARCHAR(64) NULL,
  professional_name VARCHAR(120) NULL,
  final_price DOUBLE NULL,
  created_at BIGINT NOT NULL,
  completion_rating FLOAT NULL,
  completion_review TEXT NULL,
  client_rating_of_pro FLOAT NULL,
  client_review_of_pro TEXT NULL,
  pro_rating_of_client FLOAT NULL,
  pro_review_of_client TEXT NULL,
  department VARCHAR(80) NULL,
  municipality VARCHAR(80) NULL,
  pro_hourly_rate DOUBLE NULL,
  counter_offer_amount DOUBLE NULL,
  counter_offer_sender VARCHAR(20) NULL,
  updated_at BIGINT NOT NULL,
  CONSTRAINT fk_jobs_client FOREIGN KEY (client_id) REFERENCES users (id),
  CONSTRAINT fk_jobs_professional FOREIGN KEY (professional_id) REFERENCES users (id),
  INDEX idx_jobs_status (status),
  INDEX idx_jobs_client (client_id),
  INDEX idx_jobs_professional (professional_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS bids (
  id VARCHAR(64) NOT NULL PRIMARY KEY,
  job_id VARCHAR(64) NOT NULL,
  professional_id VARCHAR(64) NOT NULL,
  professional_name VARCHAR(120) NOT NULL DEFAULT '',
  professional_rating FLOAT NOT NULL DEFAULT 5.0,
  professional_photo TEXT NULL,
  amount DOUBLE NOT NULL DEFAULT 0,
  comment TEXT NULL,
  duration_hours INT NOT NULL DEFAULT 1,
  status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
  last_counter_offer_amount DOUBLE NULL,
  created_at BIGINT NOT NULL,
  CONSTRAINT fk_bids_job FOREIGN KEY (job_id) REFERENCES jobs (id) ON DELETE CASCADE,
  INDEX idx_bids_job (job_id),
  INDEX idx_bids_professional (professional_id),
  INDEX idx_bids_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS devices (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  fcm_token VARCHAR(255) NOT NULL,
  platform VARCHAR(20) NULL,
  updated_at BIGINT NOT NULL,
  CONSTRAINT fk_devices_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT uq_devices_token UNIQUE (fcm_token),
  INDEX idx_devices_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS departments (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(80) NOT NULL,
  CONSTRAINT uq_departments_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS municipalities (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  department_id BIGINT NOT NULL,
  name VARCHAR(80) NOT NULL,
  CONSTRAINT fk_municipalities_department FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE CASCADE,
  CONSTRAINT uq_municipalities_dept_name UNIQUE (department_id, name),
  INDEX idx_municipalities_department (department_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
