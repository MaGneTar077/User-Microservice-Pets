package user.microservice.pets.application.dto;

import java.util.List;
import java.util.UUID;

public record InternalUserBatchRequest(List<UUID> ids) {}
