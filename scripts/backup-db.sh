#!/usr/bin/env bash
# Respaldo de la BD local (contenedor galibu-db) en la misma VM.
#
# Uso (dentro de la VM):  bash ~/galibu-backend/scripts/backup-db.sh
# Los archivos quedan en ~/galibu-backups/ y se conservan los últimos 14.
#
# Si existe /tmp/galibu_dump.sql (volcado de la migración desde Aiven) se
# conserva una copia comprimida como galibu-premigracion.sql.gz.
set -euo pipefail

BACKUP_DIR="${HOME}/galibu-backups"
KEEP=14
LOCAL_DB=galibu
LOCAL_ROOT_PASS=galibu_root_2026
MYSQL_IMAGE=docker.io/library/mysql:8.4

mkdir -p "$BACKUP_DIR"
DATE=$(date +%Y%m%d-%H%M%S)
OUT="$BACKUP_DIR/galibu-$DATE.sql.gz"

echo "Respaldando $LOCAL_DB en $OUT..."
sudo podman exec galibu-db mysqldump -uroot -p"$LOCAL_ROOT_PASS" \
  --single-transaction --quick --routines --triggers --no-tablespaces \
  --set-gtid-purged=OFF "$LOCAL_DB" 2>/dev/null | gzip -9 > "$OUT"

if [ ! -s "$OUT" ]; then
  echo "ERROR: el respaldo quedó vacío"
  rm -f "$OUT"
  exit 1
fi
echo "Respaldo: $(du -h "$OUT" | cut -f1)"

# Conservar el volcado original de la migración (si aún está en /tmp).
PRE="$BACKUP_DIR/galibu-premigracion.sql.gz"
if [ -s /tmp/galibu_dump.sql ] && [ ! -s "$PRE" ]; then
  gzip -9 -c /tmp/galibu_dump.sql > "$PRE"
  echo "Volcado de migración conservado en $PRE"
fi

echo "Rotación (máx. $KEEP respaldos)..."
ls -1t "$BACKUP_DIR"/galibu-2*.sql.gz 2>/dev/null | tail -n +$((KEEP + 1)) | while read -r old; do
  rm -f "$old"
  echo "Eliminado: $old"
done

ls -lh "$BACKUP_DIR"
