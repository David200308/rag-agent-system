package com.agentsystem.travel.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** List-view projection of a travel record — omits {@code expenses}, fetched separately via {@code GET /{id}/expenses}. */
public record TravelRecordSummaryDto(
        String id,
        String ownerUuid,
        String title,
        String startDate,
        String endDate,
        List<Map<String, Object>> stops,
        String notes,
        boolean allowChat,
        Instant createdAt,
        Instant updatedAt
) {}
