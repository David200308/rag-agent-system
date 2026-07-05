package com.agentsystem.travel.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "travel_records")
@Getter
@Setter
@NoArgsConstructor
public class TravelRecord {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "owner_uuid", length = 36)
    private String ownerUuid;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "start_date", nullable = false, length = 10)
    private String startDate;

    @Column(name = "end_date", nullable = false, length = 10)
    private String endDate;

    @Column(name = "stops_json", columnDefinition = "TEXT")
    private String stopsJson;

    @Column(name = "expenses_json", columnDefinition = "TEXT")
    private String expensesJson;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
