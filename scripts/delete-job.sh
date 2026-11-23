#!/usr/bin/env bash
# Elimina una solicitud concreta y sus datos dependientes (postulaciones hijas,
# mensajes y calificaciones). Desvincula transacciones de wallet.
#
# Uso (dentro de la VM):
#   CONFIRM=YES bash ~/galibu-backend/scripts/delete-job.sh <JOB_ID>
#
# Antes de borrar genera un respaldo completo en ~/galibu-backups/.
set -euo pipefail

LOCAL_DB=galibu
LOCAL_ROOT_PASS=galibu_root_2026

JOB_ID="${1:-}"
if [ -z "$JOB_ID" ]; then
  echo "Uso: CONFIRM=YES bash scripts/delete-job.sh <JOB_ID>"
  exit 1
fi
if [[ ! "$JOB_ID" =~ ^[A-Za-z0-9_]+$ ]]; then
  echo "ERROR: JOB_ID inválido: $JOB_ID"
  exit 1
fi
if [ "${CONFIRM:-}" != "YES" ]; then
  echo "Modo seguro: exporta CONFIRM=YES para eliminar $JOB_ID."
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "== Respaldo previo =="
bash "$SCRIPT_DIR/backup-db.sh"

run_sql() {
  sudo podman exec -i galibu-db mysql -uroot -p"$LOCAL_ROOT_PASS" "$LOCAL_DB"
}

echo "== Solicitud a eliminar =="
run_sql <<SQL
SELECT id, title, status, client_name, professional_name, created_at
FROM jobs WHERE id = '$JOB_ID';
SQL

echo "== Borrando =="
run_sql <<SQL
CREATE TEMPORARY TABLE del_ids (id VARCHAR(64) NOT NULL PRIMARY KEY);
INSERT IGNORE INTO del_ids (id) VALUES ('$JOB_ID');
INSERT IGNORE INTO del_ids (id) SELECT id FROM jobs WHERE parent_job_id = '$JOB_ID';

SELECT COUNT(*) AS trabajos_a_borrar FROM del_ids;

DELETE FROM chat_messages WHERE job_id IN (SELECT id FROM del_ids);
DELETE FROM ratings WHERE job_id IN (SELECT id FROM del_ids);
UPDATE wallet_transactions SET job_id = NULL WHERE job_id IN (SELECT id FROM del_ids);
DELETE FROM jobs WHERE id IN (SELECT id FROM del_ids);

DROP TEMPORARY TABLE del_ids;
SQL

echo "== Verificación =="
run_sql <<SQL
SELECT COUNT(*) AS restantes FROM jobs WHERE id = '$JOB_ID';
SQL

echo "Solicitud $JOB_ID eliminada."
