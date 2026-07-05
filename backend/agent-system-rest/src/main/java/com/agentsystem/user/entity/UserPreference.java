package com.agentsystem.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "user_preferences",
       uniqueConstraints = @UniqueConstraint(columnNames = "user_uuid"))
@Getter
@Setter
@NoArgsConstructor
public class UserPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_uuid", nullable = false, length = 36)
    private String userUuid;

    @Column(nullable = false, length = 64)
    private String timezone = "UTC";

    /** Display name of the user's selected model; null means use the system default. */
    @Column(name = "selected_model", length = 100)
    private String selectedModel;

    /** Preferred display currency for the financial dashboard. Defaults to USD. */
    @Column(name = "default_currency", length = 10)
    private String defaultCurrency = "USD";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public UserPreference(String userUuid, String timezone) {
        this.userUuid = userUuid;
        this.timezone = timezone;
    }
}
