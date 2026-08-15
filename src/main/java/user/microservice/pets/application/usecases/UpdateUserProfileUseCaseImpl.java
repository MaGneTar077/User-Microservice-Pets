package user.microservice.pets.application.usecases;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import user.microservice.pets.domain.exceptions.InvalidUserDataException;
import user.microservice.pets.domain.exceptions.UserAlreadyExistsException;
import user.microservice.pets.domain.exceptions.UserNotFoundException;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.in.UpdateUserProfileUseCase;
import user.microservice.pets.domain.ports.out.UserRepositoryPort;

import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateUserProfileUseCaseImpl implements UpdateUserProfileUseCase {

    private final UserRepositoryPort userRepositoryPort;

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    @Override
    public User updateProfile(User updatedUser) {

        validateUserData(updatedUser);

        User existingUser = userRepositoryPort.findById(updatedUser.getId())
                .orElseThrow(() -> {
                    log.warn("Attempt to update non-existent user with id: {}", updatedUser.getId());
                    return new UserNotFoundException("User not found");
                });

        boolean usernameChanged = !existingUser.getUsername().equals(updatedUser.getUsername());

        if (usernameChanged) {
            if (userRepositoryPort.existsByUsername(updatedUser.getUsername())) {
                log.warn("Attempt to update username to existing username: {}", updatedUser.getUsername());
                throw new UserAlreadyExistsException("Username already exists");
            }
        }

        existingUser.setUsername(updatedUser.getUsername().trim());

        User savedUser = userRepositoryPort.save(existingUser);
        log.info("User profile updated successfully for user: {}", savedUser.getEmail());

        return savedUser;
    }

    private void validateUserData(User user) {
        if (user.getId() == null) {
            throw new InvalidUserDataException("User ID cannot be null");
        }

        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            throw new InvalidUserDataException("Username cannot be empty");
        }

        String trimmedUsername = user.getUsername().trim();
        if (trimmedUsername.length() < 3 || trimmedUsername.length() > 50) {
            throw new InvalidUserDataException("Username must be between 3 and 50 characters");
        }

        if (!USERNAME_PATTERN.matcher(trimmedUsername).matches()) {
            throw new InvalidUserDataException("Username can only contain letters, numbers, and underscores");
        }
    }
}