package com.agentsystem.financial.repository;

import com.agentsystem.financial.entity.CashDeposit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CashDepositRepository extends JpaRepository<CashDeposit, String> {
    List<CashDeposit> findByOwnerUuidOrderByCreatedAtDesc(String ownerUuid);
}
