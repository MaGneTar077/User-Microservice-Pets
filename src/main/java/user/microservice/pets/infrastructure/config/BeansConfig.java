package user.microservice.pets.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import user.microservice.pets.application.services.RegisterService;
import user.microservice.pets.application.usecases.RegisterUserUseCaseImpl;
import user.microservice.pets.domain.ports.in.RegisterUserUseCase;
import user.microservice.pets.domain.ports.out.UserRepositoryPort;

@Configuration
public class BeansConfig {

    @Bean
    public RegisterUserUseCase registerUserUseCase(UserRepositoryPort userRepositoryPort){
        return new RegisterUserUseCaseImpl(userRepositoryPort);
    }

    @Bean
    public RegisterService registerService(RegisterUserUseCase registerUserUseCase){
        return new RegisterService(registerUserUseCase);
    }
}
