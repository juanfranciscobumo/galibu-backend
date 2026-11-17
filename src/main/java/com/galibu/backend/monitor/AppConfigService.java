package com.galibu.backend.monitor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * Configuración dinámica de la aplicación. Los valores se guardan en base de
 * datos y cada cambio queda registrado en {@code app_config_audit} para poder
 * auditar quién modificó qué y cuándo. Los umbrales de las alertas y los
 * feature flags se leen desde aquí con valores por defecto seguros.
 */
@Service
public class AppConfigService {

    private static final List<DefaultEntry> DEFAULTS = List.of(
        new DefaultEntry("alert.errorRate.warning", "2", "NUMBER", "Advertencia cuando la tasa de error supera este % (5 min)."),
        new DefaultEntry("alert.errorRate.critical", "5", "NUMBER", "Crítico cuando la tasa de error supera este % (5 min)."),
        new DefaultEntry("alert.serverErrorRate.critical", "1", "NUMBER", "Crítico cuando los 5xx superan este % (5 min)."),
        new DefaultEntry("alert.latency.warningMs", "2000", "NUMBER", "Advertencia cuando la latencia p95 supera estos ms."),
        new DefaultEntry("alert.latency.criticalMs", "5000", "NUMBER", "Crítico cuando la latencia p95 supera estos ms."),
        new DefaultEntry("alert.pool.warning", "75", "NUMBER", "Advertencia cuando el pool de BD supera este % de uso."),
        new DefaultEntry("alert.pool.critical", "90", "NUMBER", "Crítico cuando el pool de BD supera este % de uso."),
        new DefaultEntry("alert.heap.warning", "85", "NUMBER", "Advertencia cuando el heap supera este % de uso."),
        new DefaultEntry("alert.heap.critical", "95", "NUMBER", "Crítico cuando el heap supera este % de uso."),
        new DefaultEntry("alert.frontErrors.warning", "50", "NUMBER", "Advertencia cuando el buffer acumula más de N errores del front."),
        new DefaultEntry("alert.frontErrors.critical", "200", "NUMBER", "Crítico cuando el buffer acumula más de N errores del front."),
        new DefaultEntry("alert.jobsWithoutBids.warning", "1", "NUMBER", "Advertencia si hay N o más solicitudes abiertas sin oferta."),
        new DefaultEntry("feature.profile360", "true", "BOOLEAN", "Habilita la pestaña Perfil 360 en el monitor."),
        new DefaultEntry("feature.alerts", "true", "BOOLEAN", "Habilita la pestaña Alertas en el monitor."),
        new DefaultEntry("feature.cohorts", "true", "BOOLEAN", "Muestra las cohortes de registro en Estadísticas."),
        new DefaultEntry("app.version", "1.0.0", "STRING", "Versión mostrada en el footer del monitor."),
        new DefaultEntry("app.slogan", "Servicios que conectan, soluciones que llegan.", "STRING", "Slogan mostrado en el monitor."),
        new DefaultEntry("app.maintenanceMode", "false", "BOOLEAN", "Modo mantenimiento: muestra aviso en el monitor.")
    );

    private final JdbcTemplate jdbc;

    public AppConfigService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void init() {
        long now = System.currentTimeMillis();
        for (DefaultEntry entry : DEFAULTS) {
            try {
                Integer count = jdbc.queryForObject(
                    "select count(*) from app_config where config_key = ?",
                    Integer.class, entry.key);
                if (count == null || count == 0) {
                    jdbc.update(
                        "insert into app_config (config_key, config_value, value_type, description, updated_at, updated_by)"
                            + " values (?, ?, ?, ?, ?, 'system')",
                        entry.key, entry.value, entry.type, entry.description, now);
                }
            } catch (Exception ignored) {
                // Si la tabla aún no existe (migración pendiente) se omite.
            }
        }
    }

    public List<Map<String, Object>> list() {
        try {
            return jdbc.queryForList(
                "select config_key, config_value, value_type, description, updated_at, updated_by"
                    + " from app_config order by config_key");
        } catch (Exception ex) {
            return List.of();
        }
    }

    public Map<String, Object> get(String key) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "select config_key, config_value, value_type, description, updated_at, updated_by"
                    + " from app_config where config_key = ?",
                key);
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception ex) {
            return null;
        }
    }

    public String getString(String key, String fallback) {
        Map<String, Object> row = get(key);
        if (row == null) {
            return fallback;
        }
        Object value = row.get("config_value");
        return value == null ? fallback : String.valueOf(value);
    }

    public double getDouble(String key, double fallback) {
        String value = getString(key, null);
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public boolean getBoolean(String key, boolean fallback) {
        String value = getString(key, null);
        if (value == null) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase();
        if ("true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized) || "0".equals(normalized) || "no".equals(normalized)) {
            return false;
        }
        return fallback;
    }

    public Map<String, Object> update(String key, String value, String changedBy) {
        Map<String, Object> current = get(key);
        if (current == null) {
            throw new IllegalArgumentException("Config key not found: " + key);
        }
        String oldValue = String.valueOf(current.get("config_value"));
        String type = String.valueOf(current.get("value_type"));
        String normalized = validate(value, type);
        String actor = (changedBy == null || changedBy.isBlank()) ? "monitor" : changedBy;
        long now = System.currentTimeMillis();
        jdbc.update(
            "update app_config set config_value = ?, updated_at = ?, updated_by = ? where config_key = ?",
            normalized, now, actor, key);
        try {
            jdbc.update(
                "insert into app_config_audit (config_key, old_value, new_value, changed_at, changed_by)"
                    + " values (?, ?, ?, ?, ?)",
                key, oldValue, normalized, now, actor);
        } catch (Exception ignored) {
        }
        return get(key);
    }

    public List<Map<String, Object>> audit(int limit) {
        int cap = Math.max(1, Math.min(limit, 500));
        try {
            return jdbc.queryForList(
                "select id, config_key, old_value, new_value, changed_at, changed_by"
                    + " from app_config_audit order by id desc limit ?",
                cap);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String validate(String value, String type) {
        if (value == null) {
            throw new IllegalArgumentException("Value cannot be null");
        }
        String trimmed = value.trim();
        if ("NUMBER".equalsIgnoreCase(type)) {
            try {
                Double.parseDouble(trimmed);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Value must be a number");
            }
        } else if ("BOOLEAN".equalsIgnoreCase(type)) {
            String n = trimmed.toLowerCase();
            if (!("true".equals(n) || "false".equals(n) || "1".equals(n) || "0".equals(n))) {
                throw new IllegalArgumentException("Value must be true/false");
            }
            return "true".equals(n) || "1".equals(n) ? "true" : "false";
        }
        return trimmed;
    }

    private record DefaultEntry(String key, String value, String type, String description) {
    }
}
