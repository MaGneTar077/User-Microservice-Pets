package user.microservice.pets.infrastructure.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import user.microservice.pets.application.dto.InternalUserBatchRequest;
import user.microservice.pets.application.dto.InternalUserResponse;
import user.microservice.pets.domain.enums.AuthProvider;
import user.microservice.pets.domain.enums.PlatformRole;
import user.microservice.pets.domain.exceptions.InvalidUserDataException;
import user.microservice.pets.domain.exceptions.UserNotFoundException;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.domain.ports.out.UserRepositoryPort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InternalUserController - Unit Tests")
class InternalUserControllerTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;

    private InternalUserController controller;

    @BeforeEach
    void setUp() {
        controller = new InternalUserController(userRepositoryPort);
    }

    private User buildUser(UUID id) {
        return User.builder()
                .id(id)
                .username("gususer")
                .email("gus@example.com")
                .password("super-secret-hash")
                .authProvider(AuthProvider.LOCAL)
                .emailVerified(true)
                .platformRole(PlatformRole.USER)
                .build();
    }

    @Test
    @DisplayName("getById should map username to fullName and never expose the password")
    void getByIdShouldMapFieldsAndHidePassword() {
        UUID id = UUID.randomUUID();
        when(userRepositoryPort.findById(id)).thenReturn(Optional.of(buildUser(id)));

        ResponseEntity<InternalUserResponse> response = controller.getById(id.toString());

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        InternalUserResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.id()).isEqualTo(id);
        assertThat(body.fullName()).isEqualTo("gususer");
        assertThat(body.email()).isEqualTo("gus@example.com");
        assertThat(body.phone()).isNull();
        assertThat(body.emailVerified()).isTrue();
        assertThat(body.toString()).doesNotContain("super-secret-hash");
    }

    @Test
    @DisplayName("getById should throw UserNotFoundException (404) when the user does not exist")
    void getByIdShouldThrowWhenUserNotFound() {
        UUID id = UUID.randomUUID();
        when(userRepositoryPort.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.getById(id.toString()))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("getById should throw UserNotFoundException for a malformed id instead of leaking a 500")
    void getByIdShouldThrowForMalformedId() {
        assertThatThrownBy(() -> controller.getById("not-a-uuid"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("getByEmail should return the matching user")
    void getByEmailShouldReturnUser() {
        UUID id = UUID.randomUUID();
        when(userRepositoryPort.findByEmail("gus@example.com")).thenReturn(Optional.of(buildUser(id)));

        ResponseEntity<InternalUserResponse> response = controller.getByEmail("gus@example.com");

        assertThat(response.getBody().email()).isEqualTo("gus@example.com");
    }

    @Test
    @DisplayName("batch should reject more than 100 ids")
    void batchShouldRejectMoreThan100Ids() {
        List<UUID> tooMany = java.util.stream.Stream.generate(UUID::randomUUID).limit(101).toList();

        assertThatThrownBy(() -> controller.getBatch(new InternalUserBatchRequest(tooMany)))
                .isInstanceOf(InvalidUserDataException.class);
    }

    @Test
    @DisplayName("batch should return the matching users")
    void batchShouldReturnMatchingUsers() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(userRepositoryPort.findAllByIds(List.of(id1, id2)))
                .thenReturn(List.of(buildUser(id1), buildUser(id2)));

        ResponseEntity<List<InternalUserResponse>> response =
                controller.getBatch(new InternalUserBatchRequest(List.of(id1, id2)));

        assertThat(response.getBody()).hasSize(2);
    }
}
