package com.ragagent.financial.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record SalaryUsageRecordDto(
        String     id,
        String     ownerEmail,
        int        year,
        int        month,
        String     region,
        String     currency,
        BigDecimal salary,
        BigDecimal retirementSavingEmployee,
        BigDecimal retirementSavingEmployer,
        BigDecimal tax,
        BigDecimal houseRent,
        BigDecimal livingExpense,
        BigDecimal otherExpense,
        BigDecimal totalExpense,
        Instant    createdAt,
        Instant    updatedAt
) {}
