package com.fiap.hackNotification.adapters.in.kafka.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UserPayload(
        @NotNull UUID id,
        @NotBlank String name,
        @NotBlank @Email String email,
        @NotBlank String password
) {}