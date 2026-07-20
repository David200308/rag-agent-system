package com.agentsystem.notification.email.service;

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

    /**
     * Notifies a user that one of their investment alert rules (price, DeFi, or
     * prediction-market) has fired.
     */
    void sendAlertTriggered(String to, String ruleType, String symbolOrProtocol, String message);
}
