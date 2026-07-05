package com.agentsystem.knowledge.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "knowledge_sources")
@Getter
@Setter
@NoArgsConstructor
public class KnowledgeSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The "source" metadata value stored in Weaviate — used as the delete key. */
    @Column(nullable = false, unique = true, length = 512)
    private String source;

    /** Human-friendly label (filename or URL title). */
    @Column(length = 512)
    private String label;

    @Column(length = 128)
    private String category;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    /** Uuid of the user who ingested this source. Null when auth is disabled. */
    @Column(name = "owner_uuid", length = 36)
    private String ownerUuid;

    /** Org slug when in team mode; null = personal. */
    @Column(name = "org_id", length = 100)
    private String orgId;

    /** Approval status: PENDING | APPROVED | REJECTED. Always APPROVED in personal mode. */
    @Column(nullable = false, length = 20)
    private String status = "APPROVED";

    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt = Instant.now();

    @OneToMany(mappedBy = "knowledgeSource", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<KnowledgeSourceShare> shares = new ArrayList<>();

    public KnowledgeSource(String source, String label, String category, int chunkCount, String ownerUuid) {
        this(source, label, category, chunkCount, ownerUuid, null);
    }

    public KnowledgeSource(String source, String label, String category, int chunkCount,
                           String ownerUuid, String orgId) {
        this.source     = source;
        this.label      = label;
        this.category   = category;
        this.chunkCount = chunkCount;
        this.ownerUuid  = ownerUuid;
        this.orgId      = orgId;
        this.ingestedAt = Instant.now();
    }

    /** Convenience: collect all shared-target uuids. */
    public List<String> sharedUuids() {
        return shares.stream().map(KnowledgeSourceShare::getSharedUuid).toList();
    }
}
