package com.agentsystem.financial.service;

import java.time.Instant;
import java.util.Map;

public interface ExchangeRateService {

    Map<String, Double> getRates();

    /** Convert {@code amount} from one currency to another through USD as the base. */
    double convert(double amount, String from, String to);

    Instant getLastFetched();
}
