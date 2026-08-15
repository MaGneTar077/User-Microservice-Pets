package user.microservice.pets.application.services;

import user.microservice.pets.application.dto.RegisterRequest;
import user.microservice.pets.application.dto.RegisterResponse;
import user.microservice.pets.domain.enums.AuthProvider;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.in.RegisterUserUseCase;

public class RegisterService {
    private final RegisterUserUseCase registerUserUseCase;

    public RegisterService(RegisterUserUseCase registerUserUseCase){
        this.registerUserUseCase = registerUserUseCase;
    }

    public RegisterResponse register(RegisterRequest request){
        User newUser = new User();
        newUser.setUsername(request.getUsername());
        newUser.setEmail(request.getEmail());
        newUser.setPassword(request.getPassword());
        newUser.setAuthProvider(AuthProvider.LOCAL);

        User savedUser = registerUserUseCase.register(newUser);

        return new RegisterResponse(
                savedUser.getId(),
                savedUser.getUsername(),
                savedUser.getEmail(),
                savedUser.getCreatedAt()
        );
    }
}
