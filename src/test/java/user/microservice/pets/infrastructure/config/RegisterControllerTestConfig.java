package user.microservice.pets.infrastructure.config;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import user.microservice.pets.application.services.RegisterService;

@TestConfiguration
public class RegisterControllerTestConfig {

    @Bean
    public RegisterService registerService() {
        return Mockito.mock(RegisterService.class);
    }
}
