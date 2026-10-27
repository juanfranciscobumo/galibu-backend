package com.galibu.backend.users;

import com.galibu.backend.common.TextUtils;
import com.galibu.backend.auth.dto.UserDto;
import com.galibu.backend.bids.BidRepository;
import com.galibu.backend.jobs.JobRepository;
import com.galibu.backend.ratings.RatingRepository;
import com.galibu.backend.users.dto.UpdateProfileRequest;
import com.galibu.backend.wallet.WalletTransactionRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {

    /** Estados de un trabajo que siguen "en curso" y por tanto bloquean el borrado de la cuenta. */
    private static final List<String> ACTIVE_JOB_STATUSES =
        List.of("OPEN", "NEGOTIATING", "ACCEPTED", "IN_PROGRESS");

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JobRepository jobs;
    private final BidRepository bids;
    private final RatingRepository ratings;
    private final WalletTransactionRepository walletTransactions;

    public UserService(
        UserRepository users,
        PasswordEncoder passwordEncoder,
        JobRepository jobs,
        BidRepository bids,
        RatingRepository ratings,
        WalletTransactionRepository walletTransactions
    ) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jobs = jobs;
        this.bids = bids;
        this.ratings = ratings;
        this.walletTransactions = walletTransactions;
    }

    @Transactional(readOnly = true)
    public UserDto me(String uid) {
        return UserDto.from(findOr404(uid));
    }

    @Transactional(readOnly = true)
    public UserDto getById(String id) {
        return UserDto.from(findOr404(id));
    }

    @Transactional(readOnly = true)
    public List<UserDto> professionals() {
        return users.findByRoleInOrderByNameAsc(List.of("PROFESSIONAL", "BOTH")).stream()
            .map(UserDto::from)
            .toList();
    }

    @Transactional
    public UserDto updateProfile(String uid, UpdateProfileRequest req) {
        User user = findOr404(uid);
        if (req.getName() != null && !req.getName().isBlank()) {
            user.setName(TextUtils.capitalizeFirst(req.getName().trim()));
        }
        if (req.getPhone() != null) {
            user.setPhone(req.getPhone());
        }
        if (req.getProfilePhotoUrl() != null) {
            user.setProfilePhotoUrl(req.getProfilePhotoUrl());
        }
        if (req.getDepartment() != null) {
            user.setDepartment(TextUtils.capitalizeFirst(req.getDepartment()));
        }
        if (req.getMunicipality() != null) {
            user.setMunicipality(TextUtils.capitalizeFirst(req.getMunicipality()));
        }
        if (req.getAddress() != null) {
            user.setAddress(TextUtils.capitalizeFirst(req.getAddress()));
        }
        if (req.getWorkCategory() != null) {
            user.setWorkCategory(TextUtils.capitalizeFirst(req.getWorkCategory()));
        }
        if (req.getHourlyRate() != null) {
            user.setHourlyRate(req.getHourlyRate());
        }
        if (req.getWorkPhotos() != null) {
            user.setWorkPhotos(req.getWorkPhotos());
        }
        if (req.getWorkDescription() != null) {
            user.setWorkDescription(TextUtils.capitalizeFirst(req.getWorkDescription()));
        }
        if (req.getDocumentType() != null) {
            user.setDocumentType(req.getDocumentType());
        }
        if (req.getDocumentPhotoUrl() != null) {
            user.setDocumentPhotoUrl(req.getDocumentPhotoUrl());
        }
        return UserDto.from(users.save(user));
    }

    @Transactional
    public UserDto addRole(String uid, String role) {
        String upper = role == null ? "" : role.trim().toUpperCase();
        if (!"CLIENT".equals(upper) && !"PROFESSIONAL".equals(upper) && !"BOTH".equals(upper)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rol inválido (CLIENT, PROFESSIONAL o BOTH)");
        }
        User user = findOr404(uid);
        if (!upper.equals(user.getRole())) {
            user.setRole("BOTH");
            user = users.save(user);
        }
        return UserDto.from(user);
    }

    @Transactional
    public void changePassword(String uid, String currentPassword, String newPassword) {
        User user = findOr404(uid);
        if (user.getPasswordHash() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tu cuenta usa Google: inicia sesión con Google");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La nueva contraseña debe tener al menos 8 caracteres");
        }
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "La contraseña actual es incorrecta");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        users.save(user);
    }

    /**
     * Elimina la cuenta y todos sus datos. Solo se bloquea si el usuario tiene
     * trabajos en curso (para no dejar colgado al otro lado del servicio).
     * Se borran sus trabajos (arrastran sus ofertas y calificaciones), las ofertas
     * que dejó en trabajos de otros, sus calificaciones y su historial de billetera.
     */
    @Transactional
    public void deleteAccount(String uid) {
        User user = findOr404(uid);
        long activeJobs = jobs.countActiveForUser(uid, ACTIVE_JOB_STATUSES);
        if (activeJobs > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tienes solicitudes activas");
        }
        jobs.deleteAllForUser(uid);
        bids.deleteAllForUser(uid);
        ratings.deleteAllForUser(uid);
        walletTransactions.deleteAllForUser(uid);
        users.delete(user);
    }

    private User findOr404(String uid) {
        return users.findById(uid).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no existe"));
    }
}
