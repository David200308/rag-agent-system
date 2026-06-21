package com.ragagent.financial.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "salary_usage_records")
@Getter
@Setter
@NoArgsConstructor
public class SalaryUsageRecord {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "owner_email", length = 255)
    private String ownerEmail;

    @Column(nullable = false)
    private int year;

    @Column(nullable = false)
    private int month;

    @Column(nullable = false, length = 100)
    private String region;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal salary = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal bonus = BigDecimal.ZERO;

    @Column(name = "retirement_saving_employee", nullable = false, precision = 19, scale = 2)
    private BigDecimal retirementSavingEmployee = BigDecimal.ZERO;

    @Column(name = "retirement_saving_employer", nullable = false, precision = 19, scale = 2)
    private BigDecimal retirementSavingEmployer = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal tax = BigDecimal.ZERO;

    @Column(name = "house_rent", nullable = false, precision = 19, scale = 2)
    private BigDecimal houseRent = BigDecimal.ZERO;

    @Column(name = "living_expense", nullable = false, precision = 19, scale = 2)
    private BigDecimal livingExpense = BigDecimal.ZERO;

    @Column(name = "other_expense", nullable = false, precision = 19, scale = 2)
    private BigDecimal otherExpense = BigDecimal.ZERO;

    @Column(name = "total_expense", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalExpense = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
