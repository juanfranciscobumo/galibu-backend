package com.galibu.backend.monitor;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.AppenderBase;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Guarda en memoria los últimos eventos de log para mostrarlos en el
 * dashboard. Se engancha al logger raíz de Logback al arrancar.
 */
@Component
public class LogCapture extends AppenderBase<ILoggingEvent> {

    private static final int MAX_ENTRIES = 600;
    private static final int MAX_MESSAGE = 4000;
    private static final int MAX_EXCEPTION = 1800;

    private final ArrayDeque<Entry> entries = new ArrayDeque<>();
    private long infoCount;
    private long warnCount;
    private long errorCount;
    private long captured;

    @PostConstruct
    void register() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        setContext(context);
        setName("galibu-monitor");
        start();
        context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(this);
    }

    @PreDestroy
    void unregister() {
        if (getContext() instanceof LoggerContext context) {
            context.getLogger(Logger.ROOT_LOGGER_NAME).detachAppender(this);
        }
        stop();
    }

    @Override
    protected synchronized void append(ILoggingEvent event) {
        String requestId = event.getMDCPropertyMap() == null
            ? null
            : event.getMDCPropertyMap().get("requestId");
        Entry entry = new Entry(
            event.getTimeStamp(),
            event.getLevel().toString(),
            shortenLogger(event.getLoggerName()),
            truncate(event.getFormattedMessage(), MAX_MESSAGE),
            throwable(event.getThrowableProxy()),
            requestId
        );
        entries.addLast(entry);
        while (entries.size() > MAX_ENTRIES) {
            entries.removeFirst();
        }
        captured++;
        switch (event.getLevel().toInt()) {
            case Level.ERROR_INT -> errorCount++;
            case Level.WARN_INT -> warnCount++;
            case Level.INFO_INT -> infoCount++;
            default -> { }
        }
    }

    /** Eventos más recientes primero, filtrados por nivel mínimo. */
    public synchronized List<Map<String, Object>> recent(String minLevel, int limit) {
        int threshold = switch (minLevel == null ? "ALL" : minLevel.toUpperCase()) {
            case "ERROR" -> Level.ERROR_INT;
            case "WARN" -> Level.WARN_INT;
            case "INFO" -> Level.INFO_INT;
            default -> Level.TRACE_INT;
        };
        List<Map<String, Object>> out = new ArrayList<>();
        List<Entry> copy = new ArrayList<>(entries);
        for (int i = copy.size() - 1; i >= 0 && out.size() < limit; i--) {
            Entry entry = copy.get(i);
            if (levelToInt(entry.level()) >= threshold) {
                out.add(entry.toMap());
            }
        }
        return out;
    }

    public synchronized Map<String, Object> summary() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("buffered", entries.size());
        out.put("captured", captured);
        out.put("info", infoCount);
        out.put("warns", warnCount);
        out.put("errors", errorCount);
        return out;
    }

    private static int levelToInt(String level) {
        return switch (level) {
            case "ERROR" -> Level.ERROR_INT;
            case "WARN" -> Level.WARN_INT;
            case "INFO" -> Level.INFO_INT;
            case "DEBUG" -> Level.DEBUG_INT;
            case "TRACE" -> Level.TRACE_INT;
            default -> Level.INFO_INT;
        };
    }

    private static String shortenLogger(String name) {
        if (name == null) {
            return "";
        }
        if (name.startsWith("com.galibu.backend.")) {
            return "galibu." + name.substring("com.galibu.backend.".length());
        }
        if (name.startsWith("org.springframework.")) {
            return "spring." + name.substring("org.springframework.".length());
        }
        if (name.startsWith("com.zaxxer.hikari.")) {
            return "hikari." + name.substring("com.zaxxer.hikari.".length());
        }
        if (name.startsWith("org.hibernate.")) {
            return "hibernate." + name.substring("org.hibernate.".length());
        }
        return name;
    }

    private static String throwable(IThrowableProxy proxy) {
        if (proxy == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(proxy.getClassName());
        if (proxy.getMessage() != null) {
            sb.append(": ").append(proxy.getMessage());
        }
        StackTraceElementProxy[] stack = proxy.getStackTraceElementProxyArray();
        int shown = 0;
        if (stack != null) {
            for (StackTraceElementProxy element : stack) {
                if (shown++ >= 6) {
                    sb.append("\n  ...");
                    break;
                }
                sb.append("\n  at ").append(element.getSTEAsString());
            }
        }
        return truncate(sb.toString(), MAX_EXCEPTION);
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    private record Entry(
        long at,
        String level,
        String logger,
        String message,
        String exception,
        String requestId
    ) {
        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("at", at);
            map.put("level", level);
            map.put("logger", logger);
            map.put("message", message);
            map.put("exception", exception);
            map.put("requestId", requestId);
            return map;
        }
    }
}
