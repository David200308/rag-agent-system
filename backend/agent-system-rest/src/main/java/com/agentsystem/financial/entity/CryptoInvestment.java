package com.ragagent.financial.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "financial_crypto")
@Getter
@Setter
@NoArgsConstructor
public class CryptoInvestment {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "owner_email", length = 255)
    private String ownerEmail;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 30)
    private String symbol;

    @Column(nullable = false, precision = 28, scale = 8)
    private BigDecimal amount;

    @Column(name = "invest_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal investAmount;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
