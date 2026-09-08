package com.agentsystem.financial.service;

import java.math.BigDecimal;
import java.util.List;

public interface JupiterPerpsPositionService {

    /** One open perp position on a Jupiter Perpetuals (Solana) account. */
    record Position(
            String     coin,
            String     side,        // LONG | SHORT
            BigDecimal size,        // absolute size, in units of `coin` (derived from USD notional / entryPrice)
            BigDecimal entryPrice,
            BigDecimal leverage,
            BigDecimal unrealizedPnl,
            BigDecimal markPrice,
            /** Fixed entry-time collateral (USD) — verified live to NOT absorb PnL, unlike Hyperliquid's
             *  isolated margin, so no PnL back-out is needed to use this as the "invested" basis. */
            BigDecimal margin,
            BigDecimal liquidationPrice,
            /** Cumulative borrow fee cost since open (USD, negative = paid). */
            BigDecimal fundingSinceOpen,
            /** Already a percentage (e.g. 3.87 = 3.87%), from Jupiter's own pnlChangePctAfterFees —
             *  unlike Hyperliquid's fractional returnOnEquity. */
            BigDecimal returnOnEquityPercent
    ) {}

    /** Fetches all open positions for a Solana wallet address. Returns an empty list on any failure. */
    List<Position> fetchPositions(String walletAddress);
}
