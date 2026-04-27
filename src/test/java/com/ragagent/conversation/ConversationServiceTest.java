package com.ragagent.conversation;

import com.ragagent.conversation.entity.Conversation;
import com.ragagent.conversation.entity.ConversationMessage;
import com.ragagent.conversation.entity.ConversationShare;
import com.ragagent.conversation.repository.ConversationMessageRepository;
import com.ragagent.conversation.repository.ConversationRepository;
import com.ragagent.conversation.repository.ConversationShareRepository;
import com.ragagent.schema.AgentRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock ConversationRepository        conversationRepo;
    @Mock ConversationMessageRepository messageRepo;
    @Mock ConversationShareRepository   shareRepo;

    @InjectMocks ConversationService conversationService;

    // ── resolveConversation ───────────────────────────────────────────────────

    @Test
    void resolveConversation_existingId_returnsSameId() {
        when(conversationRepo.existsById("existing-id")).thenReturn(true);

        String result = conversationService.resolveConversation("existing-id", "user@test.com");

        assertThat(result).isEqualTo("existing-id");
        verify(conversationRepo, never()).save(any());
    }

    @Test
    void resolveConversation_nullId_createsNewConversation() {
        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        when(conversationRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        String result = conversationService.resolveConversation(null, "user@test.com");

        assertThat(result).isNotBlank();
        verify(conversationRepo).save(captor.capture());
        assertThat(captor.getValue().getUserEmail()).isEqualTo("user@test.com");
    }

    @Test
    void resolveConversation_blankId_createsNewConversation() {
        when(conversationRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        String result = conversationService.resolveConversation("  ", "user@test.com");

        assertThat(result).isNotBlank();
        verify(conversationRepo).save(any(Conversation.class));
    }

    @Test
    void resolveConversation_unknownId_createsNewConversation() {
        when(conversationRepo.existsById("ghost-id")).thenReturn(false);
        when(conversationRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        String result = conversationService.resolveConversation("ghost-id", "user@test.com");

        assertThat(result).isNotEqualTo("ghost-id");
    }

    // ── saveUserMessage / saveAssistantMessage ────────────────────────────────

    @Test
    void saveUserMessage_savesMessageWithUserRole() {
        ArgumentCaptor<ConversationMessage> captor = ArgumentCaptor.forClass(ConversationMessage.class);

        conversationService.saveUserMessage("conv-1", "Hello?");

        verify(messageRepo).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo("user");
        assertThat(captor.getValue().getContent()).isEqualTo("Hello?");
    }

    @Test
    void saveAssistantMessage_savesWithRunId() {
        ArgumentCaptor<ConversationMessage> captor = ArgumentCaptor.forClass(ConversationMessage.class);

        conversationService.saveAssistantMessage("conv-1", "Hi there!", "run-42");

        verify(messageRepo).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo("assistant");
        assertThat(captor.getValue().getRunId()).isEqualTo("run-42");
    }

    // ── loadHistory ───────────────────────────────────────────────────────────

    @Test
    void loadHistory_mapsMessagesToConversationTurns() {
        ConversationMessage m1 = new ConversationMessage("c1", "user", "What is RAG?", null);
        ConversationMessage m2 = new ConversationMessage("c1", "assistant", "RAG is...", "r1");
        when(messageRepo.findByConversationIdOrderByCreatedAtAsc("c1"))
                .thenReturn(List.of(m1, m2));

        List<AgentRequest.ConversationTurn> turns = conversationService.loadHistory("c1");

        assertThat(turns).hasSize(2);
        assertThat(turns.get(0).role()).isEqualTo("user");
        assertThat(turns.get(1).role()).isEqualTo("assistant");
    }

    // ── setArchived ───────────────────────────────────────────────────────────

    @Test
    void setArchived_ownerCanArchive() {
        Conversation conv = new Conversation("c1", "owner@test.com");
        when(conversationRepo.findById("c1")).thenReturn(Optional.of(conv));
        when(conversationRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        Conversation result = conversationService.setArchived("c1", "owner@test.com", true);

        assertThat(result.isArchived()).isTrue();
    }

    @Test
    void setArchived_nonOwnerThrowsSecurityException() {
        Conversation conv = new Conversation("c1", "owner@test.com");
        when(conversationRepo.findById("c1")).thenReturn(Optional.of(conv));

        assertThatThrownBy(() ->
                conversationService.setArchived("c1", "other@test.com", true))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Only the owner");
    }

    @Test
    void setArchived_unknownConversation_throwsIllegalArgument() {
        when(conversationRepo.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                conversationService.setArchived("missing", "user@test.com", true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── deleteConversation ────────────────────────────────────────────────────

    @Test
    void deleteConversation_ownerDeletesSuccessfully() {
        Conversation conv = new Conversation("c1", "owner@test.com");
        when(conversationRepo.findById("c1")).thenReturn(Optional.of(conv));

        conversationService.deleteConversation("c1", "owner@test.com");

        verify(shareRepo).deleteByConversationId("c1");
        verify(messageRepo).deleteByConversationId("c1");
        verify(conversationRepo).deleteById("c1");
    }

    @Test
    void deleteConversation_nonOwnerThrowsSecurityException() {
        Conversation conv = new Conversation("c1", "owner@test.com");
        when(conversationRepo.findById("c1")).thenReturn(Optional.of(conv));

        assertThatThrownBy(() ->
                conversationService.deleteConversation("c1", "intruder@test.com"))
                .isInstanceOf(SecurityException.class);
    }

    // ── createShare ───────────────────────────────────────────────────────────

    @Test
    void createShare_ownerCreatesShareWithExpiry() {
        Conversation conv = new Conversation("c1", "owner@test.com");
        when(conversationRepo.findById("c1")).thenReturn(Optional.of(conv));
        when(shareRepo.findByConversationIdAndOwnerEmail("c1", "owner@test.com"))
                .thenReturn(Optional.empty());
        when(shareRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        ConversationShare share = conversationService.createShare("c1", "owner@test.com", 7);

        assertThat(share.getConversationId()).isEqualTo("c1");
        assertThat(share.getOwnerEmail()).isEqualTo("owner@test.com");
        assertThat(share.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void createShare_nonOwnerThrowsSecurityException() {
        Conversation conv = new Conversation("c1", "owner@test.com");
        when(conversationRepo.findById("c1")).thenReturn(Optional.of(conv));

        assertThatThrownBy(() ->
                conversationService.createShare("c1", "other@test.com", null))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void createShare_nullExpireDays_createsNeverExpiringShare() {
        Conversation conv = new Conversation("c1", "owner@test.com");
        when(conversationRepo.findById("c1")).thenReturn(Optional.of(conv));
        when(shareRepo.findByConversationIdAndOwnerEmail("c1", "owner@test.com"))
                .thenReturn(Optional.empty());
        when(shareRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        ConversationShare share = conversationService.createShare("c1", "owner@test.com", null);

        assertThat(share.getExpiresAt()).isNull();
        assertThat(share.isActive()).isTrue();
    }

    // ── listConversations ─────────────────────────────────────────────────────

    @Test
    void listConversations_returnsNonArchivedForUser() {
        Conversation c = new Conversation("c1", "user@test.com");
        when(conversationRepo.findByUserEmailAndArchivedFalseOrderByUpdatedAtDesc("user@test.com"))
                .thenReturn(List.of(c));

        List<Conversation> result = conversationService.listConversations("user@test.com");

        assertThat(result).containsExactly(c);
    }
}
