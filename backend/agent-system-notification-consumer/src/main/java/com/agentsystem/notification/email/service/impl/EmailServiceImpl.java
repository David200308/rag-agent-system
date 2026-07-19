package com.agentsystem.notification.email.service.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.agentsystem.notification.email.service.EmailService;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    private static final String FONT_STACK =
            "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif";

    private final Resend resend;
    private final String fromEmail;
    private final String logoUrl;

    public EmailServiceImpl(
            @Value("${resend.api-key}") String apiKey,
            @Value("${resend.from-email}") String fromEmail,
            @Value("${notification.email.logo-url}") String logoUrl) {
        this.resend = new Resend(apiKey);
        this.fromEmail = fromEmail;
        this.logoUrl = logoUrl;
    }

    /**
     * Wraps email body content in the shared header/footer shell used by all notification emails.
     */
    private String buildShell(String bodyHtml, String brandName) {
        return """
                <div style="background-color:#f4f4f7;padding:40px 16px;font-family:%s">
                  <div style="max-width:480px;margin:0 auto;background:#ffffff;border-radius:12px;
                              overflow:hidden;border:1px solid #e5e7eb">
                    <div style="padding:24px 32px;border-bottom:1px solid #f1f5f9">
                      <img src="%s" alt="%s" height="24" style="display:block;height:24px;width:auto" />
                    </div>
                    <div style="padding:32px">
                      %s
                    </div>
                    <div style="padding:20px 32px;background:#fafafa;border-top:1px solid #f1f5f9">
                      <p style="margin:0;color:#94a3b8;font-size:12px;line-height:1.5">
                        This is an automated message from %s. If you didn't request this,
                        you can safely ignore it.
                      </p>
                    </div>
                  </div>
                </div>
                """.formatted(FONT_STACK, logoUrl, brandName, bodyHtml, brandName);
    }

    /**
     * Notifies the workflow owner that their run has finished.
     */
    @Override
    public void sendWorkflowComplete(String to, String workflowName, String status, String output) {
        boolean done = "DONE".equalsIgnoreCase(status);
        String statusLabel = done ? "completed" : "failed";
        String statusColor = done ? "#16a34a" : "#dc2626";
        String statusBg = done ? "#f0fdf4" : "#fef2f2";
        String preview = output != null && !output.isBlank()
                ? (output.length() > 300 ? output.substring(0, 300) + "…" : output)
                : "(no output)";
        String body = """
                <h2 style="margin:0 0 12px;color:#111827;font-size:20px;font-weight:600">%s</h2>
                <div style="margin:0 0 20px">
                  <span style="display:inline-block;padding:3px 10px;border-radius:999px;background:%s;
                               color:%s;font-size:12px;font-weight:600;text-transform:uppercase;
                               letter-spacing:.03em">%s</span>
                </div>
                <div style="background:#f8fafc;border:1px solid #e5e7eb;border-radius:8px;padding:16px;
                            margin:0 0 20px;font-size:13px;line-height:1.5;color:#334155;
                            white-space:pre-wrap;font-family:ui-monospace,SFMono-Regular,Menlo,monospace">%s</div>
                <p style="margin:0;color:#94a3b8;font-size:13px">
                  Log in to Agent System to view the full run details.
                </p>
                """.formatted(workflowName, statusBg, statusColor, statusLabel, preview);

        CreateEmailOptions options = CreateEmailOptions.builder()
                .from(fromEmail)
                .to(List.of(to))
                .subject("Workflow %s: %s".formatted(statusLabel, workflowName))
                .html(buildShell(body, "Agent System"))
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
    @Override
    public void sendOtp(String to, String code, int expiryMinutes) {
        String body = """
                <h2 style="margin:0 0 8px;color:#111827;font-size:20px;font-weight:600">Your login code</h2>
                <p style="margin:0 0 24px;color:#4b5563;font-size:14px;line-height:1.6">
                  Use the code below to sign in to Agent System.
                </p>
                <div style="font-size:36px;font-weight:700;letter-spacing:.4rem;color:#111827;
                            background:#f3f4f6;padding:20px;border-radius:10px;
                            text-align:center;margin:0 0 24px">%s</div>
                <p style="margin:0;color:#94a3b8;font-size:13px">
                  This code expires in %d minutes. Do not share it with anyone.
                </p>
                """.formatted(code, expiryMinutes);

        CreateEmailOptions options = CreateEmailOptions.builder()
                .from(fromEmail)
                .to(List.of(to))
                .subject("Your Agent System login code: " + code)
                .html(buildShell(body, "SkyProton"))
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
