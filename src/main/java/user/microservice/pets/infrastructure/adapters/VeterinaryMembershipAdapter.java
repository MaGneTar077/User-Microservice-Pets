package user.microservice.pets.infrastructure.adapters;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import user.microservice.pets.domain.exceptions.VeterinaryServiceUnavailableException;
import user.microservice.pets.domain.model.VeterinaryMembership;
import user.microservice.pets.domain.ports.out.VeterinaryMembershipPort;

import java.util.UUID;

/**
 * Consulta GET /internal/veterinaries/{veterinaryId}/members/{userId} en Veterinary
 * (CONTRATOS_COMPARTIDOS.md). Cualquier respuesta que no sea 2xx, o un timeout/error de
 * conexion, se traduce a VeterinaryServiceUnavailableException: la unica forma valida de
 * leer "no es miembro" es un 200 con member=false, nunca un error HTTP.
 */
@Slf4j
@Component
public class VeterinaryMembershipAdapter implements VeterinaryMembershipPort {

    private final RestClient veterinaryRestClient;
    private final String veterinaryServiceUrl;

    public VeterinaryMembershipAdapter(
            RestClient veterinaryRestClient,
            @Value("${veterinary.service-url}") String veterinaryServiceUrl) {
        this.veterinaryRestClient = veterinaryRestClient;
        this.veterinaryServiceUrl = veterinaryServiceUrl;
    }

    @Override
    public VeterinaryMembership getMembership(UUID veterinaryId, UUID userId) {
        try {
            VeterinaryMembershipApiResponse response = veterinaryRestClient.get()
                    .uri("/internal/veterinaries/{veterinaryId}/members/{userId}", veterinaryId, userId)
                    .retrieve()
                    .body(VeterinaryMembershipApiResponse.class);

            return toDomain(response);
        } catch (RestClientResponseException e) {
            HttpStatusCode status = e.getStatusCode();
            String keyMismatchHint = (status.value() == 401 || status.value() == 403)
                    ? " - posible INTERNAL_API_KEY distinta entre user-service y veterinary-service"
                    : "";
            log.warn("Veterinary service respondio {} al consultar membresia (veterinaryId={}){}",
                    status.value(), veterinaryId, keyMismatchHint);
            throw new VeterinaryServiceUnavailableException(
                    "Veterinary service returned " + status.value(), e);
        } catch (ResourceAccessException e) {
            log.warn("No se pudo contactar a Veterinary service en {} (veterinaryId={}): {}",
                    veterinaryServiceUrl, veterinaryId, e.getMessage());
            throw new VeterinaryServiceUnavailableException("Veterinary service unreachable", e);
        }
    }

    private VeterinaryMembership toDomain(VeterinaryMembershipApiResponse response) {
        return new VeterinaryMembership(
                response.member(),
                response.employeeId(),
                response.role(),
                response.active(),
                response.licensed(),
                response.veterinaryStatus(),
                response.subscriptionStatus()
        );
    }

    private record VeterinaryMembershipApiResponse(
            boolean member,
            UUID employeeId,
            String role,
            Boolean active,
            Boolean licensed,
            String veterinaryStatus,
            String subscriptionStatus
    ) {
    }
}
