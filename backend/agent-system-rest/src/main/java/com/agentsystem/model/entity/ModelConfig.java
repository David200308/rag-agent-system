package com.agentsystem.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "model_configs")
@Getter
@Setter
@NoArgsConstructor
public class ModelConfig {

    /** User-facing display name — also serves as the primary key / selectable ID. */
    @Id
    @Column(name = "display_name", length = 100)
    private String displayName;

    /** Provider platform: openai | anthropic | openrouter | local */
    @Column(nullable = false, length = 20)
    private String platform;

    /** Actual model identifier passed to the provider API (e.g. "gpt-4o", "claude-opus-4-7"). */
    @Column(name = "model_id", nullable = false, length = 200)
    private String modelId;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public ModelConfig(String displayName, String platform, String modelId) {
        this.displayName = displayName;
        this.platform    = platform;
        this.modelId     = modelId;
    }
}
