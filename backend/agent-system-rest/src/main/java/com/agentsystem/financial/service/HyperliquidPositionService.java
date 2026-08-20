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
            BigDecimal markPrice
    ) {}

    /** Fetches all open positions for a wallet address. Returns an empty list on any failure. */
    List<Position> fetchPositions(String address);
}
