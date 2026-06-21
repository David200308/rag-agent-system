package com.ragagent.knowledge.entity;

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

    @Column(name = "shared_email", nullable = false, length = 255)
    private String sharedEmail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public KnowledgeSourceShare(KnowledgeSource knowledgeSource, String sharedEmail) {
        this.knowledgeSource = knowledgeSource;
        this.sharedEmail     = sharedEmail;
    }
}
