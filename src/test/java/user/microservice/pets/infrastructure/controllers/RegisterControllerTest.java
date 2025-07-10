package user.microservice.pets.infrastructure.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import user.microservice.pets.application.dto.RegisterRequest;
import user.microservice.pets.application.dto.RegisterResponse;
import user.microservice.pets.application.services.RegisterService;
import user.microservice.pets.infrastructure.config.RegisterControllerTestConfig;
import user.microservice.pets.infrastructure.config.TestSecurityConfig;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RegisterController.class)
@Import({RegisterControllerTestConfig.class, TestSecurityConfig.class})

class RegisterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegisterService registerService; // Ya no es @MockBean

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldReturn201WhenUserIsRegistered() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("Gus");
        request.setEmail("gus@example.com");
        request.setPassword("secreta123");

        RegisterResponse response = new RegisterResponse(
                UUID.randomUUID(),
                "Gus",
                "gus@example.com",
                LocalDateTime.now()
        );

        Mockito.when(registerService.register(Mockito.any(RegisterRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("Gus"))
                .andExpect(jsonPath("$.email").value("gus@example.com"));
    }
}
