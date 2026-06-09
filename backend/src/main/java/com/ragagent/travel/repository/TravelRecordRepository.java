package com.ragagent.travel.repository;

import com.ragagent.travel.entity.TravelRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TravelRecordRepository extends JpaRepository<TravelRecord, String> {
    List<TravelRecord> findByOwnerEmailOrderByStartDateDesc(String ownerEmail);
}
