package user.microservice.pets.infrastructure.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import user.microservice.pets.application.dto.ProfileImageUploadResponse;
import user.microservice.pets.application.services.ProfileImageService;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.out.UserRepositoryPort;

@Slf4j
@RestController
@RequestMapping("/user/profile")
@RequiredArgsConstructor
public class ProfileImageController {

    private final ProfileImageService profileImageService;
    private final UserRepositoryPort userRepositoryPort;

    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProfileImageUploadResponse> uploadProfileImage(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {

        String email = authentication.getName();
        User user = userRepositoryPort.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        log.info("Subiendo imagen de perfil para usuario: {}", user.getId());

        ProfileImageUploadResponse response = profileImageService.uploadProfileImage(user.getId(), file);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/image")
    public ResponseEntity<Void> deleteProfileImage(Authentication authentication) {
        String email = authentication.getName();
        User user = userRepositoryPort.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        log.info("Eliminando imagen de perfil para usuario: {}", user.getId());

        profileImageService.deleteProfileImage(user.getId());

        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
