package com.galibu.backend.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.galibu.backend.auth.dto.AuthResponse;
import com.galibu.backend.auth.dto.GoogleLoginRequest;
import com.galibu.backend.auth.dto.RegisterRequest;
import com.galibu.backend.security.JwtService;
import com.galibu.backend.users.User;
import com.galibu.backend.users.UserRepository;
import com.galibu.backend.wallet.WalletService;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reglas de identidad de Fase 4: una cuenta nace con la capacidad elegida y,
 * si no se elige, nace como CLIENT (nunca PROFESSIONAL por omisión).
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository users;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, passwordEncoder, jwtService, "");
    }

    private RegisterRequest registerRequest(String role) {
        RegisterRequest req = new RegisterRequest();
        req.setName("Ana");
        req.setEmail("Nuevo@Test.com");
        req.setPassword("password123");
        req.setRole(role);
        return req;
    }

    private GoogleIdToken.Payload googlePayload(String email, boolean verified, String name) {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.put("email", email);
        payload.put("email_verified", verified);
        payload.put("name", name);
        return payload;
    }

    private void stubNewUser() {
        when(users.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void registerWithoutRoleDefaultsToClient() {
        when(users.existsByEmail("nuevo@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hash");
        stubNewUser();

        AuthResponse response = service.register(registerRequest(null));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertEquals("CLIENT", saved.getValue().getRole());
        assertEquals(0, saved.getValue().getFreeAcceptancesRemaining());
        assertEquals(WalletService.INITIAL_BALANCE, saved.getValue().getBalance());
        assertEquals("CLIENT", response.getUser().getRole());
    }

    @Test
    void registerAsProfessionalGrantsFreeAcceptances() {
        when(users.existsByEmail("nuevo@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hash");
        stubNewUser();

        service.register(registerRequest("professional"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertEquals("PROFESSIONAL", saved.getValue().getRole());
        assertEquals(WalletService.FREE_ACCEPTANCES, saved.getValue().getFreeAcceptancesRemaining());
    }

    @Test
    void registerWithInvalidRoleIsRejected() {
        when(users.existsByEmail("nuevo@test.com")).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.register(registerRequest("ADMIN")));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void newGoogleAccountDefaultsToClient() {
        AuthService spyService = spy(service);
        doReturn(googlePayload("google@test.com", true, "Ana Google"))
            .when(spyService).verifyGoogleToken("id-token");
        when(users.findByEmail("google@test.com")).thenReturn(Optional.empty());
        stubNewUser();

        AuthResponse response = spyService.loginWithGoogle(new GoogleLoginRequest("id-token", null));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertEquals("CLIENT", saved.getValue().getRole());
        assertEquals(0, saved.getValue().getFreeAcceptancesRemaining());
        assertEquals("GOOGLE", saved.getValue().getAuthProvider());
        assertEquals("CLIENT", response.getUser().getRole());
    }

    @Test
    void newGoogleAccountKeepsExplicitProfessionalIntent() {
        AuthService spyService = spy(service);
        doReturn(googlePayload("google@test.com", true, "Ana Google"))
            .when(spyService).verifyGoogleToken("id-token");
        when(users.findByEmail("google@test.com")).thenReturn(Optional.empty());
        stubNewUser();

        spyService.loginWithGoogle(new GoogleLoginRequest("id-token", "PROFESSIONAL"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertEquals("PROFESSIONAL", saved.getValue().getRole());
        assertEquals(WalletService.FREE_ACCEPTANCES, saved.getValue().getFreeAcceptancesRemaining());
    }

    @Test
    void existingGoogleAccountKeepsItsRole() {
        AuthService spyService = spy(service);
        doReturn(googlePayload("google@test.com", true, "Ana Google"))
            .when(spyService).verifyGoogleToken("id-token");
        User existing = new User();
        existing.setId("u1");
        existing.setEmail("google@test.com");
        existing.setRole("CLIENT");
        existing.setAuthProvider("GOOGLE");
        when(users.findByEmail("google@test.com")).thenReturn(Optional.of(existing));

        AuthResponse response = spyService.loginWithGoogle(new GoogleLoginRequest("id-token", "PROFESSIONAL"));

        assertEquals("CLIENT", response.getUser().getRole());
    }
}
