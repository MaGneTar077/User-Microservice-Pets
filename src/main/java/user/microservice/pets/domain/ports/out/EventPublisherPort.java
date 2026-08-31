package user.microservice.pets.domain.ports.out;

import user.microservice.pets.application.dto.AuthEvent;

public interface EventPublisherPort {
    void publishAuthEvent(AuthEvent event);
}
