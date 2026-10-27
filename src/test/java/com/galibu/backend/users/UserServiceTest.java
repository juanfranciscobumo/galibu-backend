package com.galibu.backend.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.galibu.backend.auth.dto.UserDto;
import com.galibu.backend.users.dto.UpdateProfileRequest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository users;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private com.galibu.backend.jobs.JobRepository jobs;

    @Mock
    private com.galibu.backend.bids.BidRepository bids;

    @Mock
    private com.galibu.backend.ratings.RatingRepository ratings;

    @Mock
    private com.galibu.backend.wallet.WalletTransactionRepository walletTransactions;

    @InjectMocks
    private UserService service;

    private User user(String id) {
        User u = new User();
        u.setId(id);
        u.setName("N");
        u.setEmail(id + "@t.com");
        u.setRole("CLIENT");
        u.setRating(4.5f);
        return u;
    }

    @Test
    void updateProfileOnlyTouchesAllowedFields() {
        when(users.findById("u1")).thenReturn(Optional.of(user("u1")));
        when(users.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setName("Nuevo");
        req.setPhone("300");
        req.setHourlyRate(60000.0);

        UserDto updated = service.updateProfile("u1", req);

        assertEquals("Nuevo", updated.getName());
        assertEquals("300", updated.getPhone());
        assertEquals(60000.0, updated.getHourlyRate());
        // role/email/balance are not part of UpdateProfileRequest: they can never change here
        assertEquals("CLIENT", updated.getRole());
        assertEquals("u1@t.com", updated.getEmail());
        verify(users).save(any(User.class));
    }

    @Test
    void professionalsListsBothRoles() {
        User pro = user("p1");
        pro.setRole("PROFESSIONAL");
        User both = user("p2");
        both.setRole("BOTH");
        when(users.findByRoleInOrderByNameAsc(List.of("PROFESSIONAL", "BOTH")))
            .thenReturn(List.of(pro, both));

        assertEquals(2, service.professionals().size());
    }

    @Test
    void addRoleUpgradesToBoth() {
        User u = user("u1");
        when(users.findById("u1")).thenReturn(Optional.of(u));
        when(users.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        assertEquals("BOTH", service.addRole("u1", "PROFESSIONAL").getRole());

        ResponseStatusException bad = org.junit.jupiter.api.Assertions.assertThrows(
            ResponseStatusException.class, () -> service.addRole("u1", "ADMIN"));
        assertEquals(HttpStatus.BAD_REQUEST, bad.getStatusCode());
    }

    @Test
    void changePasswordVerifiesCurrentAndLength() {
        User u = user("u1");
        u.setPasswordHash("hashed");
        when(users.findById("u1")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("old12345", "hashed")).thenReturn(true);
        org.mockito.Mockito.lenient().when(passwordEncoder.encode("new12345")).thenReturn("hashed-new");

        service.changePassword("u1", "old12345", "new12345");
        verify(users).save(any(User.class));

        when(passwordEncoder.matches(eq("wrong"), anyString())).thenReturn(false);
        ResponseStatusException wrong = assertThrows(ResponseStatusException.class,
            () -> service.changePassword("u1", "wrong", "new12345"));
        assertEquals(HttpStatus.UNAUTHORIZED, wrong.getStatusCode());

        ResponseStatusException shortPw = assertThrows(ResponseStatusException.class,
            () -> service.changePassword("u1", "old12345", "short"));
        assertEquals(HttpStatus.BAD_REQUEST, shortPw.getStatusCode());

        User google = user("u2");
        google.setPasswordHash(null);
        when(users.findById("u2")).thenReturn(Optional.of(google));
        ResponseStatusException noLocal = assertThrows(ResponseStatusException.class,
            () -> service.changePassword("u2", "x", "new12345"));
        assertEquals(HttpStatus.CONFLICT, noLocal.getStatusCode());
    }

    @Test
    void deleteAccountRefusesWithActiveJobs() {
        when(users.findById("u1")).thenReturn(Optional.of(user("u1")));
        when(jobs.countActiveForUser(eq("u1"), anyList()))
            .thenReturn(2L);

        ResponseStatusException blocked = assertThrows(ResponseStatusException.class,
            () -> service.deleteAccount("u1"));
        assertEquals(HttpStatus.CONFLICT, blocked.getStatusCode());
        verify(users, never()).delete(any(User.class));
        verify(walletTransactions, never()).deleteAllForUser(anyString());
    }

    @Test
    void deleteAccountRemovesUserAndTheirData() {
        User u = user("u1");
        when(users.findById("u1")).thenReturn(Optional.of(u));
        when(jobs.countActiveForUser(eq("u1"), anyList()))
            .thenReturn(0L);

        service.deleteAccount("u1");

        // Historial: trabajos (arrastran ofertas y calificaciones), ofertas en otros
        // trabajos, calificaciones y billetera. Después se borra la cuenta.
        verify(jobs).deleteAllForUser("u1");
        verify(bids).deleteAllForUser("u1");
        verify(ratings).deleteAllForUser("u1");
        verify(walletTransactions).deleteAllForUser("u1");
        verify(users).delete(u);
    }
}
