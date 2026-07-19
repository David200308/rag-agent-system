package com.agentsystem.notification.email.listener;

import com.agentsystem.notification.email.service.EmailService;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes notification events published by agent-system-rest's NotificationClient.
 * Topic names must stay in sync with that producer.
 */
@Component
@RequiredArgsConstructor
public class EmailEventListener {

    public static final String TOPIC_OTP = "notifications.otp";
    public static final String TOPIC_WORKFLOW_COMPLETE = "notifications.workflow-complete";

    private final EmailService emailService;

    public record OtpRequestedEvent(String to, String code, int expiryMinutes) {}

    public record WorkflowCompletedEvent(String to, String workflowName, String status, String output) {}

    @KafkaListener(topics = TOPIC_OTP, groupId = "notification-consumer")
    public void onOtpRequested(OtpRequestedEvent event) {
        emailService.sendOtp(event.to(), event.code(), event.expiryMinutes());
    }

    @KafkaListener(topics = TOPIC_WORKFLOW_COMPLETE, groupId = "notification-consumer")
    public void onWorkflowCompleted(WorkflowCompletedEvent event) {
        emailService.sendWorkflowComplete(event.to(), event.workflowName(), event.status(), event.output());
    }
}
