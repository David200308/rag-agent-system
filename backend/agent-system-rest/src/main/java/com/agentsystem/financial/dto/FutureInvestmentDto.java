package com.agentsystem.financial.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record FutureInvestmentDto(
        String     id,
        String     ownerUuid,
        String     exchangeKind,
        String     exchange,
        String     symbol,
        String     side,
        BigDecimal quantity,
        BigDecimal entryPrice,
        BigDecimal leverage,
        String     currency,
        String     connectionAddress,
        /** Live mark/last price; null if unavailable. */
        Double     currentPrice,
        /** currentPrice x quantity, in {@code currency}; null if price unavailable. */
        BigDecimal currentValue,
        BigDecimal convertedInvestAmount,
        /** currentValue converted to defaultCurrency; null if price/position unavailable. */
        BigDecimal convertedCurrentValue,
        String     convertedCurrency,
        Double     pnlPercent,
        /** "MANUAL" for user-entered Security/CEX rows, "HYPERLIQUID" for live-fetched DEX positions. */
        String     source,
        /** Only set for HYPERLIQUID rows — the id of the tracked-address row this position was expanded from. */
        String     sourceConnectionId,
        Instant    createdAt,
        Instant    updatedAt
) {}
