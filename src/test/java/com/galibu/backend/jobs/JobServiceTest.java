package com.galibu.backend.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.galibu.backend.ratings.RatingService;
import com.galibu.backend.jobs.dto.CreateJobRequest;
import com.galibu.backend.jobs.dto.JobDto;
import com.galibu.backend.subscription.SubscriptionService;
import com.galibu.backend.users.User;
import com.galibu.backend.users.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobRepository jobs;

    @Mock
    private UserRepository users;

    @Mock
    private RatingService ratingService;

    @Mock
    private SubscriptionService subscriptions;

    @Mock
    private com.galibu.backend.chat.ChatService chat;

    @Mock
    private com.galibu.backend.notifications.NotificationService notifications;

    @InjectMocks
    private JobService service;

    private User client(String id) {
        User u = new User();
        u.setId(id);
        u.setName("Cli");
        u.setEmail(id + "@t.com");
        u.setRole("CLIENT");
        return u;
    }

    private Job job(String id) {
        Job j = new Job();
        j.setId(id);
        j.setTitle("T");
        j.setStatus("OPEN");
        j.setClientId("client-1");
        j.setClientName("Cli");
        j.setBudgetMin(50000.0);
        return j;
    }

    @Test
    void createSetsClientFromTokenAndRejectsNonClients() {
        when(users.findById("client-1")).thenReturn(Optional.of(client("client-1")));
        when(jobs.save(any(Job.class))).thenAnswer(i -> i.getArgument(0));
        CreateJobRequest req = new CreateJobRequest();
        req.setTitle("Plomero");
        req.setCategory("Plomería");
        req.setDescription("fuga en la cocina");
        req.setSuggestedPrice(20000.0);

        JobDto created = service.create("client-1", req);

        assertEquals("client-1", created.getClientId());
        assertEquals("OPEN", created.getStatus());
        assertEquals("Cli", created.getClientName());
        assertEquals("Fuga en la cocina", created.getDescription());
        assertEquals(20000.0, created.getSuggestedPrice());

        User pro = client("pro-1");
        pro.setRole("PROFESSIONAL");
        when(users.findById("pro-1")).thenReturn(Optional.of(pro));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.create("pro-1", req));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void getAllowsPublicJobsAndRestrictsPrivateOnes() {
        Job open = job("j1");
        when(jobs.findById("j1")).thenReturn(Optional.of(open));
        assertEquals("j1", service.get("stranger", "j1").getId());

        Job neg = job("j2");
        neg.setStatus("NEGOTIATING");
        when(jobs.findById("j2")).thenReturn(Optional.of(neg));
        assertEquals("j2", service.get("stranger", "j2").getId());

        Job assigned = job("j3");
        assigned.setStatus("NEGOTIATING");
        assigned.setProfessionalId("pro-1");
        when(jobs.findById("j3")).thenReturn(Optional.of(assigned));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.get("stranger", "j3"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void cancelGuardsParticipantAndClosedStates() {
        Job j = job("j1");
        when(jobs.findById("j1")).thenReturn(Optional.of(j));
        when(jobs.save(any(Job.class))).thenAnswer(i -> i.getArgument(0));
        when(users.findById("client-1")).thenReturn(Optional.of(client("client-1")));

        assertEquals("CANCELLED", service.cancel("client-1", "j1", null).getStatus());

        ResponseStatusException forbidden = assertThrows(ResponseStatusException.class,
            () -> service.cancel("other", "j1", null));
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());

        j.setStatus("COMPLETED");
        ResponseStatusException conflict = assertThrows(ResponseStatusException.class,
            () -> service.cancel("client-1", "j1", null));
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());

        j.setStatus("IN_PROGRESS");
        ResponseStatusException inProgress = assertThrows(ResponseStatusException.class,
            () -> service.cancel("client-1", "j1", null));
        assertEquals(HttpStatus.CONFLICT, inProgress.getStatusCode());
    }

    @Test
    void startServicePostsSystemChatMessage() {
        Job j = job("j1");
        j.setStatus("ACCEPTED");
        j.setProfessionalId("pro-1");
        j.setVerificationCode("123456");
        when(jobs.findById("j1")).thenReturn(Optional.of(j));
        when(jobs.save(any(Job.class))).thenAnswer(i -> i.getArgument(0));

        service.startService("pro-1", "j1", "123456");

        verify(chat).postSystemMessage(eq("j1"), eq("Sistema"),
            org.mockito.ArgumentMatchers.contains("inició el servicio"));
    }

    @Test
    void cancelPostsReasonInSystemChatMessage() {
        Job j = job("j1");
        j.setStatus("ACCEPTED");
        j.setProfessionalId("pro-1");
        when(jobs.findById("j1")).thenReturn(Optional.of(j));
        when(jobs.save(any(Job.class))).thenAnswer(i -> i.getArgument(0));
        when(users.findById("client-1")).thenReturn(Optional.of(client("client-1")));

        service.cancel("client-1", "j1", "no era lo acordado");

        verify(chat).postSystemMessage(eq("j1"), eq("Sistema"),
            org.mockito.ArgumentMatchers.contains("no era lo acordado"));
    }





    @Test
    void createWithProfessionalAssignsAndNegotiates() {
        when(users.findById("client-1")).thenReturn(Optional.of(client("client-1")));
        User pro = client("pro-1");
        pro.setRole("PROFESSIONAL");
        when(users.findById("pro-1")).thenReturn(Optional.of(pro));
        when(jobs.save(any(Job.class))).thenAnswer(i -> i.getArgument(0));
        CreateJobRequest req = new CreateJobRequest();
        req.setTitle("Directa");
        req.setProfessionalId("pro-1");

        JobDto created = service.create("client-1", req);

        assertEquals("NEGOTIATING", created.getStatus());
        assertEquals("pro-1", created.getProfessionalId());

        User notPro = client("other");
        when(users.findById("other")).thenReturn(Optional.of(notPro));
        CreateJobRequest bad = new CreateJobRequest();
        bad.setTitle("X");
        bad.setProfessionalId("other");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.create("client-1", bad));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void createHonorsClientProvidedIdAndRejectsDuplicates() {
        when(users.findById("client-1")).thenReturn(Optional.of(client("client-1")));
        when(jobs.existsById("chat_pro-1_123")).thenReturn(false);
        when(jobs.save(any(Job.class))).thenAnswer(i -> i.getArgument(0));
        CreateJobRequest req = new CreateJobRequest();
        req.setTitle("Chat directo");
        req.setId("chat_pro-1_123");

        JobDto created = service.create("client-1", req);

        assertEquals("chat_pro-1_123", created.getId());
        assertEquals("OPEN", created.getStatus());

        when(jobs.existsById("chat_pro-1_123")).thenReturn(true);
        ResponseStatusException dup = assertThrows(ResponseStatusException.class,
            () -> service.create("client-1", req));
        assertEquals(HttpStatus.CONFLICT, dup.getStatusCode());
    }

    @Test
    void createRejectsDuplicateActiveLocationCategory() {
        when(users.findById("client-1")).thenReturn(Optional.of(client("client-1")));
        Job existing = job("j0");
        existing.setStatus("OPEN");
        existing.setCategory("Plomería");
        existing.setDepartment("Antioquia");
        existing.setMunicipality("Rionegro");
        existing.setAddress("Cl 1");
        when(jobs.findByClientIdOrderByCreatedAtDesc("client-1")).thenReturn(List.of(existing));

        CreateJobRequest req = new CreateJobRequest();
        req.setTitle("Plomero");
        req.setCategory("Plomería");
        req.setDepartment("antioquia");
        req.setMunicipality("RIONEGRO");
        req.setAddress("  cl 1 ");
        ResponseStatusException dup = assertThrows(ResponseStatusException.class,
            () -> service.create("client-1", req));
        assertEquals(HttpStatus.CONFLICT, dup.getStatusCode());

        // Otra dirección sí se permite.
        req.setAddress("Cl 2");
        when(jobs.save(any(Job.class))).thenAnswer(i -> i.getArgument(0));
        JobDto created = service.create("client-1", req);
        assertEquals("OPEN", created.getStatus());

        // Cerradas no bloquean.
        existing.setStatus("CANCELLED");
        req.setAddress("Cl 1");
        JobDto created2 = service.create("client-1", req);
        assertEquals("OPEN", created2.getStatus());
    }

    @Test
    void createAllowsMultipleChatThreadsInSameLocation() {
        // Los hilos de consulta (chat_*) no son solicitudes de servicio y no
        // deben bloquearse por duplicado de ubicación/categoría: un cliente
        // puede consultar a varios profesionales de la misma zona.
        when(users.findById("client-1")).thenReturn(Optional.of(client("client-1")));
        User pro1 = client("pro-1");
        pro1.setRole("PROFESSIONAL");
        User pro2 = client("pro-2");
        pro2.setRole("PROFESSIONAL");
        when(users.findById("pro-1")).thenReturn(Optional.of(pro1));
        when(users.findById("pro-2")).thenReturn(Optional.of(pro2));
        Job existing = job("j0");
        existing.setStatus("OPEN");
        existing.setCategory("Plomería");
        existing.setDepartment("Antioquia");
        existing.setMunicipality("Rionegro");
        existing.setAddress("Cl 1");
        when(jobs.save(any(Job.class))).thenAnswer(i -> i.getArgument(0));

        CreateJobRequest first = new CreateJobRequest();
        first.setId("chat_" + "a".repeat(32));
        first.setTitle("Consulta");
        first.setCategory("Plomería");
        first.setDepartment("Antioquia");
        first.setMunicipality("Rionegro");
        first.setAddress("Cl 1");
        first.setProfessionalId("pro-1");
        JobDto created = service.create("client-1", first);
        assertEquals("NEGOTIATING", created.getStatus());

        CreateJobRequest second = new CreateJobRequest();
        second.setId("chat_" + "b".repeat(32));
        second.setTitle("Consulta");
        second.setCategory("Plomería");
        second.setDepartment("Antioquia");
        second.setMunicipality("Rionegro");
        second.setAddress("Cl 1");
        second.setProfessionalId("pro-2");
        JobDto created2 = service.create("client-1", second);
        assertEquals("NEGOTIATING", created2.getStatus());
    }

    @Test
    void listEnforcesFilters() {
        when(jobs.findByClientIdOrProfessionalIdOrderByCreatedAtDesc("u", "u"))
            .thenReturn(List.of(job("j1")));

        assertEquals(1, service.list("u", null, null, null).size());

        ResponseStatusException e1 = assertThrows(ResponseStatusException.class,
            () -> service.list("u", "other", null, null));
        assertEquals(HttpStatus.FORBIDDEN, e1.getStatusCode());

        ResponseStatusException e2 = assertThrows(ResponseStatusException.class,
            () -> service.list("u", null, null, "NEGOTIATING"));
        assertEquals(HttpStatus.FORBIDDEN, e2.getStatusCode());

        verify(jobs).findByClientIdOrProfessionalIdOrderByCreatedAtDesc("u", "u");
    }

    @Test
    void startAndFinishServiceGuardStates() {
        Job j = job("j1");
        j.setStatus("ACCEPTED");
        j.setProfessionalId("pro-1");
        when(jobs.findById("j1")).thenReturn(Optional.of(j));
        when(jobs.save(any(Job.class))).thenAnswer(i -> i.getArgument(0));

        j.setVerificationCode("123456");
        JobDto started = service.startService("pro-1", "j1", "123456");
        assertEquals("IN_PROGRESS", started.getStatus());

        ResponseStatusException notAccepted = assertThrows(ResponseStatusException.class,
            () -> service.startService("pro-1", "j1", "123456"));
        assertEquals(HttpStatus.CONFLICT, notAccepted.getStatusCode());

        ResponseStatusException forbidden = assertThrows(ResponseStatusException.class,
            () -> service.finishService("other", "j1", 5, null));
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());

        // stars=5 es una calificación válida
        JobDto finished = service.finishService("pro-1", "j1", 5, "Buen cliente");
        assertEquals("COMPLETED", finished.getStatus());
        verify(ratingService).rate(eq("pro-1"), eq("j1"), any(com.galibu.backend.ratings.dto.CreateRatingRequest.class));

        // Segundo participante: el servicio ya está COMPLETED, solo se registra
        // su calificación (sin repetir mensajes de sistema ni notificaciones).
        JobDto finishedByClient = service.finishService("client-1", "j1", 4, "Buen profesional");
        assertEquals("COMPLETED", finishedByClient.getStatus());
        verify(ratingService).rate(eq("client-1"), eq("j1"), any(com.galibu.backend.ratings.dto.CreateRatingRequest.class));
    }

    @Test
    void finishAllowsOptionalRating() {
        // stars=null (sin calificar) es válido
        Job j = job("j1");
        j.setStatus("IN_PROGRESS");
        j.setProfessionalId("pro-1");
        when(jobs.findById("j1")).thenReturn(Optional.of(j));
        when(jobs.save(any(Job.class))).thenAnswer(i -> i.getArgument(0));

        JobDto finished = service.finishService("pro-1", "j1", null, null);
        assertEquals("COMPLETED", finished.getStatus());
        // Sin calificación no se llama al ratingService
        verify(ratingService, org.mockito.Mockito.never()).rate(any(), any(), any());
    }


    @Test
    void applyCreatesChildApplicationAndIsIdempotent() {
        Job parent = job("parent");
        parent.setStatus("OPEN");
        parent.setClientId("client-1");
        when(jobs.findById("parent")).thenReturn(Optional.of(parent));
        User pro = client("pro-1");
        pro.setRole("PROFESSIONAL");
        when(users.findById("pro-1")).thenReturn(Optional.of(pro));
        when(jobs.findByParentJobIdAndProfessionalId("parent", "pro-1")).thenReturn(List.of());
        when(jobs.save(any(Job.class))).thenAnswer(i -> i.getArgument(0));

        JobDto app = service.apply("pro-1", "parent");
        assertTrue(app.getId().startsWith("app_"));
        assertEquals("parent", app.getParentJobId());
        assertEquals("NEGOTIATING", app.getStatus());
        assertEquals("pro-1", app.getProfessionalId());

        // Idempotente: si ya existe una postulación activa, se devuelve la misma.
        Job existing = job("app_existing");
        existing.setParentJobId("parent");
        existing.setProfessionalId("pro-1");
        existing.setStatus("NEGOTIATING");
        when(jobs.findByParentJobIdAndProfessionalId("parent", "pro-1")).thenReturn(List.of(existing));
        when(jobs.findById("parent")).thenReturn(Optional.of(parent));
        JobDto again = service.apply("pro-1", "parent");
        assertEquals("app_existing", again.getId());
    }

    @Test
    void clientAcceptClosesSiblingApplications() {
        Job child = job("app_1");
        child.setParentJobId("parent");
        child.setStatus("NEGOTIATING");
        child.setProfessionalId("pro-1");
        child.setClientId("client-1");
        Job parent = job("parent");
        parent.setStatus("OPEN");
        parent.setClientId("client-1");
        when(jobs.findById("app_1")).thenReturn(Optional.of(child));
        when(jobs.findById("parent")).thenReturn(Optional.of(parent));

        Job sibling = job("app_2");
        sibling.setParentJobId("parent");
        sibling.setStatus("NEGOTIATING");
        sibling.setProfessionalId("pro-2");
        when(jobs.findByParentJobIdOrderByCreatedAtDesc("parent")).thenReturn(List.of(child, sibling));
        when(jobs.save(any(Job.class))).thenAnswer(i -> i.getArgument(0));

        JobDto accepted = service.clientAccept("client-1", "app_1");
        assertEquals("ACCEPTED", accepted.getStatus());
        verify(chat).postSystemMessage(eq("app_2"), eq("Sistema"),
            org.mockito.ArgumentMatchers.contains("otro profesional"));
    }

    @Test
    void rejectApplicationClosesOnlyThatConversation() {
        Job child = job("app_1");
        child.setParentJobId("parent");
        child.setStatus("NEGOTIATING");
        child.setProfessionalId("pro-1");
        child.setClientId("client-1");
        Job parent = job("parent");
        parent.setStatus("OPEN");
        parent.setClientId("client-1");
        when(jobs.findById("app_1")).thenReturn(Optional.of(child));
        when(jobs.save(any(Job.class))).thenAnswer(i -> i.getArgument(0));

        JobDto rejected = service.rejectApplication("client-1", "app_1");

        assertEquals("CANCELLED", rejected.getStatus());
        // La solicitud padre sigue abierta para las demás postulaciones.
        assertEquals("OPEN", parent.getStatus());
        verify(chat).postSystemMessage(eq("app_1"), eq("Sistema"),
            org.mockito.ArgumentMatchers.contains("rechaz"));
    }

    @Test
    void createReusesActiveDirectChatForSameProfessional() {
        when(users.findById("client-1")).thenReturn(Optional.of(client("client-1")));
        User pro = client("pro-1");
        pro.setRole("PROFESSIONAL");
        when(users.findById("pro-1")).thenReturn(Optional.of(pro));
        when(jobs.save(any(Job.class))).thenAnswer(i -> i.getArgument(0));

        // Primera consulta: se crea.
        CreateJobRequest req1 = new CreateJobRequest();
        req1.setTitle("Consulta");
        req1.setId("chat_pro-1_1");
        req1.setProfessionalId("pro-1");
        JobDto first = service.create("client-1", req1);
        assertEquals("chat_pro-1_1", first.getId());

        // Segunda consulta al mismo profesional: se reutiliza la existente.
        Job existing = job("chat_pro-1_1");
        existing.setClientId("client-1");
        existing.setProfessionalId("pro-1");
        existing.setStatus("NEGOTIATING");
        when(jobs.findByClientIdOrderByCreatedAtDesc("client-1")).thenReturn(List.of(existing));

        CreateJobRequest req2 = new CreateJobRequest();
        req2.setTitle("Consulta 2");
        req2.setId("chat_pro-1_2");
        req2.setProfessionalId("pro-1");
        JobDto second = service.create("client-1", req2);
        assertEquals("chat_pro-1_1", second.getId());
    }

    @Test
    void deleteBlocksAcceptedAndInProgressJobs() {
        Job j = job("j1");
        j.setStatus("ACCEPTED");
        when(jobs.findById("j1")).thenReturn(Optional.of(j));

        ResponseStatusException blocked = assertThrows(ResponseStatusException.class,
            () -> service.delete("client-1", "j1"));
        assertEquals(HttpStatus.CONFLICT, blocked.getStatusCode());

        j.setStatus("IN_PROGRESS");
        ResponseStatusException blocked2 = assertThrows(ResponseStatusException.class,
            () -> service.delete("client-1", "j1"));
        assertEquals(HttpStatus.CONFLICT, blocked2.getStatusCode());
    }
}
