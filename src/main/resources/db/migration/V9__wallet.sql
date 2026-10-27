CREATE TABLE IF NOT EXISTS wallet_transactions (
  id VARCHAR(64) NOT NULL PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  type VARCHAR(20) NOT NULL,
  amount DOUBLE NOT NULL,
  balance_after DOUBLE NOT NULL,
  job_id VARCHAR(64) NULL,
  description VARCHAR(255) NULL,
  created_at BIGINT NOT NULL,
  CONSTRAINT fk_wallet_user FOREIGN KEY (user_id) REFERENCES users (id),
  INDEX idx_wallet_user (user_id),
  INDEX idx_wallet_created (created_at)
);

-- Bono inicial para cuentas existentes sin saldo.
UPDATE users SET balance = 150000 WHERE balance = 0;
