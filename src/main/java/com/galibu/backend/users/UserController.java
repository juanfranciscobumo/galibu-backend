package com.galibu.backend.users;

import com.galibu.backend.auth.dto.UserDto;
import com.galibu.backend.users.dto.PublicUserDto;
import com.galibu.backend.users.dto.UpdateProfileRequest;
import com.galibu.backend.users.dto.VerificationSubmitRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public UserDto me(Authentication auth) {
        return service.me(auth.getName());
    }

    @PatchMapping("/me")
    public UserDto updateProfile(Authentication auth, @Valid @RequestBody UpdateProfileRequest request) {
        return service.updateProfile(auth.getName(), request);
    }

    @PostMapping("/me/roles")
    public UserDto addRole(Authentication auth, @Valid @RequestBody RoleRequest request) {
        return service.addRole(auth.getName(), request.role());
    }

    @PatchMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(Authentication auth, @Valid @RequestBody PasswordChangeRequest request) {
        service.changePassword(auth.getName(), request.currentPassword(), request.newPassword());
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(Authentication auth) {
        service.deleteAccount(auth.getName());
    }

    @GetMapping("/professionals")
    public List<PublicUserDto> professionals(Authentication auth) {
        return service.professionals(auth.getName());
    }

    @GetMapping("/{id}")
    public PublicUserDto getById(@PathVariable String id) {
        return service.getById(id);
    }

    @PostMapping("/me/verification")
    public UserDto submitVerification(Authentication auth, @Valid @RequestBody VerificationSubmitRequest request) {
        return service.submitVerification(auth.getName(), request);
    }

    @PostMapping("/me/verification/complete")
    public UserDto completeVerification(Authentication auth) {
        return service.completeVerification(auth.getName());
    }

    public record RoleRequest(@NotBlank String role) {
    }

    public record PasswordChangeRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 8, max = 72) String newPassword
    ) {
    }
}
