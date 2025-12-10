package user.microservice.pets.application.usecases;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.in.UpdateUserProfileUseCase;
import user.microservice.pets.domain.ports.out.UserRepositoryPort;

@Service
@RequiredArgsConstructor
public class UpdateUserProfileUseCaseImpl implements UpdateUserProfileUseCase {

    private final UserRepositoryPort userRepositoryPort;

    @Override
    public User updateProfile(User updatedUser) {
        User existingUser = userRepositoryPort.findById(updatedUser.getId())
                .orElseThrow(() -> new RuntimeException("User not found with id: " + updatedUser.getId()));

        if (userRepositoryPort.existsByUsername(updatedUser.getUsername())
                && !existingUser.getUsername().equals(updatedUser.getUsername())) {
            throw new RuntimeException("Username already exists");
        }

        existingUser.setUsername(updatedUser.getUsername());

        return userRepositoryPort.save(existingUser);
    }

}
