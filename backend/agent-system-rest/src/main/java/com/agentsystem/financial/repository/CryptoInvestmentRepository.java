package com.agentsystem.financial.repository;

import com.agentsystem.financial.entity.CryptoInvestment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CryptoInvestmentRepository extends JpaRepository<CryptoInvestment, String> {
    List<CryptoInvestment> findByOwnerEmailOrderByCreatedAtDesc(String ownerEmail);
}
