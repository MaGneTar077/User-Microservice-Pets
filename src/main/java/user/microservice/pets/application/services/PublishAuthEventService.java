package user.microservice.pets.application.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import user.microservice.pets.application.dto.AuthEvent;
import user.microservice.pets.domain.ports.in.PublishAuthEventUseCase;
import user.microservice.pets.domain.ports.out.EventPublisherPort;

@Service
@RequiredArgsConstructor
public class PublishAuthEventService implements PublishAuthEventUseCase {

    private final EventPublisherPort eventPublisherPort;

    @Override
    public void publish(AuthEvent event) {
        eventPublisherPort.publishAuthEvent(event);
    }
}
