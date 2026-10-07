package user.microservice.pets.domain.model;

import java.util.UUID;

/**
 * Respuesta de Veterinary a GET /internal/veterinaries/{veterinaryId}/members/{userId}
 * (CONTRATOS_COMPARTIDOS.md). "role" y "veterinaryStatus" se guardan como String (no enum):
 * Veterinary puede agregar valores nuevos sin coordinar con este servicio.
 */
public record VeterinaryMembership(
        boolean member,
        UUID employeeId,
        String role,
        Boolean active,
        Boolean licensed,
        String veterinaryStatus,
        String subscriptionStatus
) {
    public boolean isActiveMember() {
        return member && Boolean.TRUE.equals(active);
    }

    public boolean isLicensed() {
        return Boolean.TRUE.equals(licensed);
    }
}
