package com.fiap.hackNotification.app.usecase;

import com.fiap.hackNotification.adapters.in.kafka.dto.EventEnvelope;
import com.fiap.hackNotification.adapters.in.kafka.dto.VideoFailedPayload;
import com.fiap.hackNotification.app.ports.NotificationLoggerPort;
import org.springframework.stereotype.Service;

@Service
public class HandleVideoFailedUseCase {
    private final NotificationLoggerPort logger;

    public HandleVideoFailedUseCase(NotificationLoggerPort logger) {
        this.logger = logger;
    }

    public void handle(EventEnvelope<VideoFailedPayload> event) {
        var p = event.payload();
        logger.logFailure(
                event.correlationId(),
                "NOTIFY Video failed: videoId=%s reason=%s details=%s eventId=%s"
                        .formatted(p.videoId(), p.reason(), p.details(), event.eventId())
        );
    }
}
