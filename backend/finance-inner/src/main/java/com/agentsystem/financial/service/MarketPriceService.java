package com.agentsystem.financial.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MarketPriceService {

    Optional<Double> getStockPrice(String symbol);

    Optional<String> getStockCurrency(String symbol);

    Optional<String> getStockLogo(String symbol);

    /** Resolves a symbol's company display name, fetching it live (and caching) if not already known. */
    Optional<String> lookupStockName(String symbol);

    Optional<Double> getCryptoPrice(String symbol);

    Optional<String> getCryptoLogo(String symbol);

    Instant getStockLastFetched();

    Instant getCryptoLastFetched();

    boolean isStockStale();

    boolean isCryptoStale();

    void refreshStockPrices(List<String> symbols);

    void refreshCryptoPrices(List<String> symbols);
}
