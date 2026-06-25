package com.agentsystem.notification.email.controller;

import com.agentsystem.notification.config.NotificationServiceProperties;
import com.agentsystem.notification.email.service.EmailService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Internal-only API consumed by agent-system-rest's NotificationClient.
 * Auth: shared {@code X-Notification-Key} header — NOT a user JWT.
 */
@RestController
@RequestMapping("/internal/notifications/email")
@RequiredArgsConstructor
public class InternalEmailController {

    private final EmailService emailService;
    private final NotificationServiceProperties serviceProperties;

    public record OtpRequest(String to, String code, int expiryMinutes) {}

    public record WorkflowCompleteRequest(String to, String workflowName, String status, String output) {}

    @PostMapping("/otp")
    public ResponseEntity<Void> sendOtp(
            @RequestHeader(value = "X-Notification-Key", required = false) String serviceKey,
            @RequestBody OtpRequest request) {

        if (!validKey(serviceKey)) return ResponseEntity.status(401).build();

        emailService.sendOtp(request.to(), request.code(), request.expiryMinutes());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/workflow-complete")
    public ResponseEntity<Void> sendWorkflowComplete(
            @RequestHeader(value = "X-Notification-Key", required = false) String serviceKey,
            @RequestBody WorkflowCompleteRequest request) {

        if (!validKey(serviceKey)) return ResponseEntity.status(401).build();

        emailService.sendWorkflowComplete(request.to(), request.workflowName(), request.status(), request.output());
        return ResponseEntity.ok().build();
    }

    private boolean validKey(String serviceKey) {
        return serviceKey != null && serviceKey.equals(serviceProperties.serviceKey());
    }
}
