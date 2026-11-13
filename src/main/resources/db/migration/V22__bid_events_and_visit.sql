-- Fase 2c: historial de rondas por oferta y agenda de visita tecnica.
--
-- bid_events guarda cada movimiento de una oferta (oferta, contraoferta,
-- aceptacion o rechazo) para que cliente y profesional vean el historial
-- completo de esa negociacion. No se publica en el chat del job porque un
-- job puede tener varias ofertas de profesionales distintos.

ALTER TABLE bids
  ADD COLUMN visit_scheduled_at BIGINT NULL;

CREATE TABLE IF NOT EXISTS bid_events (
  id VARCHAR(180) NOT NULL PRIMARY KEY,
  bid_id VARCHAR(128) NOT NULL,
  job_id VARCHAR(64) NOT NULL,
  author_id VARCHAR(64) NOT NULL,
  author_role VARCHAR(20) NOT NULL,
  action VARCHAR(20) NOT NULL,
  amount DOUBLE NOT NULL,
  round_number INT NOT NULL,
  note VARCHAR(2000) NULL,
  created_at BIGINT NOT NULL,
  INDEX idx_bid_event_bid (bid_id),
  INDEX idx_bid_event_job (job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
