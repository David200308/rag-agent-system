package com.agentsystem.auth.service;

public interface PasskeyService {

    // ── Registration ─────────────────────────────────────────────────────────────

    String startRegistration(String email) throws Exception;

    void finishRegistration(String email, String responseJson) throws Exception;

    // ── Authentication ────────────────────────────────────────────────────────────

    String startAuthentication(String email, String mode, String orgId) throws Exception;

    String finishAuthentication(String email, String responseJson) throws Exception;

    // ── Status & Management ──────────────────────────────────────────────────────

    boolean hasPasskey(String email);

    void deletePasskeys(String email);
}
