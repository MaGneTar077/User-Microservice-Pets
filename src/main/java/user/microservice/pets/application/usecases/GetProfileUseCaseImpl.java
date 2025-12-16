package user.microservice.pets.application.usecases;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.in.GetProfileUseCase;
import user.microservice.pets.domain.ports.out.UserRepositoryPort;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetProfileUseCaseImpl implements GetProfileUseCase {

    private final UserRepositoryPort userRepositoryPort;

    @Override
    public User getProfile(UUID userId) {
        return userRepositoryPort.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
    }
}
