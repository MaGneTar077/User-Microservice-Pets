package user.microservice.pets.infrastructure.adapters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import user.microservice.pets.application.dto.AuthEvent;
import user.microservice.pets.domain.ports.out.EventPublisherPort;

@Slf4j
@Component
@RequiredArgsConstructor
public class GooglePubSubAuthEventAdapter implements EventPublisherPort {

    private static final String TOPIC_NAME = "user-registered";

    private final PubSubTemplate pubSubTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void publishAuthEvent(AuthEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            pubSubTemplate.publish(TOPIC_NAME, payload);
            log.info("Evento {} publicado en topic {} para userId={}",
                    event.getEventType(), TOPIC_NAME, event.getUserId());
        } catch (JsonProcessingException e) {
            log.error("Error serializando AuthEvent para userId={}", event.getUserId(), e);
        }
    }
}
