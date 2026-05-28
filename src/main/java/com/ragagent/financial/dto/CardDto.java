package com.ragagent.financial.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CardDto(
        String       id,
        String       ownerEmail,
        String       bank,
        String       countryRegion,
        List<String> types,
        String       cardName,
        String       network,
        String       expireDate,
        BigDecimal   creditLimit,
        String       creditLimitCurrency,
        Boolean      sharedCredit,
        Instant      createdAt,
        Instant      updatedAt
) {}
