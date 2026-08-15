package user.microservice.pets.application.usecases;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import user.microservice.pets.domain.exceptions.InvalidUserDataException;
import user.microservice.pets.domain.exceptions.UserNotFoundException;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.in.GetProfileUseCase;
import user.microservice.pets.domain.ports.out.UserRepositoryPort;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GetProfileUseCaseImpl implements GetProfileUseCase {

    private final UserRepositoryPort userRepositoryPort;

    @Override
    public User getProfile(UUID userId) {
        if (userId == null) {
            log.warn("Attempt to get profile with null userId");
            throw new InvalidUserDataException("User ID cannot be null");
        }

        User user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> {
                    log.warn("User not found with id: {}", userId);
                    return new UserNotFoundException("User not found");
                });

        log.info("Profile retrieved for user: {}", user.getEmail());
        return user;
    }
}