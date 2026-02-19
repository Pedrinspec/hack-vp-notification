package com.fiap.hackNotification.app.ports;

public interface NotificationLoggerPort {
    void logFailure(String correlationId, String message);
}
