package com.agentsystem.travel.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record TravelRecordDto(
        String id,
        String ownerUuid,
        String title,
        String startDate,
        String endDate,
        List<Map<String, Object>> stops,
        List<Map<String, Object>> expenses,
        String notes,
        boolean allowChat,
        Instant createdAt,
        Instant updatedAt
) {}
