-- Saldo inicial a 0 y 3 trabajos gratis de cortesía para profesionales.
ALTER TABLE users ADD COLUMN free_acceptances_remaining INT NOT NULL DEFAULT 0;

UPDATE users SET balance = 0.0;
UPDATE users SET free_acceptances_remaining = 3 WHERE role IN ('PROFESSIONAL', 'BOTH');
