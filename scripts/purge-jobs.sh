#!/usr/bin/env bash
# Elimina las solicitudes en estado OPEN (pendientes) y NEGOTIATING (en
# negociación), junto con sus postulaciones hijas y datos dependientes.
#
# Uso (dentro de la VM):
#   CONFIRM=YES bash ~/galibu-backend/scripts/purge-jobs.sh
#
# Antes de borrar genera un respaldo completo en ~/galibu-backups/.
# chat_messages y ratings se eliminan por FK ON DELETE CASCADE; wallet_transactions
# solo se desvincula (job_id = NULL) para no perder historial financiero.
set -euo pipefail

LOCAL_DB=galibu
LOCAL_ROOT_PASS=galibu_root_2026

if [ "${CONFIRM:-}" != "YES" ]; then
  echo "Modo seguro: exporta CONFIRM=YES para ejecutar la purga."
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "== Respaldo previo =="
bash "$SCRIPT_DIR/backup-db.sh"

run_sql() {
  sudo podman exec -i galibu-db mysql -uroot -p"$LOCAL_ROOT_PASS" "$LOCAL_DB"
}

echo "== Conteo antes =="
run_sql <<'SQL'
SELECT status, COUNT(*) AS total FROM jobs GROUP BY status ORDER BY total DESC;
SQL

echo "== Purgando =="
run_sql <<'SQL'
CREATE TEMPORARY TABLE purge_parents (id VARCHAR(64) NOT NULL PRIMARY KEY);
INSERT IGNORE INTO purge_parents (id)
  SELECT id FROM jobs WHERE status IN ('OPEN', 'NEGOTIATING');

-- Postulaciones hijas (también si su estado no fuera NEGOTIATING).
CREATE TEMPORARY TABLE purge_ids (id VARCHAR(64) NOT NULL PRIMARY KEY);
INSERT IGNORE INTO purge_ids (id) SELECT id FROM purge_parents;
INSERT IGNORE INTO purge_ids (id)
  SELECT id FROM jobs WHERE parent_job_id IN (SELECT id FROM purge_parents);

SELECT COUNT(*) AS solicitudes_a_borrar FROM purge_ids;

DELETE FROM chat_messages WHERE job_id IN (SELECT id FROM purge_ids);
DELETE FROM ratings WHERE job_id IN (SELECT id FROM purge_ids);
UPDATE wallet_transactions SET job_id = NULL WHERE job_id IN (SELECT id FROM purge_ids);
DELETE FROM jobs WHERE id IN (SELECT id FROM purge_ids);

DROP TEMPORARY TABLE purge_ids;
DROP TEMPORARY TABLE purge_parents;
SQL

echo "== Conteo después =="
run_sql <<'SQL'
SELECT status, COUNT(*) AS total FROM jobs GROUP BY status ORDER BY total DESC;
SQL

echo "Purga completada."
