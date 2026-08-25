package com.agentsystem.financial.service;

import java.math.BigDecimal;
import java.util.List;

public interface HyperliquidPositionService {

    /** One open perp position on a Hyperliquid account. */
    record Position(
            String     coin,
            String     side,        // LONG | SHORT
            BigDecimal size,        // absolute size (contracts of `coin`)
            BigDecimal entryPrice,
            BigDecimal leverage,
            BigDecimal unrealizedPnl,
            BigDecimal markPrice,
            /** Actual USD margin backing this position — the real capital at risk, as opposed to
             *  the leveraged notional value (entryPrice x size). Null if unavailable. */
            BigDecimal margin,
            BigDecimal liquidationPrice,
            /** Cumulative funding paid/received since the position was opened (USD, negative = paid). */
            BigDecimal fundingSinceOpen,
            /** Return on equity as a fraction (e.g. -0.374 = -37.4%), computed by Hyperliquid against the
             *  position's margin at entry (entryPrice x size / leverage) — NOT the live {@code margin}
             *  field above, which fluctuates independently. Null if unavailable. */
            BigDecimal returnOnEquity,
            /** Perp dex this position lives on: "" for Hyperliquid's default dex, or a builder-deployed
             *  dex name (e.g. "xyz" for equities/commodities perps) for HIP-3 markets. */
            String     dex
    ) {}

    /** Fetches all open positions for a wallet address. Returns an empty list on any failure. */
    List<Position> fetchPositions(String address);
}
