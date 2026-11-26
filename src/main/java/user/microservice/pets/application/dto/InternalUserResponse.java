package user.microservice.pets.application.dto;

import java.util.UUID;

/**
 * USER-06. Nunca debe incluir password, hashes, ni ningun dato de autenticacion.
 * fullName trae fallback al username cuando full_name es null.
 */
public record InternalUserResponse(UUID id, String fullName, String email, String phone, boolean emailVerified) {}
