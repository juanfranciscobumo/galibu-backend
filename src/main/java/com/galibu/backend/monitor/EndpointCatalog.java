package com.galibu.backend.monitor;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Catálogo de todas las rutas {@code /api/**} registradas por Spring MVC.
 *
 * <p>El dashboard lo combina con {@link RequestMetrics} para mostrar también
 * las APIs que no han recibido ninguna petición desde el arranque. Las rutas
 * con variables de path se normalizan a {@code {id}} para que coincidan con
 * las métricas (ej. {@code /api/jobs/{jobId}} → {@code /api/jobs/{id}}).</p>
 */
@Component
public class EndpointCatalog {

    private final ApplicationContext context;
    private volatile List<Endpoint> endpoints = List.of();
    private volatile boolean loaded = false;

    public EndpointCatalog(ApplicationContext context) {
        this.context = context;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void load() {
        synchronized (this) {
            if (loaded) {
                return;
            }
            RequestMappingHandlerMapping mapping =
                context.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
            Set<Endpoint> found = new LinkedHashSet<>();
            for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mapping.getHandlerMethods().entrySet()) {
                RequestMappingInfo info = entry.getKey();
                Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
                for (String pattern : info.getPatternValues()) {
                    String path = normalizePattern(pattern);
                    if (!path.startsWith("/api/") || path.startsWith("/api/monitor")) {
                        continue;
                    }
                    if (methods.isEmpty()) {
                        found.add(new Endpoint("ANY", path));
                    } else {
                        for (RequestMethod method : methods) {
                            found.add(new Endpoint(method.name(), path));
                        }
                    }
                }
            }
            endpoints = List.copyOf(found);
            loaded = true;
        }
    }

    public List<Endpoint> endpoints() {
        if (!loaded) {
            load();
        }
        return endpoints;
    }

    /**
     * Reemplaza cualquier variable de path por {@code {id}} para alinearla con
     * {@link RequestMetrics#normalize(String)}.
     */
    static String normalizePattern(String pattern) {
        String[] parts = pattern.split("/");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            out.append('/').append(part.startsWith("{") ? "{id}" : part);
        }
        return out.length() == 0 ? "/" : out.toString();
    }

    public record Endpoint(String method, String path) {
        public String key() {
            return method + " " + path;
        }
    }
}
