package com.agentsystem.auth.service;

public interface EmailService {

    /**
     * Notifies the workflow owner that their run has finished.
     */
    void sendWorkflowComplete(String to, String workflowName, String status, String output);

    /**
     * Send a 6-digit login OTP to {@code to}.
     * The email is sent via Resend's transactional API.
     */
    void sendOtp(String to, String code, int expiryMinutes);
}
