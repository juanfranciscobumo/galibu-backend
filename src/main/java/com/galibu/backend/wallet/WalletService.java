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

@Service
public class WalletService {

    /** Tarifa que paga el profesional cada vez que acepta un trabajo. */
    public static final double ACCEPT_FEE = 3000.0;

    /** Saldo inicial de cuentas nuevas. */
    public static final double INITIAL_BALANCE = 0.0;

    /** Trabajos gratuitos de cortesía para profesionales nuevos. */
    public static final int FREE_ACCEPTANCES = 3;

    private final WalletTransactionRepository transactions;
    private final UserRepository users;

    public WalletService(WalletTransactionRepository transactions, UserRepository users) {
        this.transactions = transactions;
        this.users = users;
    }

    @Transactional
    public WalletTransactionDto recharge(String uid, double amount) {
        if (amount <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El monto debe ser mayor a cero");
        }
        User user = findUserOr404(uid);
        user.setBalance(user.getBalance() + amount);
        users.save(user);
        return WalletTransactionDto.from(record(
            uid, "RECHARGE", amount, user.getBalance(), null, "Recarga de saldo"));
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

    /**
     * Cobra la tarifa de aceptación al profesional. Falla con 409 si no hay saldo,
     * para que la app muestre el aviso de recarga en lugar de aceptar gratis.
     */
    @Transactional
    public WalletTransactionDto chargeAcceptFee(String professionalId, String jobId, String jobTitle) {
        User pro = findUserOr404(professionalId);
        String title = jobTitle == null || jobTitle.isBlank() ? "servicio" : jobTitle;
        // Cortesía: hasta FREE_ACCEPTANCES trabajos sin pagar la tarifa.
        if (pro.getFreeAcceptancesRemaining() > 0) {
            pro.setFreeAcceptancesRemaining(pro.getFreeAcceptancesRemaining() - 1);
            users.save(pro);
            return WalletTransactionDto.from(record(
                professionalId, "FREE_ACCEPT", 0.0, pro.getBalance(), jobId,
                "Aceptación gratuita (cortesía): " + title));
        }
        if (pro.getBalance() < ACCEPT_FEE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Saldo insuficiente para aceptar. Recarga tu wallet.");
        }
        pro.setBalance(pro.getBalance() - ACCEPT_FEE);
        users.save(pro);
        return WalletTransactionDto.from(record(
            professionalId, "ACCEPT_FEE", -ACCEPT_FEE, pro.getBalance(), jobId,
            "Aceptación: " + title));
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
