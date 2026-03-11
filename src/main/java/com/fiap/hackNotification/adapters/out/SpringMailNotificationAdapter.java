package com.fiap.hackNotification.adapters.out;

import com.fiap.hackNotification.app.ports.EmailNotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SpringMailNotificationAdapter implements EmailNotificationPort {
    private static final Logger log = LoggerFactory.getLogger(SpringMailNotificationAdapter.class);

    private final JavaMailSender mailSender;
    private final String from;

    public SpringMailNotificationAdapter(JavaMailSender mailSender,
                                         @Value("${notification.email.from:no-reply@hack.local}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void sendFailureEmail(String correlationId, String to, String subject, String body) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);

        mailSender.send(message);
        log.info("correlationId={} Failure email sent to {}", correlationId, to);
    }
}
