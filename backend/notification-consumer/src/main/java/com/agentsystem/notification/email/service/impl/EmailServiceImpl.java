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
     *
     * Declares color-scheme so mail clients that honor it (Apple/iOS Mail, most modern webmail)
     * render our explicit dark palette below instead of falling back to their own auto-invert
     * heuristic — which is what was mangling the (single-color, black-on-transparent) logo into
     * a barely-visible smudge in dark mode. Clients that ignore prefers-color-scheme entirely
     * (e.g. Outlook desktop) just get the light-mode styles, unchanged from before.
     */
    private String buildShell(String bodyHtml, String brandName) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="utf-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1" />
                  <meta name="color-scheme" content="light dark" />
                  <meta name="supported-color-schemes" content="light dark" />
                  <style>
                    :root { color-scheme: light dark; supported-color-schemes: light dark; }
                    body { margin:0; padding:0; }
                    .email-logo { display:block; height:36px; width:auto; }
                    @media (prefers-color-scheme: dark) {
                      .email-bg      { background-color:#09090b !important; }
                      .email-card    { background-color:#18181b !important; border-color:#27272a !important; }
                      .email-header  { border-color:#27272a !important; }
                      .email-logo    { filter:invert(1) brightness(1.7); }
                      .email-footer  { background-color:#111113 !important; border-color:#27272a !important; }
                      .email-footnote{ color:#71717a !important; }
                      .email-heading { color:#f4f4f5 !important; }
                      .email-subtext { color:#a1a1aa !important; }
                      .email-panel   { background-color:#27272a !important; border-color:#3f3f46 !important; color:#d4d4d8 !important; }
                      .email-code    { background-color:#27272a !important; color:#f4f4f5 !important; }
                    }
                  </style>
                </head>
                <body class="email-bg" style="background-color:#f4f4f7;padding:40px 16px;font-family:%s">
                  <div class="email-card" style="max-width:480px;margin:0 auto;background:#ffffff;border-radius:12px;
                              overflow:hidden;border:1px solid #e5e7eb">
                    <div class="email-header" style="padding:24px 32px;border-bottom:1px solid #f1f5f9">
                      <img class="email-logo" src="%s" alt="%s" height="36" style="display:block;height:36px;width:auto" />
                    </div>
                    <div style="padding:32px">
                      %s
                    </div>
                    <div class="email-footer" style="padding:20px 32px;background:#fafafa;border-top:1px solid #f1f5f9">
                      <p class="email-footnote" style="margin:0;color:#94a3b8;font-size:12px;line-height:1.5">
                        This is an automated message from %s. If you didn't request this,
                        you can safely ignore it.
                      </p>
                    </div>
                  </div>
                </body>
                </html>
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
                <h2 class="email-heading" style="margin:0 0 12px;color:#111827;font-size:20px;font-weight:600">%s</h2>
                <div style="margin:0 0 20px">
                  <span style="display:inline-block;padding:3px 10px;border-radius:999px;background:%s;
                               color:%s;font-size:12px;font-weight:600;text-transform:uppercase;
                               letter-spacing:.03em">%s</span>
                </div>
                <div class="email-panel" style="background:#f8fafc;border:1px solid #e5e7eb;border-radius:8px;padding:16px;
                            margin:0 0 20px;font-size:13px;line-height:1.5;color:#334155;
                            white-space:pre-wrap;font-family:ui-monospace,SFMono-Regular,Menlo,monospace">%s</div>
                <p class="email-footnote" style="margin:0;color:#94a3b8;font-size:13px">
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
     * Notifies a user that one of their investment alert rules (price, DeFi, or
     * prediction-market) has fired.
     */
    @Override
    public void sendAlertTriggered(String to, String ruleType, String symbolOrProtocol, String message) {
        String body = """
                <h2 class="email-heading" style="margin:0 0 12px;color:#111827;font-size:20px;font-weight:600">🚨 Alert triggered</h2>
                <div style="margin:0 0 20px">
                  <span style="display:inline-block;padding:3px 10px;border-radius:999px;background:#fef2f2;
                               color:#dc2626;font-size:12px;font-weight:600;text-transform:uppercase;
                               letter-spacing:.03em">%s</span>
                </div>
                <div class="email-panel" style="background:#f8fafc;border:1px solid #e5e7eb;border-radius:8px;padding:16px;
                            margin:0 0 20px;font-size:14px;line-height:1.5;color:#334155">%s</div>
                <p class="email-footnote" style="margin:0;color:#94a3b8;font-size:13px">
                  Log in to Agent System to view or update this alert rule.
                </p>
                """.formatted(ruleType, message);

        CreateEmailOptions options = CreateEmailOptions.builder()
                .from(fromEmail)
                .to(List.of(to))
                .subject("Alert triggered: " + symbolOrProtocol)
                .html(buildShell(body, "Agent System"))
                .build();

        try {
            resend.emails().send(options);
            log.info("[EmailService] Alert-triggered notification sent to {}", to);
        } catch (ResendException e) {
            log.warn("[EmailService] Failed to send alert-triggered notification to {}: {}", to, e.getMessage());
        }
    }

    /**
     * Send a 6-digit login OTP to {@code to}.
     * The email is sent via Resend's transactional API.
     */
    @Override
    public void sendOtp(String to, String code, int expiryMinutes) {
        String body = """
                <h2 class="email-heading" style="margin:0 0 8px;color:#111827;font-size:20px;font-weight:600">Your login code</h2>
                <p class="email-subtext" style="margin:0 0 24px;color:#4b5563;font-size:14px;line-height:1.6">
                  Use the code below to sign in to Agent System.
                </p>
                <div class="email-code" style="font-size:36px;font-weight:700;letter-spacing:.4rem;color:#111827;
                            background:#f3f4f6;padding:20px;border-radius:10px;
                            text-align:center;margin:0 0 24px">%s</div>
                <p class="email-footnote" style="margin:0;color:#94a3b8;font-size:13px">
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
