package com.ragagent.financial.repository;

import com.ragagent.financial.entity.SalaryUsageRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SalaryUsageRecordRepository extends JpaRepository<SalaryUsageRecord, String> {
    List<SalaryUsageRecord> findByOwnerEmailOrderByYearDescMonthDesc(String ownerEmail);
}
