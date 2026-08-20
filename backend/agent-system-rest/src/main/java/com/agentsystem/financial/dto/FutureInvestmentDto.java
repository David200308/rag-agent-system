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
        /** currentPrice x quantity, in {@code currency}; the full leveraged notional value, null if price unavailable. */
        BigDecimal currentValue,
        /** Actual capital deployed (margin), in {@code currency} — used for portfolio totals, not the leveraged notional. */
        BigDecimal margin,
        /** Price at which this position would be liquidated, in {@code currency}; null if unavailable/not applicable. */
        BigDecimal liquidationPrice,
        /** Cumulative funding paid/received since the position was opened, in {@code currency}; null if unavailable/not applicable. */
        BigDecimal fundingSinceOpen,
        /** Converted from {@code margin}, not the leveraged notional — this is what feeds portfolio totals/% of total. */
        BigDecimal convertedInvestAmount,
        /** margin + PNL converted to defaultCurrency; null if price/position unavailable. */
        BigDecimal convertedCurrentValue,
        String     convertedCurrency,
        Double     pnlPercent,
        /** "MANUAL" for user-entered Security/CEX rows, "HYPERLIQUID" for live-fetched DEX positions. */
        String     source,
        /** Only set for HYPERLIQUID rows — the id of the tracked-address row this position was expanded from. */
        String     sourceConnectionId,
        /** Only set for HYPERLIQUID rows on a builder-deployed perp dex (HIP-3, e.g. equities perps); null on the default dex. */
        String     hyperliquidDex,
        Instant    createdAt,
        Instant    updatedAt
) {}
