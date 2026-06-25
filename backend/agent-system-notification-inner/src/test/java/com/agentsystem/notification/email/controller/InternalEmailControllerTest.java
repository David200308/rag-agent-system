package com.agentsystem.notification.email.controller;

import com.agentsystem.notification.config.NotificationServiceProperties;
import com.agentsystem.notification.email.service.EmailService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class InternalEmailControllerTest {

    @Mock EmailService emailService;

    // NotificationServiceProperties is a record (final) — instantiate directly
    NotificationServiceProperties serviceProperties = new NotificationServiceProperties("secret-key");

    InternalEmailController controller;

    @BeforeEach
    void setUp() {
        controller = new InternalEmailController(emailService, serviceProperties);
    }

    // ── /otp ──────────────────────────────────────────────────────────────────

    @Test
    void sendOtp_nullServiceKey_returns401() {
        var req = new InternalEmailController.OtpRequest("user@test.com", "123456", 10);

        ResponseEntity<Void> resp = controller.sendOtp(null, req);

        assertThat(resp.getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(emailService);
    }

    @Test
    void sendOtp_wrongServiceKey_returns401() {
        var req = new InternalEmailController.OtpRequest("user@test.com", "123456", 10);

        ResponseEntity<Void> resp = controller.sendOtp("wrong-key", req);

        assertThat(resp.getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(emailService);
    }

    @Test
    void sendOtp_validKey_callsServiceAndReturns200() {
        var req = new InternalEmailController.OtpRequest("user@test.com", "123456", 10);

        ResponseEntity<Void> resp = controller.sendOtp("secret-key", req);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        verify(emailService).sendOtp("user@test.com", "123456", 10);
    }

    // ── /workflow-complete ────────────────────────────────────────────────────

    @Test
    void sendWorkflowComplete_nullServiceKey_returns401() {
        var req = new InternalEmailController.WorkflowCompleteRequest("user@test.com", "MyFlow", "DONE", "output");

        ResponseEntity<Void> resp = controller.sendWorkflowComplete(null, req);

        assertThat(resp.getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(emailService);
    }

    @Test
    void sendWorkflowComplete_wrongServiceKey_returns401() {
        var req = new InternalEmailController.WorkflowCompleteRequest("user@test.com", "MyFlow", "DONE", "output");

        ResponseEntity<Void> resp = controller.sendWorkflowComplete("wrong-key", req);

        assertThat(resp.getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(emailService);
    }

    @Test
    void sendWorkflowComplete_validKey_callsServiceAndReturns200() {
        var req = new InternalEmailController.WorkflowCompleteRequest("user@test.com", "MyFlow", "DONE", "output");

        ResponseEntity<Void> resp = controller.sendWorkflowComplete("secret-key", req);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        verify(emailService).sendWorkflowComplete("user@test.com", "MyFlow", "DONE", "output");
    }
}
