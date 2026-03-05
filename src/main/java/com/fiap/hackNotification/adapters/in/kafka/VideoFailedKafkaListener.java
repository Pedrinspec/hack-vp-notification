package com.fiap.hackNotification.adapters.in.kafka;

import com.fiap.hackNotification.adapters.in.kafka.dto.EventEnvelope;
import com.fiap.hackNotification.adapters.in.kafka.dto.VideoFailedPayload;
import com.fiap.hackNotification.app.usecase.HandleVideoFailedUseCase;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

@Component
public class VideoFailedKafkaListener {
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final HandleVideoFailedUseCase useCase;

    public VideoFailedKafkaListener(ObjectMapper objectMapper, Validator validator, HandleVideoFailedUseCase useCase) {
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.useCase = useCase;
    }

    @KafkaListener(topics = "${notification.topics.videoFailed}")
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) throws Exception {
        EventEnvelope<VideoFailedPayload> event =
                objectMapper.readValue(
                        record.value(),
                        objectMapper.getTypeFactory().constructParametricType(EventEnvelope.class, VideoFailedPayload.class)
                );

        Set<ConstraintViolation<EventEnvelope<VideoFailedPayload>>> violations = validator.validate(event);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException("Invalid event envelope: " + violations);
        }

        if (!"VideoFailed.v1".equals(event.eventName())) {
            throw new IllegalArgumentException("Unexpected eventName=" + event.eventName());
        }

        useCase.handle(event);

        ack.acknowledge();
    }
}
