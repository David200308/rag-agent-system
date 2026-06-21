package com.ragagent.financial.repository;

import com.ragagent.financial.entity.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CardRepository extends JpaRepository<Card, String> {
    List<Card> findByOwnerEmailOrderByCreatedAtDesc(String ownerEmail);
}
