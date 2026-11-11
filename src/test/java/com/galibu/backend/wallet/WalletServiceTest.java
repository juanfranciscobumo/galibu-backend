package com.galibu.backend.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.galibu.backend.users.User;
import com.galibu.backend.users.UserRepository;
import com.galibu.backend.wallet.dto.WalletTransactionDto;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletTransactionRepository transactions;

    @Mock
    private UserRepository users;

    @InjectMocks
    private WalletService service;

    private User user(String id, double balance) {
        User u = new User();
        u.setId(id);
        u.setBalance(balance);
        return u;
    }

    @Test
    void creditExternalAddsBalanceAndRecords() {
        when(users.findById("pro-1")).thenReturn(Optional.of(user("pro-1", 1000.0)));
        when(transactions.save(any(WalletTransaction.class))).thenAnswer(i -> i.getArgument(0));

        WalletTransactionDto dto = service.creditExternal("pro-1", 50000.0, "Test");

        assertEquals("RECHARGE", dto.getType());
        assertEquals(50000.0, dto.getAmount());
        assertEquals(51000.0, dto.getBalanceAfter());
    }

    @Test
    void creditExternalRejectsNonPositive() {
        ResponseStatusException bad = assertThrows(ResponseStatusException.class,
            () -> service.creditExternal("pro-1", 0.0, "Test"));
        assertEquals(HttpStatus.BAD_REQUEST, bad.getStatusCode());
    }
}