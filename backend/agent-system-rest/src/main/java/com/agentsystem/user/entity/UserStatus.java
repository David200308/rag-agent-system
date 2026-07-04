package com.agentsystem.user.entity;

/**
 * PRE_USER — registered and email-verified, awaiting manual admin approval (no login access).
 * USER     — approved; can request/verify OTP or passkey and receive a JWT.
 */
public enum UserStatus {
    PRE_USER,
    USER
}
