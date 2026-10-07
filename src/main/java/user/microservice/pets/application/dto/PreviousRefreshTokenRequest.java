package user.microservice.pets.application.dto;

/** refreshToken es opcional: si viene (el del contexto anterior), se revoca al cambiar de contexto. */
public record PreviousRefreshTokenRequest(String refreshToken) {}
