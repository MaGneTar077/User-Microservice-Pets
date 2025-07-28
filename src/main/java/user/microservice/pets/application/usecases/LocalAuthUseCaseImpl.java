package user.microservice.pets.application.usecases;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.in.LocalAuthUseCase;
import user.microservice.pets.domain.ports.out.UserRepositoryPort;

@Service
@RequiredArgsConstructor
public class LocalAuthUseCaseImpl implements LocalAuthUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final PasswordEncoder passwordEncoder;

    public User login(String email, String password) {
        return userRepositoryPort.findByEmail(email)
                .filter(user -> passwordEncoder.matches(password, user.getPassword()))
                .orElseThrow(() -> new RuntimeException("Email o contraseña incorrectos"));
    }
}
