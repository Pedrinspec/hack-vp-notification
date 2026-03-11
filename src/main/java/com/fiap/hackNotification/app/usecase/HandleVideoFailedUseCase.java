package com.fiap.hackNotification.app.usecase;

import com.fiap.hackNotification.adapters.in.kafka.dto.EventEnvelope;
import com.fiap.hackNotification.adapters.in.kafka.dto.VideoFailedPayload;
import com.fiap.hackNotification.app.ports.EmailNotificationPort;
import com.fiap.hackNotification.app.ports.NotificationLoggerPort;
import org.springframework.stereotype.Service;

@Service
public class HandleVideoFailedUseCase {
    private final NotificationLoggerPort logger;
    private final EmailNotificationPort emailNotification;

    public HandleVideoFailedUseCase(NotificationLoggerPort logger, EmailNotificationPort emailNotification) {
        this.logger = logger;
        this.emailNotification = emailNotification;
    }

    public void handle(EventEnvelope<VideoFailedPayload> event) {
        var p = event.payload();
        var body = "Olá %s,\n\nIdentificamos uma falha no processamento do seu vídeo.\n\nvideoId: %s\nMotivo: %s\nDetalhes: %s\n\nCorrelationId: %s\nEventId: %s"
                .formatted(p.user().name(), p.videoId(), p.reason(), p.details(), event.correlationId(), event.eventId());

        emailNotification.sendFailureEmail(
                event.correlationId(),
                p.user().email(),
                "Falha no processamento do vídeo",
                body
        );

        logger.logFailure(
                event.correlationId(),
                "NOTIFY Video failed email sent: videoId=%s reason=%s email=%s eventId=%s"
                        .formatted(p.videoId(), p.reason(), p.user().email(), event.eventId())
        );
    }
}
