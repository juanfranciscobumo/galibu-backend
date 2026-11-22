#!/usr/bin/env bash
# Despliegue del backend en la VM de OCI.
#
# Uso (dentro de la VM):  bash ~/galibu-backend/scripts/deploy.sh
#
# - La BD (MySQL) corre en el contenedor `galibu-db`, en la misma VM y en la red
#   `galibu`, por lo que la latencia es ~1 ms.
# - Solo se crea el contenedor de BD la primera vez; los datos viven en el
#   volumen `galibu_db_data`.
set -euo pipefail

APP_DIR="${HOME}/galibu-backend"
cd "$APP_DIR"

MYSQL_IMAGE=docker.io/library/mysql:8.4
LOCAL_DB=galibu
LOCAL_USER=galibu
LOCAL_PASS=galibu_local_2026
LOCAL_ROOT_PASS=galibu_root_2026

# ---------- 1. Limpieza de espacio (no toca volúmenes ni imágenes en uso) ----------
echo "Limpiando espacio..."
sudo podman image prune -af >/dev/null 2>&1 || true
rm -rf "$HOME"/.gradle/wrapper/dists "$APP_DIR"/build "$APP_DIR"/.gradle 2>/dev/null || true
sudo find /var/log -type f -name "*.log" -size +50M -delete 2>/dev/null || true
sudo journalctl --vacuum-size=100M >/dev/null 2>&1 || true

# ---------- 2. Red compartida backend <-> BD ----------
if ! sudo podman network inspect galibu >/dev/null 2>&1; then
  echo "Creando red galibu..."
  sudo podman network create galibu >/dev/null
fi

# ---------- 3. MySQL local ----------
if ! sudo podman container exists galibu-db 2>/dev/null; then
  echo "Creando contenedor MySQL local..."
  sudo podman volume create galibu_db_data >/dev/null 2>&1 || true
  sudo podman run -d --name galibu-db --restart=unless-stopped --network galibu \
    -e MYSQL_DATABASE="$LOCAL_DB" \
    -e MYSQL_USER="$LOCAL_USER" \
    -e MYSQL_PASSWORD="$LOCAL_PASS" \
    -e MYSQL_ROOT_PASSWORD="$LOCAL_ROOT_PASS" \
    -v galibu_db_data:/var/lib/mysql \
    "$MYSQL_IMAGE" >/dev/null
else
  sudo podman start galibu-db >/dev/null 2>&1 || true
fi

echo "Esperando a MySQL..."
for _ in $(seq 1 60); do
  if sudo podman exec galibu-db mysqladmin ping -uroot -p"$LOCAL_ROOT_PASS" >/dev/null 2>&1; then
    echo "MySQL listo"
    break
  fi
  sleep 5
done

# ---------- 4. Imagen del backend ----------
echo "Construyendo imagen del backend..."
sudo podman build -t galibu-backend:latest .

# ---------- 5. Backend ----------
sudo podman rm -f galibu-backend >/dev/null 2>&1 || true
sudo podman run -d --name galibu-backend --restart=unless-stopped \
  --network galibu -p 8080:8080 --env-file .env galibu-backend:latest >/dev/null
sudo podman ps

# ---------- 6. Health check ----------
for _ in $(seq 1 40); do
  if curl -fsS http://localhost:8080/actuator/health >/dev/null 2>&1; then
    echo "backend UP"
    sudo podman logs --tail 40 galibu-backend 2>&1 | grep -oE "jdbc:mysql://[^/?]+/[^? ]*" | tail -1 || true
    exit 0
  fi
  sleep 5
done

echo "Health check pendiente: revisar 'sudo podman logs galibu-backend'"
exit 1
