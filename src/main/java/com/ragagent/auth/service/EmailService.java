package com.ragagent.auth.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class EmailService {

    private final Resend resend;
    private final String fromEmail;

    public EmailService(
            @Value("${resend.api-key}") String apiKey,
            @Value("${resend.from-email}") String fromEmail) {
        this.resend = new Resend(apiKey);
        this.fromEmail = fromEmail;
    }

    /**
     * Notifies the workflow owner that their run has finished.
     */
    public void sendWorkflowComplete(String to, String workflowName, String status, String output) {
        boolean done = "DONE".equalsIgnoreCase(status);
        String statusLabel = done ? "completed" : "failed";
        String preview = output != null && !output.isBlank()
                ? (output.length() > 300 ? output.substring(0, 300) + "…" : output)
                : "(no output)";
        String html = """
                <div style="font-family:sans-serif;max-width:560px;margin:auto">
                  <h2 style="color:%s">Workflow %s: %s</h2>
                  <p>Your workflow run has %s.</p>
                  <div style="background:#f1f5f9;border-radius:.5rem;padding:1rem;
                              margin:1rem 0;font-size:.875rem;white-space:pre-wrap">%s</div>
                  <p style="color:#64748b;font-size:.875rem">
                    Log in to RAG Agent to view the full run details.
                  </p>
                </div>
                """.formatted(
                done ? "#16a34a" : "#dc2626",
                statusLabel,
                workflowName,
                statusLabel,
                preview);

        CreateEmailOptions options = CreateEmailOptions.builder()
                .from(fromEmail)
                .to(List.of(to))
                .subject("Workflow %s: %s".formatted(statusLabel, workflowName))
                .html(html)
                .build();

        try {
            resend.emails().send(options);
            log.info("[EmailService] Workflow-complete notification sent to {}", to);
        } catch (ResendException e) {
            log.warn("[EmailService] Failed to send workflow-complete notification to {}: {}", to, e.getMessage());
        }
    }

    /**
     * Send a 6-digit login OTP to {@code to}.
     * The email is sent via Resend's transactional API.
     */
    public void sendOtp(String to, String code, int expiryMinutes) {
        String html = """
                <div style="font-family:sans-serif;max-width:480px;margin:auto">
                  <h2 style="color:#4f46e5">Your login code</h2>
                  <p>Use the code below to sign in to RAG Agent.</p>
                  <div style="font-size:2.5rem;font-weight:700;letter-spacing:.5rem;
                              background:#f1f5f9;padding:1rem;border-radius:.5rem;
                              text-align:center;margin:1.5rem 0">%s</div>
                  <p style="color:#64748b;font-size:.875rem">
                    This code expires in %d minutes. Do not share it with anyone.
                  </p>
                </div>
                """.formatted(code, expiryMinutes);

        CreateEmailOptions options = CreateEmailOptions.builder()
                .from(fromEmail)
                .to(List.of(to))
                .subject("Your RAG Agent login code: " + code)
                .html(html)
                .build();

        try {
            resend.emails().send(options);
            log.info("[EmailService] OTP sent to {}", to);
        } catch (ResendException e) {
            log.error("[EmailService] Failed to send OTP to {}: {}", to, e.getMessage(), e);
            throw new RuntimeException("Failed to send verification email. Please try again.", e);
        }
    }
}
