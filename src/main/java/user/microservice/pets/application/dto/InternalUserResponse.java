package user.microservice.pets.application.dto;

import java.util.UUID;

/**
 * USER-06. Nunca debe incluir password, hashes, ni ningun dato de autenticacion.
 * fullName = username y phone = null por ahora: la tabla users no tiene columnas
 * full_name/phone todavia (ver CLAUDE.md, deuda tecnica).
 */
public record InternalUserResponse(UUID id, String fullName, String email, String phone, boolean emailVerified) {}
