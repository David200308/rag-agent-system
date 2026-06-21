package com.ragagent.financial.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record CashDepositDto(
        String     id,
        String     ownerEmail,
        String     platform,
        String     platformType,
        String     countryRegion,
        String     depositType,
        String     currency,
        BigDecimal amount,
        BigDecimal convertedAmount,
        String     convertedCurrency,
        Instant    createdAt,
        Instant    updatedAt
) {}
