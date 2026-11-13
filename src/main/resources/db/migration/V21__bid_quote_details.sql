-- Detalle de la cotizacion del profesional (Fase 2b).
-- El precio vive en las ofertas; estos campos describen como se construyo:
--   quote_type        ESTIMATED (estimada), AFTER_VISIT (tras visita), FIXED (cerrada)
--   visit_required    la cotizacion requiere visita tecnica
--   visit_fee         costo opcional de la visita (informativo)
--   materials_included / materials_cost  materiales incluidos en el precio
--   counter_round     numero de ronda de negociacion (0 = primera oferta)

ALTER TABLE bids
  ADD COLUMN quote_type VARCHAR(20) NOT NULL DEFAULT 'ESTIMATED',
  ADD COLUMN visit_required BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN visit_fee DOUBLE NULL,
  ADD COLUMN materials_included BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN materials_cost DOUBLE NULL,
  ADD COLUMN counter_round INTEGER NOT NULL DEFAULT 0;
