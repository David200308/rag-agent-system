package com.ragagent.financial.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record CryptoInvestmentDto(
        String     id,
        String     ownerEmail,
        String     name,
        String     symbol,
        BigDecimal amount,
        BigDecimal investAmount,
        String     currency,
        /** Live price in USDT from Binance; null if unavailable. */
        Double     currentPrice,
        /** currentPrice × amount (in USDT); null if price unavailable. */
        BigDecimal currentValue,
        BigDecimal convertedInvestAmount,
        /** currentValue converted to defaultCurrency; null if price unavailable. */
        BigDecimal convertedCurrentValue,
        String     convertedCurrency,
        Instant    createdAt,
        Instant    updatedAt
) {}
