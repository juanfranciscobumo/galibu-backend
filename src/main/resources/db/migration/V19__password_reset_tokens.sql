-- Tokens de recuperación de contraseña de un solo uso para auth/password.
CREATE TABLE IF NOT EXISTS password_reset_tokens (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  token_hash VARCHAR(100) NOT NULL,
  expires_at BIGINT NOT NULL,
  used TINYINT(1) NOT NULL DEFAULT 0,
  created_at BIGINT NOT NULL,
  CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  INDEX idx_password_reset_user (user_id),
  INDEX idx_password_reset_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
