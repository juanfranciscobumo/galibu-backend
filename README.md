# Galibu-BE — Backend API + Firebase

API Spring Boot + MySQL que reemplaza a Firestore/Auth como base de datos.
Firebase se conserva solo para: push (FCM), chat en tiempo real
(`jobs/{id}/chats`, `typing`) y Storage (fotos/documentos).

## Requisitos

- JDK 21
- MySQL corriendo en `localhost:3306` (XAMPP) con base `galibu` y usuario `galibu`
  (ver comandos abajo; Flyway crea el esquema solo)

## Configuración

Variables de entorno (con default de desarrollo):

| Variable | Default | Descripción |
|---|---|---|
| `DB_USER` | `galibu` | Usuario MySQL |
| `DB_PASSWORD` | `galibu_local_2026` | Clave MySQL |
| `JWT_SECRET` | clave de dev | **Mínimo 256 bits. Cambiar en producción** |
| `GOOGLE_CLIENT_IDS` | client ID Android | IDs de cliente OAuth separados por coma |
| `PORT` | `8080` | No cableado aún (usar `-Dserver.port=...` si se necesita) |

Crear la base una vez (XAMPP, root sin clave por defecto):

```sql
CREATE DATABASE IF NOT EXISTS galibu CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'galibu'@'localhost' IDENTIFIED BY 'galibu_local_2026';
GRANT ALL PRIVILEGES ON galibu.* TO 'galibu'@'localhost';
FLUSH PRIVILEGES;
```

## Correr

```bat
cd Galibu-BE
.\gradlew.bat bootRun
```

La app escucha en `http://localhost:8080`. Flyway ejecuta las migraciones
(`V1__init.sql`: `users`, `jobs`, `bids`, `devices`, `departments`, `municipalities`;
`V2__bid_id_length.sql`; `V3__categories.sql`) al arrancar.

## Endpoints F0

```bash
# Registro
curl -X POST localhost:8080/api/auth/register -H "Content-Type: application/json" -d "{\"name\":\"Prueba\",\"email\":\"prueba@test.com\",\"password\":\"secreto123\",\"role\":\"CLIENT\"}"

# Login
curl -X POST localhost:8080/api/auth/login -H "Content-Type: application/json" -d "{\"email\":\"prueba@test.com\",\"password\":\"secreto123\"}"

# Google (idToken del SDK)
curl -X POST localhost:8080/api/auth/google -H "Content-Type: application/json" -d "{\"idToken\":\"...\",\"role\":\"PROFESSIONAL\"}"

# Refresh
curl -X POST localhost:8080/api/auth/refresh -H "Content-Type: application/json" -d "{\"refreshToken\":\"...\"}"

# Geo (públicos)
curl localhost:8080/api/geo/departments
curl localhost:8080/api/geo/departments/1/municipalities

# Health
curl localhost:8080/actuator/health
```

Uso con token: `Authorization: Bearer <accessToken>`.

## Endpoints F1 (requieren JWT salvo geo/auth)

Jobs (`/api/jobs`):

```bash
H="Authorization: Bearer <TOKEN>"
curl localhost:8080/api/jobs/open -H "$H"                                  # marketplace OPEN
curl "localhost:8080/api/jobs?clientId=<uid>" -H "$H"                       # mis solicitudes
curl "localhost:8080/api/jobs?professionalId=<uid>" -H "$H"                 # mis trabajos
curl localhost:8080/api/jobs/<id> -H "$H"                                   # detalle (OPEN o NEGOTIATING sin asignar son públicos)
curl -X POST localhost:8080/api/jobs -H "$H" -H "Content-Type: application/json" -d "{\"title\":\"Fuga\",\"category\":\"Plomería\",\"budgetMin\":50000,\"suggestedPrice\":45000}"
curl -X PATCH localhost:8080/api/jobs/<id>/cancel -H "$H"                   # cancelado suave (participante)
curl -X DELETE localhost:8080/api/jobs/<id> -H "$H"                         # borrado duro (cliente; cascada a bids)
curl -X POST localhost:8080/api/jobs/<id>/counter -H "$H" -H "Content-Type: application/json" -d "{\"amount\":40000}"
curl -X POST localhost:8080/api/jobs/<id>/accept-direct -H "$H"             # pro asignado
curl -X POST localhost:8080/api/jobs/<id>/reject-direct -H "$H"             # pro asignado
curl -X POST localhost:8080/api/jobs/<id>/start -H "$H"                     # pro asignado: ACCEPTED -> IN_PROGRESS
curl -X POST localhost:8080/api/jobs/<id>/finish -H "$H" -H "Content-Type: application/json" -d "{\"stars\":5,\"comment\":\"Buen cliente\"}"   # pro asignado: IN_PROGRESS -> COMPLETED + califica al cliente (stars obligatorio)
```

