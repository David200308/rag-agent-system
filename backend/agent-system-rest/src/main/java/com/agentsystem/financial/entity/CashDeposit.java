package com.agentsystem.financial.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "financial_cash_deposits")
@Getter
@Setter
@NoArgsConstructor
public class CashDeposit {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "owner_email", length = 255)
    private String ownerEmail;

    @Column(nullable = false, length = 255)
    private String platform;

    @Column(name = "platform_type", nullable = false, length = 100)
    private String platformType;

    @Column(name = "country_region", length = 100)
    private String countryRegion;

    /** FIXED or FLEX */
    @Column(name = "deposit_type", nullable = false, length = 10)
    private String depositType;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
