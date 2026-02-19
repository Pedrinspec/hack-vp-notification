package com.fiap.hackNotification.adapters.out;

import com.fiap.hackNotification.app.ports.NotificationLoggerPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class Slf4jNotificationLoggerAdapter implements NotificationLoggerPort {
    private static final Logger log = LoggerFactory.getLogger(Slf4jNotificationLoggerAdapter.class);

    @Override
    public void logFailure(String correlationId, String message) {
        log.error("correlationId={} {}", correlationId, message);
    }
}
