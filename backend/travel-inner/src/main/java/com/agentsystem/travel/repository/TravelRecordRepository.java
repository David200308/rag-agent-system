package com.agentsystem.travel.repository;

import com.agentsystem.travel.entity.TravelRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TravelRecordRepository extends JpaRepository<TravelRecord, String> {

    /** Excludes {@code expensesJson} — used by the list endpoint, which never needs expenses. */
    List<TravelRecordSummaryProjection> findSummaryByOwnerUuidOrderByStartDateDesc(String ownerUuid);

    List<TravelRecord> findByOwnerUuidAndAllowChatTrueOrderByStartDateDesc(String ownerUuid);
}
