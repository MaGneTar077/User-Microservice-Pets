package user.microservice.pets.application.dto;

/** refreshToken es opcional: si viene, el logout tambien revoca esa cadena. */
public record LogoutRequest(String refreshToken) {}
