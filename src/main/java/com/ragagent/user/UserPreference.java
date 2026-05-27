package com.ragagent.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "user_preferences",
       uniqueConstraints = @UniqueConstraint(columnNames = "email"))
@Getter
@Setter
@NoArgsConstructor
public class UserPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String email;

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

    public UserPreference(String email, String timezone) {
        this.email    = email;
        this.timezone = timezone;
    }
}
