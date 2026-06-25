package com.agentsystem.notification;

import com.agentsystem.config.NotificationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Calls agent-system-notification-inner's internal REST API on behalf of authenticated users.
 * Uses X-Notification-Key authentication — no JWT needed (mirrors StorageClient).
 */
@Component
public class NotificationClient {

    private final RestClient restClient;

    public NotificationClient(NotificationProperties props) {
        this.restClient = RestClient.builder()
                .baseUrl(props.url())
                .defaultHeader("X-Notification-Key", props.serviceKey())
                .build();
    }

    private record OtpRequest(String to, String code, int expiryMinutes) {}

    private record WorkflowCompleteRequest(String to, String workflowName, String status, String output) {}

    /**
     * Send a 6-digit login OTP to {@code to}.
     */
    public void sendOtp(String to, String code, int expiryMinutes) {
        restClient.post()
                .uri("/internal/notifications/email/otp")
                .body(new OtpRequest(to, code, expiryMinutes))
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Notifies the workflow owner that their run has finished.
     */
    public void sendWorkflowComplete(String to, String workflowName, String status, String output) {
        restClient.post()
                .uri("/internal/notifications/email/workflow-complete")
                .body(new WorkflowCompleteRequest(to, workflowName, status, output))
                .retrieve()
                .toBodilessEntity();
    }
}
