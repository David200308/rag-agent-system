package com.agentsystem.financial.service;

import java.util.List;
import java.util.Optional;

/**
 * Live futures/perp mark prices fetched directly from each CEX's own public API —
 * NOT the shared spot-crypto (Hyperliquid mids) cache used elsewhere, since a given
 * exchange's futures price and instrument-id format are exchange-specific.
 */
public interface CexFuturesPriceService {

    /** exchange (BINANCE|OKX|KRAKEN) + that exchange's own native instrument id (e.g. "BTCUSDT", "BTC-USDT-SWAP", "PF_XBTUSD"). */
    record ExchangeSymbol(String exchange, String symbol) {}

    Optional<Double> getPrice(String exchange, String symbol);

    boolean isStale();

    void refreshPrices(List<ExchangeSymbol> symbols);
}
