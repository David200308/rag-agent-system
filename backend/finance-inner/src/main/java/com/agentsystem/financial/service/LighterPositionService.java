package com.agentsystem.financial.service;

import java.math.BigDecimal;
import java.util.List;

public interface LighterPositionService {

    /** One open perp position on a Lighter (zklighter.elliot.ai) account. */
    record Position(
            String     coin,
            String     side,        // LONG | SHORT
            BigDecimal size,        // absolute size, in units of `coin`
            BigDecimal entryPrice,
            BigDecimal leverage,
            BigDecimal unrealizedPnl,
            BigDecimal markPrice,
            /** Fixed entry-time margin (entryPrice x size x initial_margin_fraction). Lighter's own
             *  live `allocated_margin` field was inconsistent/mostly zero across sampled live positions
             *  (isolated and cross), so it is deliberately NOT used here — this derived figure mirrors
             *  the entry-margin basis already validated for Hyperliquid/Jupiter instead. */
            BigDecimal margin,
            BigDecimal liquidationPrice,
            /** Cumulative funding since open (USD), taken as-is from Lighter's total_funding_paid_out —
             *  sign convention inferred from a single live sample (negative = paid), not exchange-confirmed. */
            BigDecimal fundingSinceOpen
    ) {}

    /** Fetches all open positions for an L1 (EVM) deposit address. Returns an empty list on any failure. */
    List<Position> fetchPositions(String l1Address);
}
