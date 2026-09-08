package com.agentsystem.travel.repository;

import java.time.Instant;

/** Closed JPA projection excluding {@code expensesJson} so the list query neither fetches nor deserializes it. */
public interface TravelRecordSummaryProjection {
    String getId();
    String getOwnerUuid();
    String getTitle();
    String getStartDate();
    String getEndDate();
    String getStopsJson();
    String getNotes();
    boolean isAllowChat();
    Instant getCreatedAt();
    Instant getUpdatedAt();
}
