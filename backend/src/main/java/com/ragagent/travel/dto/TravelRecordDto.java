package com.ragagent.travel.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record TravelRecordDto(
        String id,
        String ownerEmail,
        String title,
        String startDate,
        String endDate,
        List<Map<String, Object>> stops,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {}
