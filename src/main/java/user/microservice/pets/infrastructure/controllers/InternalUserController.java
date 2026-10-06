package user.microservice.pets.infrastructure.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import user.microservice.pets.application.dto.InternalUserBatchRequest;
import user.microservice.pets.application.dto.InternalUserResponse;
import user.microservice.pets.domain.exceptions.InvalidUserDataException;
import user.microservice.pets.domain.exceptions.UserNotFoundException;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.out.UserRepositoryPort;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * USER-06. Rutas usadas por otros servicios del ecosistema (p. ej. Veterinary), protegidas
 * por InternalApiKeyFilter (X-Internal-Api-Key), no por JWT de usuario. Nunca expone
 * password, hashes, ni ningun dato de autenticacion.
 */
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private static final int MAX_BATCH_SIZE = 100;

    private final UserRepositoryPort userRepositoryPort;

    @GetMapping("/{id}")
    public ResponseEntity<InternalUserResponse> getById(@PathVariable String id) {
        UUID userId;
        try {
            userId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new UserNotFoundException("User not found");
        }

        User user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        return ResponseEntity.ok(toResponse(user));
    }

    @PostMapping("/batch")
    public ResponseEntity<List<InternalUserResponse>> getBatch(@RequestBody InternalUserBatchRequest request) {
        List<UUID> ids = request.ids() == null ? List.of() : request.ids();
        if (ids.size() > MAX_BATCH_SIZE) {
            throw new InvalidUserDataException("Cannot request more than " + MAX_BATCH_SIZE + " ids at once");
        }

        List<InternalUserResponse> response = userRepositoryPort.findAllByIds(ids).stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/by-email")
    public ResponseEntity<InternalUserResponse> getByEmail(@RequestParam String email) {
        User user = userRepositoryPort.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        return ResponseEntity.ok(toResponse(user));
    }

    private InternalUserResponse toResponse(User user) {
        return new InternalUserResponse(
                user.getId(),
                user.getUsername(), // fullName = username por ahora (no existe full_name en users)
                user.getEmail(),
                null, // phone: columna no existe todavia
                user.isEmailVerified()
        );
    }
}
