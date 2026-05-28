package com.ragagent.financial.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "financial_cards")
@Getter
@Setter
@NoArgsConstructor
public class Card {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "owner_email", length = 255)
    private String ownerEmail;

    @Column(nullable = false, length = 255)
    private String bank;

    @Column(name = "country_region", length = 100)
    private String countryRegion;

    /** Comma-separated list: Credit, Debit, ATM */
    @Column(nullable = false, length = 50)
    private String types;

    @Column(name = "card_name", nullable = false, length = 255)
    private String cardName;

    /** Mastercard | Visa | UnionPay | JCB | AMEX */
    @Column(nullable = false, length = 20)
    private String network;

    /** YYYY-MM format */
    @Column(name = "expire_date", length = 7)
    private String expireDate;

    @Column(name = "credit_limit", precision = 19, scale = 2)
    private BigDecimal creditLimit;

    @Column(name = "credit_limit_currency", length = 10)
    private String creditLimitCurrency;

    /** null = unknown, true = shared pool, false = dedicated limit */
    @Column(name = "shared_credit")
    private Boolean sharedCredit;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
