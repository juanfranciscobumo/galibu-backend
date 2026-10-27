# Monitor del backend

Dashboard interno para ver el estado del backend y de la base de datos en vivo.

## Acceso

- URL: `http://<host-del-backend>:8080/monitor` (por ejemplo `http://192.168.1.9:8080/monitor`)
- Usuario por defecto: `monitor`
- Contraseña por defecto: `galibu_monitor_2026`

Se puede cambiar con variables de entorno al arrancar:

```bash
MONITOR_USER=admin MONITOR_PASSWORD=otra-clave ./gradlew bootRun
```

O en `application.yml`:

```yaml
app:
  monitor:
    username: monitor
    password: galibu_monitor_2026
```

> Cambiar la contraseña por defecto en producción. El dashboard tiene su propia
> cadena de seguridad (sesión con formulario) y no afecta la autenticación JWT
> de la app móvil.

## Qué muestra

| Sección | Contenido |
|---|---|
| KPIs | Peticiones totales, peticiones/segundo (promedio 5 s), tasa de error, latencia promedio, p95, conexiones del pool y heap de la JVM |
| Tráfico 60 s | Peticiones por segundo y errores (4xx/5xx) de los últimos 60 segundos |
| Latencia 60 s | Latencia promedio y máxima por segundo |
| Tráfico 2 h | Peticiones por minuto de las últimas 2 horas |
| Servidor / JVM | Heap, CPU del proceso y del sistema, hilos, GC, clases, núcleos y RAM libre |
| Endpoints | Todas las rutas `/api/**` registradas por Spring (incluidas las que tienen 0 peticiones) con peticiones, porcentaje, promedio, p95, máximo y errores. Filtros **Todas / Sin uso / Poco uso** (<10 peticiones) y marcas por fila |
| Base de datos | Estado del pool Hikari, latencia del `SELECT 1`, consultas totales y QPS, conexiones, consultas lentas, filas por tabla y tamaño |
| Errores recientes | Últimos 150 errores (4xx/5xx) con hora, método, API, código, duración, cliente y el mensaje devuelto por el backend |
| Últimas peticiones | Feed en vivo con método, ruta, código de estado, duración y cliente |
| Logs | Últimos 600 eventos en memoria, con filtro por nivel (INFO/WARN/ERROR) y trazas de excepción |

## Notas

- Las métricas viven **en memoria**: se reinician cuando se reinicia el backend.
  El botón "Reiniciar métricas" solo borra los contadores, no los logs.
- Las peticiones del propio dashboard y de `/actuator` **no se contabilizan**.
- Las rutas dinámicas se agrupan: `/api/jobs/job_abc123/bids` aparece como
  `/api/jobs/{id}/bids`.
- `/favicon.ico` y el tráfico del dashboard no se contabilizan. Las páginas del
  monitor incluyen su propio icono para que el navegador no lo pida.
- El detalle de un error es el campo `message` que devuelve la API; si la
  respuesta no trae cuerpo (por ejemplo un 401 de seguridad) se muestra un texto
  genérico según el código.
- `server.error.include-message=always` hace que los 500 también devuelvan el
  mensaje de la excepción (útil en desarrollo).
- Los logs también se guardan en `logs/galibu-backend.log` (rotación de 10 MB,
  7 archivos).
- El endpoint JSON que usa la página es `GET /api/monitor/data` (requiere la
  sesión del monitor). `GET /api/monitor/logs?level=WARN&limit=200` devuelve los
  logs y `POST /api/monitor/reset` reinicia los contadores.
