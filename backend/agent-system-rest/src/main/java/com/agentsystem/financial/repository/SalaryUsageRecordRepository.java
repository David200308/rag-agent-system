package com.agentsystem.financial.repository;

import com.agentsystem.financial.entity.SalaryUsageRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SalaryUsageRecordRepository extends JpaRepository<SalaryUsageRecord, String> {
    List<SalaryUsageRecord> findByOwnerUuidOrderByYearDescMonthDesc(String ownerUuid);
}