Bids:

```bash
curl localhost:8080/api/jobs/<id>/bids -H "$H"                              # cliente ve todas; pro solo las suyas
curl -X POST localhost:8080/api/jobs/<id>/bids -H "$H" -H "Content-Type: application/json" -d "{\"amount\":45000,\"comment\":\"Voy hoy\",\"durationHours\":2}"
curl -X POST localhost:8080/api/bids/<bidId>/counter -H "$H" -H "Content-Type: application/json" -d "{\"amount\":40000}"
curl -X POST localhost:8080/api/bids/<bidId>/reject -H "$H"                 # cliente
curl -X POST localhost:8080/api/bids/<bidId>/accept -H "$H"                 # cliente (cualquiera) o pro (solo su oferta contraofertada por el cliente; asigna + rechaza resto)
```

Users:

```bash
curl localhost:8080/api/users/me -H "$H"
curl -X PATCH localhost:8080/api/users/me -H "$H" -H "Content-Type: application/json" -d "{\"phone\":\"300\",\"municipality\":\"Rionegro\"}"
curl localhost:8080/api/users/professionals -H "$H"
curl localhost:8080/api/users/<id> -H "$H"
curl -X POST localhost:8080/api/users/me/roles -H "$H" -H "Content-Type: application/json" -d "{\"role\":\"PROFESSIONAL\"}"
curl -X PATCH localhost:8080/api/users/me/password -H "$H" -H "Content-Type: application/json" -d "{\"currentPassword\":\"...\",\"newPassword\":\"...\"}"
curl -X DELETE localhost:8080/api/users/me -H "$H"   # 409 si tiene solicitudes
```

Ratings (`stars` 1-5, una por usuario y solicitud, solo en IN_PROGRESS/COMPLETED):

```bash
curl -X POST localhost:8080/api/jobs/<id>/ratings -H "$H" -H "Content-Type: application/json" -d "{\"stars\":5,\"comment\":\"Excelente\"}"
curl localhost:8080/api/jobs/<id>/ratings -H "$H"                # participantes
curl localhost:8080/api/users/<id>/ratings -H "$H"                # recibidas por un usuario
```

Geo extra: `GET /api/geo/tree`, `GET /api/geo/categories`, `POST /api/geo/categories`.
Bids extra: `GET /api/bids/mine`.
Jobs: `POST /api/jobs` acepta `id` opcional (formato `^[A-Za-z0-9_-]{1,64}$`) y `professionalId` opcional (crea NEGOTIATING para contratación directa). Rechaza con 409 si el cliente ya tiene una solicitud activa con igual categoría y ubicación (departamento + municipio + dirección, insensible a mayúsculas); otra dirección u otra categoría sí se permite. Las cerradas (COMPLETED/CANCELLED) no bloquean.

Reglas aplicadas en servidor: una sola oferta por (job, profesional) con id
estable `bid_<job>_<uid>`; aceptar/rechazar/cancelar/eliminar solo con rol y
estado válidos (`403` ajeno, `404` inexistente, `409` estado inválido).

## Estructura

- `config/` seguridad HTTP + BCrypt
- `security/` JWT (access 60 min + refresh 30 días)
- `auth/` registro, login, Google, refresh
- `users|jobs|bids|devices/` entidades + repositorios (los IDs string de Firestore se conservan como PK)
- `geo/` departamentos y municipios

## Notas F0

- Los usuarios de correo existentes en Firebase Auth deberán restablecer su
  contraseña una vez (los hashes scrypt no son migrables); los de Google no
  sienten el cambio (match por email).
- `departments`/`municipalities` se pueblan en la migración F4 desde Firestore.
- Push (FCM), storage y migración de datos llegan en F3/F4.

## Firebase (lo que queda)

`firestore.rules`, `firestore.indexes.json`, `firebase.json` y `.firebaserc`
viven aquí (alcance backend/infra). Desplegar reglas/índices desde esta carpeta:

```bat
firebase deploy --only firestore:rules
firebase deploy --only firestore:indexes
```

Alcance actual: `jobs/{id}/chats` + `typing` (tiempo real), Storage (blobs),
FCM (push, envío desde backend en F3). El resto de colecciones se apagan en F4.
