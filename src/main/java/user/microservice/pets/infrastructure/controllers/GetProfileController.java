package user.microservice.pets.infrastructure.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import user.microservice.pets.application.dto.ProfileResponse;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.in.GetProfileUseCase;

import java.util.UUID;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class GetProfileController {

    private final GetProfileUseCase getProfileUseCase;

    @GetMapping("/profile/{id}")
    public ResponseEntity<ProfileResponse> getProfile(@PathVariable UUID id) {
        User user = getProfileUseCase.getProfile(id);

        ProfileResponse response = new ProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getCreatedAt(),
                user.getAuthProvider()
        );

        return ResponseEntity.ok(response);
    }

}
