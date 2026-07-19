package com.agentsystem.notification.email.listener;

import com.agentsystem.notification.email.service.EmailService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailEventListenerTest {

    @Mock EmailService emailService;

    EmailEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new EmailEventListener(emailService);
    }

    @Test
    void onOtpRequested_delegatesToEmailService() {
        listener.onOtpRequested(new EmailEventListener.OtpRequestedEvent("user@test.com", "123456", 10));

        verify(emailService).sendOtp("user@test.com", "123456", 10);
    }

    @Test
    void onWorkflowCompleted_delegatesToEmailService() {
        listener.onWorkflowCompleted(
                new EmailEventListener.WorkflowCompletedEvent("user@test.com", "MyFlow", "DONE", "output"));

        verify(emailService).sendWorkflowComplete("user@test.com", "MyFlow", "DONE", "output");
    }
}
