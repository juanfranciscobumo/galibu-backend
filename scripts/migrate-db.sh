#!/usr/bin/env bash
# Migra la base de datos remota (Aiven) a un MySQL local en la misma VM.
#
# Uso (dentro de la VM):  bash ~/galibu-backend/scripts/migrate-db.sh
#
# - Lee las credenciales remotas de ~/galibu-backend/.env (no las imprime).
# - Guarda un respaldo del volcado en /tmp/galibu_dump.sql.
# - Guarda un respaldo del .env anterior (.env.bak.<fecha>).
# - Restaura en el contenedor galibu-db y reapunta el backend a él.
set -euo pipefail

APP_DIR="${HOME}/galibu-backend"
cd "$APP_DIR"

LOCAL_DB=galibu
LOCAL_USER=galibu
LOCAL_PASS=galibu_local_2026
LOCAL_ROOT_PASS=galibu_root_2026
MYSQL_IMAGE=docker.io/library/mysql:8.4
DUMP=/tmp/galibu_dump.sql

# ---------- 1. Credenciales remotas desde .env ----------
python3 - > /tmp/galibu_remote.env <<'PY'
import os, re, shlex
path = os.path.expanduser('~/galibu-backend/.env')
cfg = {}
for line in open(path, encoding='utf-8'):
    line = line.strip()
    if not line or line.startswith('#') or '=' not in line:
        continue
    k, v = line.split('=', 1)
    v = v.strip()
    if len(v) >= 2 and v[0] == v[-1] and v[0] in '"\'':
        v = v[1:-1]
    cfg[k.strip()] = v

url = cfg.get('DB_URL', '')
m = re.match(r'^jdbc:mysql://([^/]+)/([^?]+)', url)
if not m:
    print('ERROR: no hay DB_URL remota en .env (¿ya migraste o falta la variable?)', flush=True)
    raise SystemExit(3)
hostpart, db = m.group(1), m.group(2)
user = pw = None
if '@' in hostpart:
    userinfo, hostpart = hostpart.rsplit('@', 1)
    if ':' in userinfo:
        user, pw = userinfo.split(':', 1)
    else:
        user = userinfo
if ':' in hostpart:
    host, port = hostpart.rsplit(':', 1)
else:
    host, port = hostpart, '3306'
user = cfg.get('DB_USER') or user
pw = cfg.get('DB_PASSWORD') or pw
if not user or pw is None:
    print('ERROR: faltan DB_USER/DB_PASSWORD para el volcado', flush=True)
    raise SystemExit(3)

q = shlex.quote
print('RHOST=' + q(host))
print('RPORT=' + q(port))
print('RDB=' + q(db))
print('RUSER=' + q(user))
print('RPASS=' + q(pw))
PY

chmod 600 /tmp/galibu_remote.env
. /tmp/galibu_remote.env
echo "Origen: ${RUSER}@${RHOST}:${RPORT}/${RDB}"

# ---------- 2. Asegurar MySQL local ----------
sudo podman network exists galibu >/dev/null 2>&1 || sudo podman network create galibu
if ! sudo podman container exists galibu-db >/dev/null 2>&1; then
  echo "Creando contenedor MySQL local..."
  sudo podman volume create galibu_db_data >/dev/null 2>&1 || true
  sudo podman run -d --name galibu-db --restart=unless-stopped --network galibu \
    -e MYSQL_DATABASE="$LOCAL_DB" \
    -e MYSQL_USER="$LOCAL_USER" \
    -e MYSQL_PASSWORD="$LOCAL_PASS" \
    -e MYSQL_ROOT_PASSWORD="$LOCAL_ROOT_PASS" \
    -v galibu_db_data:/var/lib/mysql \
    "$MYSQL_IMAGE"
else
  sudo podman start galibu-db >/dev/null 2>&1 || true
fi

echo "Esperando a MySQL local..."
for _ in $(seq 1 60); do
  if sudo podman exec galibu-db mysqladmin ping -uroot -p"$LOCAL_ROOT_PASS" >/dev/null 2>&1; then
    echo "MySQL local listo"
    break
  fi
  sleep 5
done

# ---------- 3. Volcado de la BD remota ----------
echo "Volcando BD remota..."
dump_db() {
  sudo podman run --rm -e MYSQL_PWD="$RPASS" "$MYSQL_IMAGE" \
    mysqldump -h "$RHOST" -P "$RPORT" -u "$RUSER" "$@" "$RDB"
}

