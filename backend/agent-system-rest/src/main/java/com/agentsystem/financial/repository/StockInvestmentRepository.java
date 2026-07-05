package com.agentsystem.financial.repository;

import com.agentsystem.financial.entity.StockInvestment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockInvestmentRepository extends JpaRepository<StockInvestment, String> {
    List<StockInvestment> findByOwnerUuidOrderByCreatedAtDesc(String ownerUuid);
}
