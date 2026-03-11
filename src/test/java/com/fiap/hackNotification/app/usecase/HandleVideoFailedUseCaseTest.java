package com.fiap.hackNotification.app.usecase;

import com.fiap.hackNotification.adapters.in.kafka.dto.EventEnvelope;
import com.fiap.hackNotification.adapters.in.kafka.dto.UserPayload;
import com.fiap.hackNotification.adapters.in.kafka.dto.VideoFailedPayload;
import com.fiap.hackNotification.app.ports.EmailNotificationPort;
import com.fiap.hackNotification.app.ports.NotificationLoggerPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HandleVideoFailedUseCaseTest {

    @Test
    void shouldSendFailureEmailToUserAndLogEvent() {
        var logger = new LoggerSpy();
        var email = new EmailSpy();
        var useCase = new HandleVideoFailedUseCase(logger, email);

        var event = new EventEnvelope<>(
                "VideoFailed.v1",
                UUID.randomUUID(),
                Instant.now(),
                "corr-123",
                new VideoFailedPayload(
                        UUID.randomUUID(),
                        "ENCODING_FAILED",
                        "Codec inválido",
                        new UserPayload(UUID.randomUUID(), "Maria", "maria@email.com", "secret")
                )
        );

        useCase.handle(event);

        assertEquals("maria@email.com", email.to);
        assertEquals("Falha no processamento do vídeo", email.subject);
        assertEquals("corr-123", email.correlationId);
        assertEquals("corr-123", logger.correlationId);
    }

    private static class LoggerSpy implements NotificationLoggerPort {
        String correlationId;

        @Override
        public void logFailure(String correlationId, String message) {
            this.correlationId = correlationId;
        }
    }

    private static class EmailSpy implements EmailNotificationPort {
        String correlationId;
        String to;
        String subject;

        @Override
        public void sendFailureEmail(String correlationId, String to, String subject, String body) {
            this.correlationId = correlationId;
            this.to = to;
            this.subject = subject;
        }
    }
}