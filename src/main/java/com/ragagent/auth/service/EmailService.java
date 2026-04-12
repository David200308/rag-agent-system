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
