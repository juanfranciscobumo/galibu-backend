package com.galibu.backend.wallet;

import com.galibu.backend.users.User;
import com.galibu.backend.users.UserRepository;
import com.galibu.backend.wallet.dto.WalletTransactionDto;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Historial de movimientos de la billetera. Galibu ya no cobra por aceptar
 * trabajos: la billetera queda solo como registro de recargas legadas y como
 * compatibilidad con clientes antiguos. El acceso a trabajar se controla con
 * {@link com.galibu.backend.subscription.SubscriptionService}.
 */
@Service
public class WalletService {

    /** Saldo inicial de cuentas nuevas. */
    public static final double INITIAL_BALANCE = 0.0;

    private final WalletTransactionRepository transactions;
    private final UserRepository users;

    public WalletService(WalletTransactionRepository transactions, UserRepository users) {
        this.transactions = transactions;
        this.users = users;
    }

    /**
     * Acredita una recarga confirmada por una pasarela externa (Bold).
     * La idempotencia la garantiza quien llama (orden de pago bloqueada).
     */
    @Transactional
    public WalletTransactionDto creditExternal(String uid, double amount, String description) {
        if (amount <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "El monto debe ser mayor a cero");
        }
        User user = findUserOr404(uid);
        user.setBalance(user.getBalance() + amount);
        users.save(user);
        return WalletTransactionDto.from(record(
            uid, "RECHARGE", amount, user.getBalance(), null,
            description == null || description.isBlank() ? "Recarga de saldo" : description));
    }

    @Transactional(readOnly = true)
    public List<WalletTransactionDto> history(String uid) {
        findUserOr404(uid);
        return transactions.findByUserIdOrderByCreatedAtDesc(uid).stream()
            .map(WalletTransactionDto::from).toList();
    }

    private WalletTransaction record(String uid, String type, double amount,
            double balanceAfter, String jobId, String description) {
        WalletTransaction tx = new WalletTransaction();
        tx.setId("wtx_" + UUID.randomUUID().toString().replace("-", ""));
        tx.setUserId(uid);
        tx.setType(type);
        tx.setAmount(amount);
        tx.setBalanceAfter(balanceAfter);
        tx.setJobId(jobId);
        tx.setDescription(description);
        tx.setCreatedAt(System.currentTimeMillis());
        return transactions.save(tx);
    }

    private User findUserOr404(String uid) {
        return users.findById(uid).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no existe"));
    }
}
