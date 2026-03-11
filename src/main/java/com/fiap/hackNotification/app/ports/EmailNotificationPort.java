package com.fiap.hackNotification.app.ports;

public interface EmailNotificationPort {
    void sendFailureEmail(String correlationId, String to, String subject, String body);
}
