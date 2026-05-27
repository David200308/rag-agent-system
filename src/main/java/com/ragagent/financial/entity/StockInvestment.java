package com.ragagent.financial.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "financial_stocks")
@Getter
@Setter
@NoArgsConstructor
public class StockInvestment {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "owner_email", length = 255)
    private String ownerEmail;

    @Column(nullable = false, length = 255)
    private String broker;

    /** US_STOCK | HK_STOCK | CN_STOCK | SG_STOCK | OTHER */
    @Column(name = "stock_type", nullable = false, length = 20)
    private String stockType;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "stock_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal stockAmount;

    @Column(name = "invest_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal investAmount;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal fee = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
