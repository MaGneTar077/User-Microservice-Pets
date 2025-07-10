package user.microservice.pets.infrastructure.adapters;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import user.microservice.pets.domain.enums.AuthProvider;
import user.microservice.pets.domain.model.User;
import user.microservice.pets.infrastructure.entity.UserEntity;
import user.microservice.pets.infrastructure.repositories.JpaUserRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRepositoryAdapterTest {

    @Mock
    private JpaUserRepository jpaUserRepository;

    @InjectMocks
    private UserRepositoryAdapter adapter;

    private User user;
    private UserEntity userEntity;

    @BeforeEach
    void setUp() {
        UUID id = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now();

        user = new User(id, "Gus", "gus@example.com", "secret", createdAt, AuthProvider.LOCAL);

        userEntity = UserEntity.builder()
                .id(id)
                .username("Gus")
                .email("gus@example.com")
                .password("secret")
                .createdAt(createdAt)
                .authProvider(AuthProvider.LOCAL)
                .build();
    }

    @Test
    void shouldSaveUser() {
        when(jpaUserRepository.save(any(UserEntity.class))).thenReturn(userEntity);

        User result = adapter.save(user);

        assertNotNull(result);
        assertEquals(user.getEmail(), result.getEmail());
        verify(jpaUserRepository).save(any(UserEntity.class));
    }

    @Test
    void shouldFindUserByEmail() {
        when(jpaUserRepository.findByEmail("gus@example.com")).thenReturn(Optional.of(userEntity));

        Optional<User> result = adapter.findByEmail("gus@example.com");

        assertTrue(result.isPresent());
        assertEquals("gus@example.com", result.get().getEmail());
        verify(jpaUserRepository).findByEmail("gus@example.com");
    }

    @Test
    void shouldReturnTrueIfEmailExists() {
        when(jpaUserRepository.existsByEmail("gus@example.com")).thenReturn(true);

        boolean exists = adapter.existsByEmail("gus@example.com");

        assertTrue(exists);
        verify(jpaUserRepository).existsByEmail("gus@example.com");
    }
}
