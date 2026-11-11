package com.galibu.backend.subscription;

import com.galibu.backend.jobs.JobRepository;
import com.galibu.backend.subscription.dto.SubscriptionDto;
import com.galibu.backend.users.User;
import com.galibu.backend.users.UserRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Plan del profesional.
 *
 * Modelo: el cliente nunca paga. El profesional puede completar sus primeros
 * {@value #FREE_SERVICES} servicios sin suscribirse. A partir del siguiente
 * necesita un plan Pro activo. Un "servicio" es un trabajo que llegó a ser
 * aceptado (ACCEPTED, IN_PROGRESS o COMPLETED): así nadie puede acumular
 * ofertas aceptadas sin plan y luego reclamar servicios gratis ilimitados.
 */
@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    public static final int FREE_SERVICES = 3;

    public static final String PLAN_MONTHLY = "MONTHLY";
    public static final String PLAN_YEARLY = "YEARLY";

    public static final double MONTHLY_PRICE = 29900.0;
    public static final double YEARLY_PRICE = 299000.0;

    private static final long DAY_MS = 24L * 60L * 60L * 1000L;

    /** Trabajos que consumen la cuota gratuita. */
    private static final List<String> SERVICE_STATUSES =
        List.of("ACCEPTED", "IN_PROGRESS", "COMPLETED");

    private final UserRepository users;
    private final JobRepository jobs;
    private final SubscriptionRepository subscriptions;

    public SubscriptionService(
        UserRepository users,
        JobRepository jobs,
        SubscriptionRepository subscriptions
    ) {
        this.users = users;
        this.jobs = jobs;
        this.subscriptions = subscriptions;
    }

    @Transactional
    public SubscriptionDto me(String uid) {
        User user = findOr404(uid);
        refreshExpiry(user);
        return toDto(user, usedServices(uid));
    }

    /**
     * Gate que se aplica antes de que el profesional tome un servicio nuevo
     * (ofertar o aceptar una contratación directa). Lanza 402 para que la app
     * abra el paywall; el cliente nunca ve este error porque no oferta.
     */
    @Transactional
    public void assertCanWork(String uid) {
        User user = findOr404(uid);
        refreshExpiry(user);
        if (hasActiveAccess(user)) {
            return;
        }
        int used = usedServices(uid);
        if (used < FREE_SERVICES) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
            "Usaste tus " + FREE_SERVICES + " servicios gratis. Activa Galibu Pro "
                + "para seguir tomando trabajos.");
    }

    /** Variante sin excepción para validaciones internas (ej. aceptar una oferta). */
    @Transactional
    public boolean canWork(String uid) {
        User user = findOr404(uid);
        refreshExpiry(user);
        return hasActiveAccess(user) || usedServices(uid) < FREE_SERVICES;
    }

    public int usedServices(String uid) {
        return (int) jobs.countServicesByProfessionalId(uid, SERVICE_STATUSES);
    }

    /**
     * Activa (o extiende) el plan Pro tras confirmar el pago en Bold. Si el
     * usuario ya tenía acceso vigente, el nuevo periodo se suma al final.
     */
    @Transactional
    public SubscriptionDto activate(String uid, String plan, String boldOrderId, double amount) {
        String normalizedPlan = normalizePlan(plan);
        if (boldOrderId != null
            && subscriptions.findByBoldOrderId(boldOrderId).isPresent()) {
            // Idempotente: el polling y el webhook pueden confirmar el mismo pago.
            return me(uid);
        }
        User user = findOr404(uid);
        refreshExpiry(user);
        long now = System.currentTimeMillis();
        long base = hasActiveAccess(user) && user.getSubscriptionExpiresAt() != null
            ? user.getSubscriptionExpiresAt()
            : now;
        long period = PLAN_YEARLY.equals(normalizedPlan) ? 365L * DAY_MS : 30L * DAY_MS;
        long expiresAt = base + period;

        user.setSubscriptionPlan("PRO");
        user.setSubscriptionStatus("ACTIVE");
        user.setSubscriptionExpiresAt(expiresAt);
        users.save(user);

        Subscription subscription = new Subscription();
        subscription.setId("sub_" + UUID.randomUUID().toString().replace("-", ""));
        subscription.setUserId(uid);
        subscription.setPlan(normalizedPlan);
        subscription.setStatus("ACTIVE");
        subscription.setAmount(amount);
        subscription.setStartedAt(now);
        subscription.setExpiresAt(expiresAt);
        subscription.setBoldOrderId(boldOrderId);
        subscriptions.save(subscription);

        log.info("Suscripción activada user={} plan={} expiresAt={} order={}",
            uid, normalizedPlan, expiresAt, boldOrderId);
        return toDto(user, usedServices(uid));
    }

    @Transactional
    public SubscriptionDto cancel(String uid) {
        User user = findOr404(uid);
        refreshExpiry(user);
        if (!hasActiveAccess(user)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "No tienes una suscripción activa.");
        }
        // Cancelar no quita el acceso ya pagado: el plan sigue vigente hasta el
        // final del periodo y luego expira solo.
        user.setSubscriptionStatus("CANCELLED");
        users.save(user);
        log.info("Suscripción cancelada user={} expiresAt={}", uid, user.getSubscriptionExpiresAt());
        return toDto(user, usedServices(uid));
    }

    public static double priceFor(String plan) {
        return PLAN_YEARLY.equals(normalizePlanStatic(plan)) ? YEARLY_PRICE : MONTHLY_PRICE;
    }

    private static String normalizePlanStatic(String plan) {
        String upper = plan == null ? "" : plan.trim().toUpperCase();
        if (PLAN_YEARLY.equals(upper)) {
            return PLAN_YEARLY;
        }
        if (PLAN_MONTHLY.equals(upper)) {
            return PLAN_MONTHLY;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "Plan inválido (MONTHLY o YEARLY).");
    }

    private String normalizePlan(String plan) {
        return normalizePlanStatic(plan);
    }

    private boolean hasActiveAccess(User user) {
        Long expiresAt = user.getSubscriptionExpiresAt();
        if (expiresAt == null || expiresAt <= System.currentTimeMillis()) {
            return false;
        }
        return "ACTIVE".equals(user.getSubscriptionStatus())
            || "CANCELLED".equals(user.getSubscriptionStatus());
    }

    private void refreshExpiry(User user) {
        Long expiresAt = user.getSubscriptionExpiresAt();
        if (expiresAt != null
            && expiresAt <= System.currentTimeMillis()
            && !"EXPIRED".equals(user.getSubscriptionStatus())
            && !"NONE".equals(user.getSubscriptionStatus())) {
            user.setSubscriptionStatus("EXPIRED");
            user.setSubscriptionPlan("FREE");
            users.save(user);
        }
    }

    private SubscriptionDto toDto(User user, int used) {
        boolean active = hasActiveAccess(user);
        int remaining = Math.max(0, FREE_SERVICES - used);
        return new SubscriptionDto(
            active ? "PRO" : "FREE",
            user.getSubscriptionStatus(),
            user.getSubscriptionExpiresAt(),
            active,
            active || remaining > 0,
            FREE_SERVICES,
            used,
            remaining,
            MONTHLY_PRICE,
            YEARLY_PRICE
        );
    }

    private User findOr404(String uid) {
        return users.findById(uid).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no existe"));
    }
}
