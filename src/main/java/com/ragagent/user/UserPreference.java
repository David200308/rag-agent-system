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

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public UserPreference(String email, String timezone) {
        this.email    = email;
        this.timezone = timezone;
    }
}
