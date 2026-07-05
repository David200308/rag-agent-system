package com.agentsystem.conversation.service;

import com.agentsystem.conversation.service.impl.ConversationServiceImpl;

import com.agentsystem.conversation.entity.Conversation;
import com.agentsystem.conversation.entity.ConversationMessage;
import com.agentsystem.conversation.entity.ConversationShare;
import com.agentsystem.conversation.repository.ConversationMessageRepository;
import com.agentsystem.conversation.repository.ConversationRepository;
import com.agentsystem.conversation.repository.ConversationShareRepository;
import com.agentsystem.org.OrgContext;
import com.agentsystem.schema.AgentRequest;
import com.agentsystem.user.entity.User;
import com.agentsystem.user.entity.UserStatus;
import com.agentsystem.user.service.UserAccountService;
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
    @Mock UserAccountService            userAccountService;

    @InjectMocks ConversationServiceImpl conversationService;

    /** Stubs userAccountService to resolve {@code email} to a matching uuid (same string, for simplicity). */
    private void stubUuidResolution(String email) {
        lenient().when(userAccountService.findByEmail(email))
                .thenReturn(Optional.of(new User(email, email, UserStatus.USER, true)));
    }

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
        stubUuidResolution("user@test.com");
        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        when(conversationRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        String result = conversationService.resolveConversation(null, "user@test.com");

        assertThat(result).isNotBlank();
        verify(conversationRepo).save(captor.capture());
        assertThat(captor.getValue().getUserUuid()).isEqualTo("user@test.com");
    }

    @Test
    void resolveConversation_blankId_createsNewConversation() {
        stubUuidResolution("user@test.com");
        when(conversationRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        String result = conversationService.resolveConversation("  ", "user@test.com");

        assertThat(result).isNotBlank();
        verify(conversationRepo).save(any(Conversation.class));
    }

    @Test
    void resolveConversation_unknownId_createsNewConversation() {
        stubUuidResolution("user@test.com");
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
        when(shareRepo.findByConversationIdAndOwnerUuid("c1", "owner@test.com"))
                .thenReturn(Optional.empty());
        when(shareRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        ConversationShare share = conversationService.createShare(
                "c1", "owner@test.com", 7, "READ_ONLY", "EVERYONE", List.of());

        assertThat(share.getConversationId()).isEqualTo("c1");
        assertThat(share.getOwnerUuid()).isEqualTo("owner@test.com");
        assertThat(share.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void createShare_nonOwnerThrowsSecurityException() {
        Conversation conv = new Conversation("c1", "owner@test.com");
        when(conversationRepo.findById("c1")).thenReturn(Optional.of(conv));

        assertThatThrownBy(() ->
                conversationService.createShare("c1", "other@test.com", null,
                        "READ_ONLY", "EVERYONE", List.of()))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void createShare_nullExpireDays_createsNeverExpiringShare() {
        Conversation conv = new Conversation("c1", "owner@test.com");
        when(conversationRepo.findById("c1")).thenReturn(Optional.of(conv));
        when(shareRepo.findByConversationIdAndOwnerUuid("c1", "owner@test.com"))
                .thenReturn(Optional.empty());
        when(shareRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        ConversationShare share = conversationService.createShare(
                "c1", "owner@test.com", null, "READ_ONLY", "EVERYONE", List.of());

        assertThat(share.getExpiresAt()).isNull();
        assertThat(share.isActive()).isTrue();
    }

    @Test
    void createShare_readOnlyMode_setsShareMode() {
        Conversation conv = new Conversation("c1", "owner@test.com");
        when(conversationRepo.findById("c1")).thenReturn(Optional.of(conv));
        when(shareRepo.findByConversationIdAndOwnerUuid("c1", "owner@test.com"))
                .thenReturn(Optional.empty());
        when(shareRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        ConversationShare share = conversationService.createShare(
                "c1", "owner@test.com", null, "READ_ONLY", "EVERYONE", List.of());

        assertThat(share.getShareMode()).isEqualTo("READ_ONLY");
    }

    @Test
    void createShare_whitelistMode_setsAccessType() {
        Conversation conv = new Conversation("c1", "owner@test.com");
        when(conversationRepo.findById("c1")).thenReturn(Optional.of(conv));
        when(shareRepo.findByConversationIdAndOwnerUuid("c1", "owner@test.com"))
                .thenReturn(Optional.empty());
        when(shareRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        ConversationShare share = conversationService.createShare(
                "c1", "owner@test.com", null, "READ_ONLY", "WHITELIST",
                List.of("a@test.com", "b@test.com"));

        assertThat(share.getAccessType()).isEqualTo("WHITELIST");
    }

    // ── setConversationModel ──────────────────────────────────────────────────

    @Test
    void setConversationModel_ownerSetsModel() {
        Conversation conv = new Conversation("c1", "owner@test.com");
        when(conversationRepo.findById("c1")).thenReturn(Optional.of(conv));
        when(conversationRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        Conversation result = conversationService.setConversationModel("c1", "owner@test.com", "GPT-4o");

        assertThat(result.getSelectedModel()).isEqualTo("GPT-4o");
    }

    @Test
    void setConversationModel_nullDisplayName_clearsModel() {
        Conversation conv = new Conversation("c1", "owner@test.com");
        conv.setSelectedModel("GPT-4o");
        when(conversationRepo.findById("c1")).thenReturn(Optional.of(conv));
        when(conversationRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        Conversation result = conversationService.setConversationModel("c1", "owner@test.com", null);

        assertThat(result.getSelectedModel()).isNull();
    }

    @Test
    void setConversationModel_blankDisplayName_clearsModel() {
        Conversation conv = new Conversation("c1", "owner@test.com");
        conv.setSelectedModel("GPT-4o");
        when(conversationRepo.findById("c1")).thenReturn(Optional.of(conv));
        when(conversationRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        Conversation result = conversationService.setConversationModel("c1", "owner@test.com", "  ");

        assertThat(result.getSelectedModel()).isNull();
    }

    @Test
    void setConversationModel_nonOwnerThrowsSecurityException() {
        Conversation conv = new Conversation("c1", "owner@test.com");
        when(conversationRepo.findById("c1")).thenReturn(Optional.of(conv));

        assertThatThrownBy(() ->
                conversationService.setConversationModel("c1", "other@test.com", "GPT-4o"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void setConversationModel_notFound_throwsIllegalArgument() {
        when(conversationRepo.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                conversationService.setConversationModel("missing", "owner@test.com", "GPT-4o"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── getConversationModel ──────────────────────────────────────────────────

    @Test
    void getConversationModel_returnsStoredModel() {
        Conversation conv = new Conversation("c1", "owner@test.com");
        conv.setSelectedModel("Claude-Sonnet");
        when(conversationRepo.findById("c1")).thenReturn(Optional.of(conv));

        assertThat(conversationService.getConversationModel("c1")).isEqualTo("Claude-Sonnet");
    }

    @Test
    void getConversationModel_noModelSet_returnsNull() {
        Conversation conv = new Conversation("c1", "owner@test.com");
        when(conversationRepo.findById("c1")).thenReturn(Optional.of(conv));

        assertThat(conversationService.getConversationModel("c1")).isNull();
    }

    @Test
    void getConversationModel_notFound_returnsNull() {
        when(conversationRepo.findById("missing")).thenReturn(Optional.empty());

        assertThat(conversationService.getConversationModel("missing")).isNull();
    }

    // ── listConversations ─────────────────────────────────────────────────────

    @Test
    void listConversations_returnsNonArchivedForUser() {
        stubUuidResolution("user@test.com");
        Conversation c = new Conversation("c1", "user@test.com");
        when(conversationRepo.findByUserUuidAndArchivedFalseOrderByUpdatedAtDesc("user@test.com"))
                .thenReturn(List.of(c));

        List<Conversation> result = conversationService.listConversations("user@test.com");

        assertThat(result).containsExactly(c);
    }

    // ── getSharedMessages ─────────────────────────────────────────────────────

    @Test
    void getSharedMessages_activeToken_returnsMessages() {
        ConversationShare share = new ConversationShare("c1", "tok-1", "owner@test.com", null, "READ_ONLY", "EVERYONE");
        ConversationMessage msg = new ConversationMessage("c1", "user", "Hello", null);
        when(shareRepo.findByToken("tok-1")).thenReturn(Optional.of(share));
        when(messageRepo.findByConversationIdOrderByCreatedAtAsc("c1")).thenReturn(List.of(msg));

        List<ConversationMessage> result = conversationService.getSharedMessages("tok-1");

        assertThat(result).containsExactly(msg);
    }

    @Test
    void getSharedMessages_expiredToken_throwsIllegalArgument() {
        when(shareRepo.findByToken("expired")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversationService.getSharedMessages("expired"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Share link not found or expired");
    }

    // ── getShareByToken ───────────────────────────────────────────────────────

    @Test
    void getShareByToken_activeShare_returnsShare() {
        ConversationShare share = new ConversationShare("c1", "tok-1", "owner@test.com", null, "READ_ONLY", "EVERYONE");
        when(shareRepo.findByToken("tok-1")).thenReturn(Optional.of(share));

        ConversationShare result = conversationService.getShareByToken("tok-1");

        assertThat(result).isEqualTo(share);
    }

    @Test
    void getShareByToken_expired_throwsIllegalArgument() {
        when(shareRepo.findByToken("expired")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversationService.getShareByToken("expired"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Share link not found or expired");
    }

    // ── validateShareAccess ───────────────────────────────────────────────────

    @Test
    void validateShareAccess_everyoneMode_anyEmailAllowed() {
        ConversationShare share = new ConversationShare("c1", "tok-1", "owner@test.com", null, "READ_ONLY", "EVERYONE");
        when(shareRepo.findByToken("tok-1")).thenReturn(Optional.of(share));

        ConversationShare result = conversationService.validateShareAccess("tok-1", "anyone@test.com");

        assertThat(result).isEqualTo(share);
    }

    @Test
    void validateShareAccess_whitelistMode_allowedEmail() {
        ConversationShare share = new ConversationShare("c1", "tok-1", "owner@test.com", null, "READ_ONLY", "WHITELIST");
        share.getWhitelist().add("allowed@test.com");
        when(shareRepo.findByToken("tok-1")).thenReturn(Optional.of(share));

        ConversationShare result = conversationService.validateShareAccess("tok-1", "allowed@test.com");

        assertThat(result).isEqualTo(share);
    }

    @Test
    void validateShareAccess_whitelistMode_notAllowed_throwsSecurityException() {
        ConversationShare share = new ConversationShare("c1", "tok-1", "owner@test.com", null, "READ_ONLY", "WHITELIST");
        share.getWhitelist().add("allowed@test.com");
        when(shareRepo.findByToken("tok-1")).thenReturn(Optional.of(share));

        assertThatThrownBy(() -> conversationService.validateShareAccess("tok-1", "notallowed@test.com"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("not on the whitelist");
    }

    @Test
    void validateShareAccess_whitelistMode_nullEmail_throwsSecurityException() {
        ConversationShare share = new ConversationShare("c1", "tok-1", "owner@test.com", null, "READ_ONLY", "WHITELIST");
        share.getWhitelist().add("allowed@test.com");
        when(shareRepo.findByToken("tok-1")).thenReturn(Optional.of(share));

        assertThatThrownBy(() -> conversationService.validateShareAccess("tok-1", null))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Authentication required");
    }

    // ── getShare ──────────────────────────────────────────────────────────────

    @Test
    void getShare_found_returnsShare() {
        ConversationShare share = new ConversationShare("c1", "tok-1", "owner@test.com", null, "READ_ONLY", "EVERYONE");
        when(shareRepo.findByConversationIdAndOwnerUuid("c1", "owner@test.com"))
                .thenReturn(Optional.of(share));

        ConversationShare result = conversationService.getShare("c1", "owner@test.com");

        assertThat(result).isEqualTo(share);
    }

    @Test
    void getShare_notFound_throwsIllegalArgument() {
        when(shareRepo.findByConversationIdAndOwnerUuid("c1", "owner@test.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversationService.getShare("c1", "owner@test.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No active share");
    }

    // ── revokeShare ───────────────────────────────────────────────────────────

    @Test
    void revokeShare_ownerCanRevoke() {
        ConversationShare share = new ConversationShare("c1", "tok-1", "owner@test.com", null, "READ_ONLY", "EVERYONE");
        Conversation conv = new Conversation("c1", "owner@test.com");
        when(shareRepo.findByConversationIdAndOwnerUuid("c1", "owner@test.com"))
                .thenReturn(Optional.of(share));
        when(conversationRepo.findById("c1")).thenReturn(Optional.of(conv));

        conversationService.revokeShare("c1", "owner@test.com");

        verify(shareRepo).delete(share);
    }

    @Test
    void revokeShare_nonOwner_throwsSecurityException() {
        ConversationShare share = new ConversationShare("c1", "tok-1", "owner@test.com", null, "READ_ONLY", "EVERYONE");
        Conversation conv = new Conversation("c1", "owner@test.com");
        when(shareRepo.findByConversationIdAndOwnerUuid("c1", "intruder@test.com"))
                .thenReturn(Optional.of(share));
        when(conversationRepo.findById("c1")).thenReturn(Optional.of(conv));

        assertThatThrownBy(() -> conversationService.revokeShare("c1", "intruder@test.com"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Only the owner");
    }

    // ── resolveConversation with OrgContext ───────────────────────────────────

    @Test
    void resolveConversation_teamContext_setsOrgId() {
        OrgContext ctx = new OrgContext("user@test.com", "user@test.com", "TEAM", "skyproton");
        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        when(conversationRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        String result = conversationService.resolveConversation(null, ctx);

        assertThat(result).isNotBlank();
        verify(conversationRepo).save(captor.capture());
        assertThat(captor.getValue().getOrgId()).isEqualTo("skyproton");
    }

    // ── listConversations(OrgContext) ─────────────────────────────────────────

    @Test
    void listConversations_teamMode_queriesTeamRepo() {
        OrgContext ctx = new OrgContext("user@test.com", "user@test.com", "TEAM", "skyproton");
        Conversation c = new Conversation("c1", "user@test.com");
        when(conversationRepo.findByUserUuidAndOrgIdAndArchivedFalseOrderByUpdatedAtDesc(
                "user@test.com", "skyproton")).thenReturn(List.of(c));

        List<Conversation> result = conversationService.listConversations(ctx);

        assertThat(result).containsExactly(c);
        verify(conversationRepo).findByUserUuidAndOrgIdAndArchivedFalseOrderByUpdatedAtDesc(
                "user@test.com", "skyproton");
    }

    @Test
    void listConversations_personalMode_queriesPersonalRepo() {
        OrgContext ctx = new OrgContext("user@test.com", "user@test.com", "PERSONAL", null);
        Conversation c = new Conversation("c1", "user@test.com");
        when(conversationRepo.findByUserUuidAndOrgIdIsNullAndArchivedFalseOrderByUpdatedAtDesc(
                "user@test.com")).thenReturn(List.of(c));

        List<Conversation> result = conversationService.listConversations(ctx);

        assertThat(result).containsExactly(c);
        verify(conversationRepo).findByUserUuidAndOrgIdIsNullAndArchivedFalseOrderByUpdatedAtDesc(
                "user@test.com");
    }

    // ── listArchivedConversations(OrgContext) ─────────────────────────────────

    @Test
    void listArchivedConversations_teamMode_queriesTeamRepo() {
        OrgContext ctx = new OrgContext("user@test.com", "user@test.com", "TEAM", "skyproton");
        Conversation c = new Conversation("c1", "user@test.com");
        when(conversationRepo.findByUserUuidAndOrgIdAndArchivedTrueOrderByUpdatedAtDesc(
                "user@test.com", "skyproton")).thenReturn(List.of(c));

        List<Conversation> result = conversationService.listArchivedConversations(ctx);

        assertThat(result).containsExactly(c);
        verify(conversationRepo).findByUserUuidAndOrgIdAndArchivedTrueOrderByUpdatedAtDesc(
                "user@test.com", "skyproton");
    }

    @Test
    void listArchivedConversations_emailOverload_returnsResults() {
        stubUuidResolution("user@test.com");
        Conversation c = new Conversation("c1", "user@test.com");
        // ConversationService.listArchivedConversations(String) calls
        // conversationRepo.findByUserUuidAndArchivedTrueOrderByUpdatedAtDesc (the default interface method)
        // On a mock, we stub the default method directly
        when(conversationRepo.findByUserUuidAndArchivedTrueOrderByUpdatedAtDesc("user@test.com"))
                .thenReturn(List.of(c));

        List<Conversation> result = conversationService.listArchivedConversations("user@test.com");

        assertThat(result).containsExactly(c);
    }
}
