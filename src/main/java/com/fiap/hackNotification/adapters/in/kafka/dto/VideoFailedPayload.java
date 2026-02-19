package com.fiap.hackNotification.adapters.in.kafka.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record VideoFailedPayload(
        @NotNull UUID videoId,
        @NotBlank String reason,
        String details
) {}
