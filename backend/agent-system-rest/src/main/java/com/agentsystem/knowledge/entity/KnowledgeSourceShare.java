package com.agentsystem.knowledge.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "knowledge_source_shares")
@Getter
@Setter
@NoArgsConstructor
public class KnowledgeSourceShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    private KnowledgeSource knowledgeSource;

    @Column(name = "shared_uuid", nullable = false, length = 36)
    private String sharedUuid;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public KnowledgeSourceShare(KnowledgeSource knowledgeSource, String sharedUuid) {
        this.knowledgeSource = knowledgeSource;
        this.sharedUuid      = sharedUuid;
    }
}