set +e
dump_db --single-transaction --quick --routines --triggers --no-tablespaces \
  --set-gtid-purged=OFF > "$DUMP" 2> /tmp/galibu_dump.err
RC=$?
if [ "$RC" -ne 0 ] || ! grep -q "CREATE TABLE" "$DUMP"; then
  echo "El volcado completo falló (rc=$RC); reintentando con opciones mínimas..."
  dump_db --single-transaction --quick --no-tablespaces --set-gtid-purged=OFF \
    > "$DUMP" 2> /tmp/galibu_dump.err
  RC=$?
fi
set -e

if [ "$RC" -ne 0 ] || ! grep -q "CREATE TABLE" "$DUMP"; then
  echo "ERROR: no se pudo volcar la BD remota; no se modifica nada:"
  tail -20 /tmp/galibu_dump.err || true
  exit 1
fi
echo "Volcado: $(stat -c %s "$DUMP") bytes"

# ---------- 4. Restaurar en el MySQL local ----------
echo "Restaurando en MySQL local..."
sudo podman exec -i galibu-db mysql -uroot -p"$LOCAL_ROOT_PASS" -e \
  "DROP DATABASE IF EXISTS \`$LOCAL_DB\`; CREATE DATABASE \`$LOCAL_DB\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
sudo podman exec -i galibu-db mysql -uroot -p"$LOCAL_ROOT_PASS" "$LOCAL_DB" < "$DUMP"

echo "Filas restauradas:"
sudo podman exec galibu-db mysql -uroot -p"$LOCAL_ROOT_PASS" -N -e \
  "SELECT CONCAT('users=', COUNT(*)) FROM \`$LOCAL_DB\`.users;
   SELECT CONCAT('jobs=', COUNT(*)) FROM \`$LOCAL_DB\`.jobs;
   SELECT CONCAT('chat_messages=', COUNT(*)) FROM \`$LOCAL_DB\`.chat_messages;"

# ---------- 5. Reapuntar el backend a la BD local ----------
cp .env ".env.bak.$(date +%Y%m%d%H%M%S)"
python3 - <<'PY'
import re
p = '/home/opc/galibu-backend/.env'
url = ('jdbc:mysql://galibu-db:3306/galibu?useSSL=false&allowPublicKeyRetrieval=true'
       '&serverTimezone=America/Bogota&useUnicode=true&characterEncoding=UTF-8')
values = {'DB_URL': url, 'DB_USER': 'galibu', 'DB_PASSWORD': 'galibu_local_2026'}
seen = set()
out = []
for line in open(p, encoding='utf-8').read().splitlines():
    m = re.match(r'^(DB_URL|DB_USER|DB_PASSWORD)\s*=', line)
    if m:
        k = m.group(1)
        out.append(k + '=' + values[k])
        seen.add(k)
    else:
        out.append(line)
for k, v in values.items():
    if k not in seen:
        out.append(k + '=' + v)
open(p, 'w', encoding='utf-8').write('\n'.join(out) + '\n')
print('Archivo .env actualizado a BD local')
PY

sudo podman rm -f galibu-backend >/dev/null 2>&1 || true
sudo podman run -d --name galibu-backend --restart=unless-stopped \
  --network galibu -p 8080:8080 --env-file .env galibu-backend:latest
sudo podman ps

# ---------- 6. Verificación ----------
for _ in $(seq 1 40); do
  if curl -fsS http://localhost:8080/actuator/health >/dev/null 2>&1; then
    echo "backend UP con BD local"
    sudo podman logs --tail 40 galibu-backend 2>&1 | grep -oE "jdbc:mysql://[^/?]+/[^? ]*" | tail -1 || true
    exit 0
  fi
  sleep 5
done

echo "El backend no arrancó con la BD local. Revisar: sudo podman logs galibu-backend"
echo "Para revertir: cp ~/galibu-backend/.env.bak.* ~/galibu-backend/.env && sudo podman rm -f galibu-backend &&"
echo "  sudo podman run -d --name galibu-backend --restart=unless-stopped --network galibu -p 8080:8080 --env-file .env galibu-backend:latest"
exit 1
