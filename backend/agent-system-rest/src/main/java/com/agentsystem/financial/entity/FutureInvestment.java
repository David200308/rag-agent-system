package com.agentsystem.financial.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "financial_futures")
@Getter
@Setter
@NoArgsConstructor
public class FutureInvestment {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "owner_uuid", length = 36)
    private String ownerUuid;

    /** SECURITY | CRYPTO_CEX | CRYPTO_DEX */
    @Column(name = "exchange_kind", nullable = false, length = 20)
    private String exchangeKind;

    /** IBKR | BINANCE | OKX | KRAKEN | HYPERLIQUID */
    @Column(nullable = false, length = 20)
    private String exchange;

    /** Null for CRYPTO_DEX (address-only row; positions are fetched live). */
    @Column(length = 30)
    private String symbol;

    /** LONG | SHORT; null for CRYPTO_DEX. */
    @Column(length = 5)
    private String side;

    @Column(precision = 28, scale = 8)
    private BigDecimal quantity;

    @Column(name = "entry_price", precision = 19, scale = 4)
    private BigDecimal entryPrice;

    @Column(precision = 6, scale = 2)
    private BigDecimal leverage;

    @Column(nullable = false, length = 10)
    private String currency = "USD";

    /** Wallet address to auto-track; CRYPTO_DEX only. */
    @Column(name = "connection_address", length = 255)
    private String connectionAddress;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
