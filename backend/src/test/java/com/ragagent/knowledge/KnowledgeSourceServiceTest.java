package com.ragagent.knowledge;

import com.ragagent.knowledge.entity.KnowledgeSource;
import com.ragagent.knowledge.repository.KnowledgeSourceRepository;
import com.ragagent.rag.DocumentIngestionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KnowledgeSourceServiceTest {

    @Mock KnowledgeSourceRepository repo;
    @Mock DocumentIngestionService  ingestionService;

    @InjectMocks KnowledgeSourceService service;

    // ── upsert ────────────────────────────────────────────────────────────────

    @Test
    void upsert_newSource_createsNewRecord() {
        when(repo.findBySource("doc.pdf")).thenReturn(Optional.empty());
        ArgumentCaptor<KnowledgeSource> captor = ArgumentCaptor.forClass(KnowledgeSource.class);

        service.upsert("doc.pdf", "My Doc", "ai", 42, "owner@test.com");

        verify(repo).save(captor.capture());
        KnowledgeSource saved = captor.getValue();
        assertThat(saved.getSource()).isEqualTo("doc.pdf");
        assertThat(saved.getLabel()).isEqualTo("My Doc");
        assertThat(saved.getChunkCount()).isEqualTo(42);
        assertThat(saved.getOwnerEmail()).isEqualTo("owner@test.com");
    }

    @Test
    void upsert_existingSource_updatesLabelAndCategory() {
        KnowledgeSource existing = new KnowledgeSource("doc.pdf", "Old Label", "old", 10, "owner@test.com");
        when(repo.findBySource("doc.pdf")).thenReturn(Optional.of(existing));

        service.upsert("doc.pdf", "New Label", "new", 20, "owner@test.com");

        verify(repo).save(existing);
        assertThat(existing.getLabel()).isEqualTo("New Label");
        assertThat(existing.getCategory()).isEqualTo("new");
        assertThat(existing.getChunkCount()).isEqualTo(20);
    }

    @Test
    void upsert_existingSourceWithNullOwner_setsOwnerFromCaller() {
        KnowledgeSource existing = new KnowledgeSource("doc.pdf", "Label", "cat", 5, null);
        when(repo.findBySource("doc.pdf")).thenReturn(Optional.of(existing));

        service.upsert("doc.pdf", "Label", "cat", 5, "new-owner@test.com");

        assertThat(existing.getOwnerEmail()).isEqualTo("new-owner@test.com");
    }

    @Test
    void upsert_existingSourceWithOwner_preservesOriginalOwner() {
        KnowledgeSource existing = new KnowledgeSource("doc.pdf", "Label", "cat", 5, "original@test.com");
        when(repo.findBySource("doc.pdf")).thenReturn(Optional.of(existing));

        service.upsert("doc.pdf", "Label", "cat", 5, "new-owner@test.com");

        assertThat(existing.getOwnerEmail()).isEqualTo("original@test.com");
    }

    // ── listAccessible ────────────────────────────────────────────────────────

    @Test
    void listAccessible_nullEmail_returnsAllSources() {
        KnowledgeSource ks = new KnowledgeSource("doc.pdf", "Doc", "cat", 5, "owner@test.com");
        when(repo.findAllByOrderByIngestedAtDesc()).thenReturn(List.of(ks));

        List<KnowledgeSource> result = service.listAccessible(null);

        assertThat(result).containsExactly(ks);
        verify(repo).findAllByOrderByIngestedAtDesc();
        verify(repo, never()).findAccessibleByEmail(any());
    }

    @Test
    void listAccessible_withEmail_returnsAccessibleSources() {
        KnowledgeSource ks = new KnowledgeSource("doc.pdf", "Doc", "cat", 5, "user@test.com");
        when(repo.findAccessibleByEmail("user@test.com")).thenReturn(List.of(ks));

        List<KnowledgeSource> result = service.listAccessible("user@test.com");

        assertThat(result).containsExactly(ks);
        verify(repo, never()).findAllByOrderByIngestedAtDesc();
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_ownerCanDelete() {
        KnowledgeSource ks = new KnowledgeSource("doc.pdf", "Doc", "cat", 5, "owner@test.com");
        when(repo.findBySource("doc.pdf")).thenReturn(Optional.of(ks));

        service.delete("doc.pdf", "owner@test.com");

        verify(ingestionService).deleteBySource("doc.pdf");
        verify(repo).deleteBySource("doc.pdf");
    }

    @Test
    void delete_nonOwnerThrowsSecurityException() {
        KnowledgeSource ks = new KnowledgeSource("doc.pdf", "Doc", "cat", 5, "owner@test.com");
        when(repo.findBySource("doc.pdf")).thenReturn(Optional.of(ks));

        assertThatThrownBy(() -> service.delete("doc.pdf", "intruder@test.com"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Only the owner");

        verifyNoInteractions(ingestionService);
    }

    @Test
    void delete_sourceNotFound_doesNothing() {
        when(repo.findBySource("missing.pdf")).thenReturn(Optional.empty());

        service.delete("missing.pdf", "owner@test.com");

        verifyNoInteractions(ingestionService);
        verify(repo, never()).deleteBySource(any());
    }

    // ── updateMetadata ────────────────────────────────────────────────────────

    @Test
    void updateMetadata_ownerCanUpdateLabel() {
        KnowledgeSource ks = new KnowledgeSource("doc.pdf", "Old", "cat", 5, "owner@test.com");
        when(repo.findBySource("doc.pdf")).thenReturn(Optional.of(ks));
        when(repo.save(ks)).thenReturn(ks);

        KnowledgeSource result = service.updateMetadata("doc.pdf", "New Label", null, "owner@test.com");

        assertThat(result.getLabel()).isEqualTo("New Label");
        assertThat(result.getCategory()).isEqualTo("cat");
    }

    @Test
    void updateMetadata_nonOwnerThrowsSecurityException() {
        KnowledgeSource ks = new KnowledgeSource("doc.pdf", "Label", "cat", 5, "owner@test.com");
        when(repo.findBySource("doc.pdf")).thenReturn(Optional.of(ks));

        assertThatThrownBy(() ->
                service.updateMetadata("doc.pdf", "New", null, "other@test.com"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void updateMetadata_sourceNotFound_throwsIllegalArgument() {
        when(repo.findBySource("ghost.pdf")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.updateMetadata("ghost.pdf", "Label", "cat", "owner@test.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── updateSharing ─────────────────────────────────────────────────────────

    @Test
    void updateSharing_ownerCanShareWithMultipleEmails() {
        KnowledgeSource ks = new KnowledgeSource("doc.pdf", "Doc", "cat", 5, "owner@test.com");
        when(repo.findBySource("doc.pdf")).thenReturn(Optional.of(ks));
        when(repo.save(ks)).thenReturn(ks);

        service.updateSharing("doc.pdf", List.of("a@test.com", "b@test.com"), "owner@test.com");

        assertThat(ks.getShares()).hasSize(2);
        assertThat(ks.sharedEmails()).containsExactlyInAnyOrder("a@test.com", "b@test.com");
    }

    @Test
    void updateSharing_nonOwnerThrowsSecurityException() {
        KnowledgeSource ks = new KnowledgeSource("doc.pdf", "Doc", "cat", 5, "owner@test.com");
        when(repo.findBySource("doc.pdf")).thenReturn(Optional.of(ks));

        assertThatThrownBy(() ->
                service.updateSharing("doc.pdf", List.of("x@test.com"), "other@test.com"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void updateSharing_deduplicatesEmails() {
        KnowledgeSource ks = new KnowledgeSource("doc.pdf", "Doc", "cat", 5, "owner@test.com");
        when(repo.findBySource("doc.pdf")).thenReturn(Optional.of(ks));
        when(repo.save(ks)).thenReturn(ks);

        service.updateSharing("doc.pdf", List.of("a@test.com", "a@test.com"), "owner@test.com");

        assertThat(ks.getShares()).hasSize(1);
    }
}
