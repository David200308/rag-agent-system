package com.agentsystem.financial.repository;

import com.agentsystem.financial.entity.FutureInvestment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FutureInvestmentRepository extends JpaRepository<FutureInvestment, String> {
    List<FutureInvestment> findByOwnerUuidOrderByCreatedAtDesc(String ownerUuid);
}
