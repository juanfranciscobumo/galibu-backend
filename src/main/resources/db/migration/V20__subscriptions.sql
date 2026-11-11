-- Suscripcion Pro del profesional.
-- El cliente sigue siendo gratuito. El profesional puede completar sus 3
-- primeros servicios sin suscribirse; a partir del cuarto necesita un plan Pro.

ALTER TABLE users
  ADD COLUMN subscription_plan VARCHAR(20) NOT NULL DEFAULT 'FREE',
  ADD COLUMN subscription_status VARCHAR(20) NOT NULL DEFAULT 'NONE',
  ADD COLUMN subscription_expires_at BIGINT NULL;

-- Las ordenes de Bold ahora pueden pagar una recarga de wallet (legado) o una
-- suscripcion. El concepto y el plan permiten acreditar lo correcto al pagar.
ALTER TABLE bold_payment_orders
  ADD COLUMN concept VARCHAR(20) NOT NULL DEFAULT 'WALLET',
  ADD COLUMN plan VARCHAR(20) NULL,
  ADD COLUMN subscription_id VARCHAR(64) NULL;

CREATE TABLE IF NOT EXISTS subscriptions (
  id VARCHAR(64) NOT NULL PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  plan VARCHAR(20) NOT NULL,
  status VARCHAR(20) NOT NULL,
  amount DOUBLE NOT NULL,
  started_at BIGINT NOT NULL,
  expires_at BIGINT NOT NULL,
  bold_order_id VARCHAR(64) NULL,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  CONSTRAINT fk_subscription_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  INDEX idx_subscription_user (user_id),
  INDEX idx_subscription_status (status),
  INDEX idx_subscription_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
