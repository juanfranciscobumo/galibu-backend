-- Contadores diarios de tráfico para las estadísticas del monitor.
-- Se actualizan en bloque desde UsageLedger para no escribir en cada petición.
CREATE TABLE IF NOT EXISTS api_daily_usage (
  usage_date DATE NOT NULL PRIMARY KEY,
  requests BIGINT NOT NULL DEFAULT 0,
  errors BIGINT NOT NULL DEFAULT 0,
  total_ms BIGINT NOT NULL DEFAULT 0,
  max_ms BIGINT NOT NULL DEFAULT 0
);
