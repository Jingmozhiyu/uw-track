package com.jing.monitor.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jing.monitor.model.AlertDeadLetter;
import com.jing.monitor.model.AlertDeliveryLog;
import com.jing.monitor.model.AlertType;
import com.jing.monitor.model.event.AlertEvent;
import com.jing.monitor.repository.AlertDeadLetterRepository;
import com.jing.monitor.repository.AlertDeliveryLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Consumes alert events from RabbitMQ and handles dead-letter persistence.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertConsumerService {

    private final MailService mailService;
    private final AlertDeadLetterRepository alertDeadLetterRepository;
    private final AlertDeliveryLogRepository alertDeliveryLogRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.rabbitmq.queue}")
    private String alertQueueName;

    /**
     * Delivers one queued alert email. Failures are rejected and routed into the DLQ.
     *
     * @param event queued alert payload
     */
    @RabbitListener(queues = "${app.rabbitmq.queue}")
    public void consumeAlert(AlertEvent event) {
        try {
            if (event.getAlertType() == AlertType.OPEN) {
                mailService.sendCourseOpenAlert(event.getRecipientEmail(), event.getSectionId(), event.getCourseDisplayName());
            } else if (event.getAlertType() == AlertType.WAITLIST) {
                mailService.sendCourseWaitlistedAlert(event.getRecipientEmail(), event.getSectionId(), event.getCourseDisplayName());
            } else {
                throw new IllegalArgumentException("Unsupported alert type: " + event.getAlertType());
            }

            saveDeliveryLogQuietly(event);
        } catch (Exception e) {
            log.error("[AlertConsumer] Mail send failed for event {} on queue {}", event.getEventId(), alertQueueName, e);
            throw new AmqpRejectAndDontRequeueException("Mail send failed: " + e.getMessage(), e);
        }
    }

    /**
     * Persists dead-letter events for manual inspection and follow-up.
     * Spring AMQP calls this method automatically after a rejected message lands in the DLQ.
     *
     * @param event dead-lettered alert payload
     * @param message raw AMQP message with dead-letter headers
     */
    @RabbitListener(queues = "${app.rabbitmq.dlq}")
    @Transactional
    public void consumeDeadLetter(AlertEvent event, Message message) {
        AlertDeadLetter deadLetter = new AlertDeadLetter();
        deadLetter.setEventId(event == null ? null : event.getEventId());
        deadLetter.setAlertType(event == null || event.getAlertType() == null ? null : event.getAlertType().name());
        deadLetter.setRecipientEmail(event == null ? null : event.getRecipientEmail());
        deadLetter.setSectionId(event == null ? null : event.getSectionId());
        deadLetter.setCourseDisplayName(event == null ? null : event.getCourseDisplayName());
        deadLetter.setReason(extractDeadLetterReason(message));
        deadLetter.setSourceQueue(extractSourceQueue(message));
        deadLetter.setCreatedAt(LocalDateTime.now());
        deadLetter.setPayloadJson(serializePayload(event));
        alertDeadLetterRepository.save(deadLetter);

        log.error("[AlertConsumer] Dead letter saved for event {} from queue {} with reason {}",
                deadLetter.getEventId(), deadLetter.getSourceQueue(), deadLetter.getReason());
    }

    private String extractDeadLetterReason(Message message) {
        Object firstDeathReason = message.getMessageProperties().getHeaders().get("x-first-death-reason");
        if (firstDeathReason != null) {
            return String.valueOf(firstDeathReason);
        }

        Object xDeath = message.getMessageProperties().getHeaders().get("x-death");
        return xDeath == null ? "unknown" : String.valueOf(xDeath);
    }

    private String extractSourceQueue(Message message) {
        Object xDeath = message.getMessageProperties().getHeaders().get("x-death");
        if (xDeath instanceof Iterable<?> iterable) {
            for (Object entry : iterable) {
                if (entry instanceof Map<?, ?> deathMap) {
                    Object queue = deathMap.get("queue");
                    if (queue != null) {
                        return String.valueOf(queue);
                    }
                }
            }
        }
        return message.getMessageProperties().getConsumerQueue();
    }

    private String serializePayload(AlertEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            return "{\"serializationError\":\"" + e.getMessage() + "\"}";
        }
    }

    private void saveDeliveryLogQuietly(AlertEvent event) {
        try {
            AlertDeliveryLog deliveryLog = new AlertDeliveryLog();
            deliveryLog.setEventId(event.getEventId());
            deliveryLog.setAlertType(event.getAlertType().name());
            deliveryLog.setRecipientEmail(event.getRecipientEmail());
            deliveryLog.setSectionId(event.getSectionId());
            deliveryLog.setCourseDisplayName(event.getCourseDisplayName());
            deliveryLog.setSourceQueue(alertQueueName);
            deliveryLog.setManualTest(event.isManualTest());
            deliveryLog.setSentAt(LocalDateTime.now());
            alertDeliveryLogRepository.save(deliveryLog);
        } catch (Exception e) {
            log.error("[AlertConsumer] Mail was sent, but delivery log persistence failed for event {}", event.getEventId(), e);
        }
    }
}
