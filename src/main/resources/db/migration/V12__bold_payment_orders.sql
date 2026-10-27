CREATE TABLE IF NOT EXISTS bold_payment_orders (
  id VARCHAR(64) NOT NULL PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  amount DOUBLE NOT NULL,
  payment_link VARCHAR(128) NULL,
  checkout_url VARCHAR(512) NULL,
  status VARCHAR(20) NOT NULL,
  transaction_id VARCHAR(128) NULL,
  payment_method VARCHAR(64) NULL,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  paid_at BIGINT NULL,
  CONSTRAINT fk_bold_order_user FOREIGN KEY (user_id) REFERENCES users (id),
  INDEX idx_bold_order_user (user_id),
  INDEX idx_bold_order_link (payment_link),
  INDEX idx_bold_order_status (status)
);
