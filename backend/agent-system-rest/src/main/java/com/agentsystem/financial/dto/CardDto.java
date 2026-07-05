package com.agentsystem.financial.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CardDto(
        String       id,
        String       ownerUuid,
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
