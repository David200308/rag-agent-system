package com.agentsystem.financial.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record StockInvestmentDto(
        String     id,
        String     ownerUuid,
        String     broker,
        String     stockType,
        String     symbol,
        String     name,
        BigDecimal stockAmount,
        BigDecimal investAmount,
        String     currency,
        BigDecimal fee,
        /** Live price from Yahoo Finance in priceCurrency; null if unavailable. */
        Double     currentPrice,
        /** Currency of the live price (e.g. USD, HKD). */
        String     priceCurrency,
        /** Company logo image URL from Finnhub; null if unavailable. */
        String     logoUrl,
        /** currentPrice × stockAmount; null if price unavailable. */
        BigDecimal currentValue,
        BigDecimal convertedInvestAmount,
        /** convertedCurrentValue uses currentValue converted to defaultCurrency; null if price unavailable. */
        BigDecimal convertedCurrentValue,
        String     convertedCurrency,
        /** (convertedCurrentValue - convertedInvestAmount) / convertedInvestAmount * 100; null if price unavailable. */
        Double     pnlPercent,
        Instant    createdAt,
        Instant    updatedAt
) {}
