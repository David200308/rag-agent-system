package com.agentsystem.notification.email.service.impl;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.Emails;
import com.resend.services.emails.model.CreateEmailOptions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

    @Mock Resend resend;
    @Mock Emails emails;

    EmailServiceImpl service;

    @BeforeEach
    void setUp() {
        // EmailServiceImpl builds its own Resend client internally — swap it for a mock via reflection
        service = new EmailServiceImpl("test-api-key", "noreply@test.com", "https://example.com/logo.svg");
        ReflectionTestUtils.setField(service, "resend", resend);
        lenient().when(resend.emails()).thenReturn(emails);
    }

    // ── sendOtp ───────────────────────────────────────────────────────────────

    @Test
    void sendOtp_success_buildsOptionsWithCodeAndExpiry() throws Exception {
        service.sendOtp("user@test.com", "123456", 10);

        ArgumentCaptor<CreateEmailOptions> captor = ArgumentCaptor.forClass(CreateEmailOptions.class);
        verify(emails).send(captor.capture());
        CreateEmailOptions options = captor.getValue();

        assertThat(options.getFrom()).isEqualTo("noreply@test.com");
        assertThat(options.getTo()).containsExactly("user@test.com");
        assertThat(options.getSubject()).contains("123456");
        assertThat(options.getHtml()).contains("123456").contains("10 minutes");
    }

    @Test
    void sendOtp_resendThrows_wrapsInRuntimeException() throws Exception {
        when(emails.send(any(CreateEmailOptions.class))).thenThrow(new ResendException("boom"));

        assertThatThrownBy(() -> service.sendOtp("user@test.com", "123456", 10))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to send verification email");
    }

    // ── sendWorkflowComplete ──────────────────────────────────────────────────

    @Test
    void sendWorkflowComplete_done_buildsCompletedEmail() throws Exception {
        service.sendWorkflowComplete("user@test.com", "MyFlow", "DONE", "some output");

        ArgumentCaptor<CreateEmailOptions> captor = ArgumentCaptor.forClass(CreateEmailOptions.class);
        verify(emails).send(captor.capture());
        CreateEmailOptions options = captor.getValue();

        assertThat(options.getSubject()).contains("completed").contains("MyFlow");
        assertThat(options.getHtml()).contains("#16a34a").contains("some output");
    }

    @Test
    void sendWorkflowComplete_notDone_buildsFailedEmail() throws Exception {
        service.sendWorkflowComplete("user@test.com", "MyFlow", "FAILED", "error trace");

        ArgumentCaptor<CreateEmailOptions> captor = ArgumentCaptor.forClass(CreateEmailOptions.class);
        verify(emails).send(captor.capture());
        CreateEmailOptions options = captor.getValue();

        assertThat(options.getSubject()).contains("failed");
        assertThat(options.getHtml()).contains("#dc2626").contains("error trace");
    }

    @Test
    void sendWorkflowComplete_blankOutput_usesNoOutputPlaceholder() throws Exception {
        service.sendWorkflowComplete("user@test.com", "MyFlow", "DONE", "");

        ArgumentCaptor<CreateEmailOptions> captor = ArgumentCaptor.forClass(CreateEmailOptions.class);
        verify(emails).send(captor.capture());

        assertThat(captor.getValue().getHtml()).contains("(no output)");
    }

    @Test
    void sendWorkflowComplete_nullOutput_usesNoOutputPlaceholder() throws Exception {
        service.sendWorkflowComplete("user@test.com", "MyFlow", "DONE", null);

        ArgumentCaptor<CreateEmailOptions> captor = ArgumentCaptor.forClass(CreateEmailOptions.class);
        verify(emails).send(captor.capture());

        assertThat(captor.getValue().getHtml()).contains("(no output)");
    }

    @Test
    void sendWorkflowComplete_longOutput_truncatedWithEllipsis() throws Exception {
        String longOutput = "x".repeat(400);

        service.sendWorkflowComplete("user@test.com", "MyFlow", "DONE", longOutput);

        ArgumentCaptor<CreateEmailOptions> captor = ArgumentCaptor.forClass(CreateEmailOptions.class);
        verify(emails).send(captor.capture());

        assertThat(captor.getValue().getHtml()).contains("x".repeat(300) + "…");
        assertThat(captor.getValue().getHtml()).doesNotContain("x".repeat(301));
    }

    @Test
    void sendWorkflowComplete_resendThrows_isSwallowed() throws Exception {
        when(emails.send(any(CreateEmailOptions.class))).thenThrow(new ResendException("boom"));

        assertThatCode(() -> service.sendWorkflowComplete("user@test.com", "MyFlow", "DONE", "output"))
                .doesNotThrowAnyException();
    }
}
