package user.microservice.pets.domain.ports.out;

import user.microservice.pets.domain.model.VeterinaryMembership;

import java.util.UUID;

public interface VeterinaryMembershipPort {
    VeterinaryMembership getMembership(UUID veterinaryId, UUID userId);
}
