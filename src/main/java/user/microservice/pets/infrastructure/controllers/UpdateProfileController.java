package user.microservice.pets.infrastructure.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import user.microservice.pets.application.dto.UpdateUserRequest;
import user.microservice.pets.application.dto.UpdateUserResponse;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.in.UpdateUserProfileUseCase;

import java.util.UUID;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UpdateProfileController {

    private final UpdateUserProfileUseCase updateUserProfileUseCase;

    @PutMapping("/{id}")
    public ResponseEntity<UpdateUserResponse> updateProfile(@PathVariable UUID id, @RequestBody UpdateUserRequest request) {

        User domain = User.builder()
                .id(id)
                .username(request.getUsername())
                .build();

        User updated = updateUserProfileUseCase.updateProfile(domain);

        UpdateUserResponse response =
                new UpdateUserResponse(
                        updated.getId(),
                        updated.getUsername(),
                        updated.getEmail()
                );

        return ResponseEntity.ok(response);
    }
}