package com.agentsystem.notification;

import java.util.concurrent.TimeUnit;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes notification events consumed by agent-system-notification-consumer.
 * Topic names must stay in sync with that consumer's @KafkaListener topics.
 */
@Component
public class NotificationClient {

    public static final String TOPIC_OTP = "notifications.otp";
    public static final String TOPIC_WORKFLOW_COMPLETE = "notifications.workflow-complete";
    public static final String TOPIC_ALERT_TRIGGERED = "notifications.alert-triggered";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public NotificationClient(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    private record OtpRequestedEvent(String to, String code, int expiryMinutes) {}

    private record WorkflowCompletedEvent(String to, String workflowName, String status, String output) {}

    private record AlertTriggeredEvent(String to, String ruleType, String symbolOrProtocol, String message) {}

    /**
     * Send a 6-digit login OTP to {@code to}.
     * Blocks briefly on broker acknowledgment so the login/register request still fails
     * fast if Kafka itself is unreachable; actual email delivery happens asynchronously
     * in the consumer.
     */
    public void sendOtp(String to, String code, int expiryMinutes) {
        try {
            kafkaTemplate.send(TOPIC_OTP, to, new OtpRequestedEvent(to, code, expiryMinutes))
                    .get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send verification email. Please try again.", e);
        }
    }

    /**
     * Notifies the workflow owner that their run has finished.
     */
    public void sendWorkflowComplete(String to, String workflowName, String status, String output) {
        kafkaTemplate.send(TOPIC_WORKFLOW_COMPLETE, to,
                new WorkflowCompletedEvent(to, workflowName, status, output));
    }

    /**
     * Notifies a user that one of their investment alert rules (price, DeFi, or
     * prediction-market) has fired.
     */
    public void sendAlertTriggered(String to, String ruleType, String symbolOrProtocol, String message) {
        kafkaTemplate.send(TOPIC_ALERT_TRIGGERED, to,
                new AlertTriggeredEvent(to, ruleType, symbolOrProtocol, message));
    }
}
