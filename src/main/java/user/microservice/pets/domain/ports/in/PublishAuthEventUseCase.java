package user.microservice.pets.domain.ports.in;

import user.microservice.pets.application.dto.AuthEvent;

public interface PublishAuthEventUseCase {
    void publish(AuthEvent event);
}
