package com.fiap.hackNotification.adapters.in.kafka.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record EventEnvelope<T>(
        @NotBlank String eventName,
        @NotNull UUID eventId,
        @NotNull Instant occurredAt,
        @NotBlank String correlationId,
        @NotNull @Valid T payload
) {}
