package com.agentsystem.auth.notification;

import java.util.concurrent.TimeUnit;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes the OTP-requested event consumed by notification-consumer.
 * Same topic name and logical type-mapping id ("otpRequested") as agent-system-rest's
 * own NotificationClient — the consumer resolves by that logical name, not by FQCN, so
 * this module's own local event record works unchanged (see notification-consumer's
 * application.yml spring.json.type.mapping comment for why).
 */
@Component
public class NotificationClient {

    public static final String TOPIC_OTP = "notifications.otp";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public NotificationClient(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public record OtpRequestedEvent(String to, String code, int expiryMinutes) {}

    /**
     * Send a 6-digit login/registration OTP to {@code to}. Blocks briefly on broker
     * acknowledgment so the request still fails fast if Kafka itself is unreachable.
     */
    public void sendOtp(String to, String code, int expiryMinutes) {
        try {
            kafkaTemplate.send(TOPIC_OTP, to, new OtpRequestedEvent(to, code, expiryMinutes))
                    .get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send verification email. Please try again.", e);
        }
    }
}
