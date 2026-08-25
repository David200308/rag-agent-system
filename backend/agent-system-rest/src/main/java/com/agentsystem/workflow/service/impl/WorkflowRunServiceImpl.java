package com.agentsystem.workflow.service.impl;

import com.agentsystem.workflow.service.WorkflowRunService;
import com.agentsystem.workflow.service.WorkflowScheduleClient;
import com.agentsystem.workflow.service.WorkflowService;

import com.agentsystem.config.ChatModelFactory;
import com.agentsystem.config.LlmProperties;
import com.agentsystem.connector.service.GoogleDocsService;
import com.agentsystem.connector.service.GoogleSheetsService;
import com.agentsystem.connector.service.GoogleSlidesService;
import com.agentsystem.connector.service.TelegramService;
import com.agentsystem.model.service.ModelConfigService;
import com.agentsystem.notification.NotificationClient;
import com.agentsystem.sandbox.service.SandboxService;
import com.agentsystem.org.OrgContext;
import com.agentsystem.skill.service.SkillService;
import com.agentsystem.user.entity.User;
import com.agentsystem.user.service.UserAccountService;
import com.agentsystem.webfetch.service.WebFetchService;
import com.agentsystem.workflow.entity.Workflow;
import com.agentsystem.workflow.entity.WorkflowAgent;
import com.agentsystem.workflow.entity.WorkflowEdge;
import com.agentsystem.workflow.entity.WorkflowRun;
import com.agentsystem.workflow.entity.WorkflowRunLog;
import com.agentsystem.workflow.repository.WorkflowAgentRepository;
import com.agentsystem.workflow.repository.WorkflowEdgeRepository;
import com.agentsystem.workflow.repository.WorkflowRunLogRepository;
import com.agentsystem.workflow.repository.WorkflowRunRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Executes workflow runs in two modes:
 *
 * ORCHESTRATOR — one main agent delegates tasks to named sub-agents.
 *   - Main agent emits: <delegate to="agent-name">task</delegate>
 *   - Sub-agents run their own ReAct loop, results fed back to main agent.
 *
 * TEAM — peer agents run either in PARALLEL (all at once, results merged)
 *         or SEQUENTIAL (each agent's output feeds the next).
 *
 * Each agent execution is a ReAct loop: LLM → parse tool/delegate call
 * → execute in Docker sandbox → feed result back → repeat.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowRunServiceImpl implements WorkflowRunService {

    private static final int MAX_REACT_ITERATIONS = 12;
    private static final Pattern TOOL_PATTERN =
            Pattern.compile("<use_tool name=\"(\\w+)\">(.*?)</use_tool>", Pattern.DOTALL);
    private static final Pattern DELEGATE_PATTERN =
            Pattern.compile("<delegate to=\"([^\"]+)\">(.*?)</delegate>", Pattern.DOTALL);
    private static final Pattern HTTP_URL_PATTERN =
            Pattern.compile("https?://[^\\s\"'\\\\]+");

    private final WorkflowRunRepository    runRepo;
    private final WorkflowRunLogRepository logRepo;
    private final WorkflowAgentRepository  agentRepo;
    private final WorkflowEdgeRepository   edgeRepo;
    private final WorkflowService          workflowService;
    private final SandboxService           sandboxService;
    private final WebFetchService          webFetchService;
    private final SkillService             skillService;
    private final ChatClient               chatClient;
    private final ChatModelFactory         chatModelFactory;
    private final ModelConfigService       modelConfigService;
    private final LlmProperties            llmProperties;
    private final NotificationClient       notificationClient;
    private final WorkflowScheduleClient   workflowScheduleClient;
    private final GoogleDocsService        googleDocsService;
    private final GoogleSheetsService      googleSheetsService;
    private final GoogleSlidesService      googleSlidesService;
    private final TelegramService          telegramService;
    private final UserAccountService       userAccountService;

    private static final java.util.Set<String> CONNECTOR_TOOL_NAMES = java.util.Set.of(
            "GOOGLE_DOCS_WRITE", "GOOGLE_DOCS_READ",
            "GOOGLE_SHEETS_WRITE",
            "GOOGLE_SLIDES_WRITE",
            "TELEGRAM_SEND"
    );

    /** Active SSE emitters keyed by runId. */
    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /** Runs that requested an email notification on completion. */
    private final ConcurrentHashMap<String, Boolean> emailNotifyRuns = new ConcurrentHashMap<>();

    /** In-flight run futures keyed by runId, so a stop request can interrupt the worker thread. */
    private final ConcurrentHashMap<String, Future<?>> runningFutures = new ConcurrentHashMap<>();

    /** RunIds that received a stop request while still executing — checked when the run settles. */
    private final ConcurrentHashMap<String, Boolean> cancelledRuns = new ConcurrentHashMap<>();

    private final ExecutorService asyncPool = Executors.newVirtualThreadPerTaskExecutor();

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Creates a WorkflowRun record and starts async execution. Returns the runId.
     */
    @Override
    public String startRun(String workflowId, String userInput, String ownerEmail, boolean emailNotify) {
        return startRunByUuid(workflowId, userInput, resolveUuid(ownerEmail), emailNotify);
    }

    @Override
    public String startRunByUuid(String workflowId, String userInput, String ownerUuid, boolean emailNotify) {
        WorkflowRun run = new WorkflowRun(UUID.randomUUID().toString(), workflowId, ownerUuid, userInput);
        run.setStatus(WorkflowRun.RunStatus.PENDING);
        run.setWorkflowVersion(workflowService.latestVersionNumber(workflowId).orElse(null));
        runRepo.save(run);
        if (emailNotify && ownerUuid != null) {
            emailNotifyRuns.put(run.getId(), true);
        }
        log.info("[WorkflowRun] Starting run {} for workflow {} (emailNotify={})", run.getId(), workflowId, emailNotify);

        Future<?> future = asyncPool.submit(() -> executeRun(run));
        runningFutures.put(run.getId(), future);
        return run.getId();
    }

    /**
     * Opens an SSE stream for a run. The caller must hold the connection open.
     * Historical logs are replayed first, then live events streamed.
     */
    @Override
    public SseEmitter streamLogs(String runId) {
        SseEmitter emitter = new SseEmitter(10 * 60 * 1000L); // 10-min timeout
        emitters.put(runId, emitter);
        emitter.onCompletion(() -> emitters.remove(runId));
        emitter.onTimeout(()    -> emitters.remove(runId));
        emitter.onError(e -> emitters.remove(runId));

        // Replay already-persisted logs (handle late connections)
        asyncPool.submit(() -> {
            try {
                List<WorkflowRunLog> historical = logRepo.findByRunIdOrderByCreatedAt(runId);
                for (WorkflowRunLog l : historical) {
                    pushLogEvent(emitter, l);
                }
                // If run already finished, close emitter
                runRepo.findById(runId).ifPresent(run -> {
                    if (run.getStatus() == WorkflowRun.RunStatus.DONE
                            || run.getStatus() == WorkflowRun.RunStatus.FAILED) {
                        try {
                            emitter.send(SseEmitter.event().name("done")
                                    .data(Map.of("status", run.getStatus().name(),
                                                 "output", run.getFinalOutput() != null ? run.getFinalOutput() : "")));
                            emitter.complete();
                        } catch (IOException ignored) {}
                    }
                });
            } catch (Exception e) {
                log.warn("[WorkflowRun] SSE replay error: {}", e.getMessage());
            }
        });

        return emitter;
    }

    @Override
    public List<WorkflowRunLog> getLogs(String runId) {
        return logRepo.findByRunIdOrderByCreatedAt(runId);
    }

    @Override
    public Page<WorkflowRun> getRuns(String workflowId, int page, int size) {
        return runRepo.findByWorkflowIdOrderByStartedAtDesc(
                workflowId, PageRequest.of(page, Math.min(size, 50)));
    }

    @Transactional
    @Override
    public void cancelRun(String runId, String callerUuid) {
        WorkflowRun run = runRepo.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Run not found: " + runId));
        if (callerUuid != null && run.getOwnerUuid() != null && !callerUuid.equals(run.getOwnerUuid())) {
            throw new SecurityException("Only the owner can stop this run.");
        }
        if (run.getStatus() != WorkflowRun.RunStatus.RUNNING && run.getStatus() != WorkflowRun.RunStatus.PENDING) {
            return; // already terminal — nothing to do
        }

        cancelledRuns.put(runId, true);
        log.info("[WorkflowRun] Cancel requested for run {}", runId);

        Future<?> future = runningFutures.get(runId);
        if (future != null) future.cancel(true);

        // Killing the sandbox now breaks any in-flight `sandboxService.exec` call, which lets
        // the executeRun worker thread unwind through its catch/finally blocks promptly.
        sandboxService.destroySandbox(run.getSandboxContainer());

        run.setStatus(WorkflowRun.RunStatus.CANCELLED);
        run.setFinishedAt(Instant.now());
        runRepo.save(run);
        emit(runId, null, null, WorkflowRunLog.LogType.SYSTEM, "Run cancelled by user.");
        pushDoneEvent(runId, "CANCELLED", run.getFinalOutput());
    }

    @Transactional
    @Override
    public void deleteRun(String runId, String callerUuid) {
        WorkflowRun run = runRepo.findById(runId).orElse(null);
        if (run == null) return;
        if (callerUuid != null && run.getOwnerUuid() != null && !callerUuid.equals(run.getOwnerUuid())) {
            throw new SecurityException("Only the owner can delete this run.");
        }
        if (run.getStatus() == WorkflowRun.RunStatus.RUNNING || run.getStatus() == WorkflowRun.RunStatus.PENDING) {
            cancelRun(runId, callerUuid);
        }
        logRepo.deleteByRunId(runId);
        runRepo.deleteById(runId);
        log.info("[WorkflowRun] Deleted run {} by {}", runId, callerUuid);
    }

    // ── Execution engine ──────────────────────────────────────────────────────

    private void executeRun(WorkflowRun run) {
        Workflow workflow = workflowService.findById(run.getWorkflowId())
                .orElseThrow(() -> new RuntimeException("Workflow not found: " + run.getWorkflowId()));
        List<WorkflowAgent> agents = agentRepo.findByWorkflowIdOrderByOrderIndex(run.getWorkflowId());

        run.setStatus(WorkflowRun.RunStatus.RUNNING);
        runRepo.save(run);

        emit(run.getId(), null, null, WorkflowRunLog.LogType.SYSTEM,
                "Workflow started [pattern=" + workflow.getAgentPattern()
                + ", agents=" + agents.size() + "]");

        boolean needsNetwork = agents.stream().anyMatch(a -> {
            List<String> tools = workflowService.parseTools(a);
            return tools.stream().anyMatch(t -> t.equalsIgnoreCase("CURL") || t.equalsIgnoreCase("NET"));
        });

        // containerId must be declared before the try so the finally block can always call destroySandbox.
        // It is assigned inside the try — if sandbox creation throws, it stays null and destroySandbox is a no-op.
        String containerId = null;
        try {
            emit(run.getId(), null, null, WorkflowRunLog.LogType.SYSTEM,
                    "Initializing sandbox" + (needsNetwork ? " (network enabled)…" : "…"));

            java.util.function.Consumer<String> sandboxLog = msg ->
                    emit(run.getId(), null, null, WorkflowRunLog.LogType.SYSTEM, "[Sandbox] " + msg);

            containerId = needsNetwork
                    ? sandboxService.createSandboxWithNetwork(run.getId(), sandboxLog)
                    : sandboxService.createSandbox(run.getId(), sandboxLog);
            run.setSandboxContainer(containerId);
            runRepo.save(run);

            emit(run.getId(), null, null, WorkflowRunLog.LogType.SYSTEM, "Sandbox ready.");

            // Resolve ChatClient: workflow model → configured DEFAULT_MODEL → raw provider
            String modelName = workflow.getSelectedModel();
            if (modelName == null) {
                String dm = llmProperties.getDefaultModel();
                if (dm != null && !dm.isBlank()) modelName = dm;
            }
            ChatClient effectiveClient = modelName != null
                    ? modelConfigService.findByDisplayName(modelName)
                        .filter(com.agentsystem.model.entity.ModelConfig::isEnabled)
                        .map(chatModelFactory::buildChatClient)
                        .orElse(chatClient)
                    : chatClient;

            String output = switch (workflow.getAgentPattern()) {
                case ORCHESTRATOR -> executeOrchestrator(run, agents, containerId, effectiveClient);
                case TEAM         -> executeTeam(run, workflow, agents, containerId, effectiveClient);
                case GRAPH        -> executeGraph(run, agents, edgeRepo.findByWorkflowId(run.getWorkflowId()),
                                                   containerId, effectiveClient);
            };

            // A stop request may have arrived after the last cancellable checkpoint (e.g. the
            // final LLM call was already in flight) — honor it instead of reporting success.
            if (cancelledRuns.remove(run.getId()) != null) {
                run.setStatus(WorkflowRun.RunStatus.CANCELLED);
                run.setFinishedAt(Instant.now());
                runRepo.save(run);
                emit(run.getId(), null, null, WorkflowRunLog.LogType.SYSTEM, "Run cancelled by user.");
                pushDoneEvent(run.getId(), "CANCELLED", output);
            } else {
                run.setFinalOutput(output);
                run.setStatus(WorkflowRun.RunStatus.DONE);
                run.setFinishedAt(Instant.now());
                runRepo.save(run);

                emit(run.getId(), null, null, WorkflowRunLog.LogType.SYSTEM, "Workflow completed.");
                pushDoneEvent(run.getId(), "DONE", output);
            }

        } catch (SandboxService.SandboxStartupException ex) {
            log.error("[WorkflowRun] Run {} — sandbox failed to start: {}", run.getId(), ex.getMessage());
            finalizeFailure(run, "Sandbox failed to start — " + ex.getMessage()
                    + ". Ensure the Docker socket is mounted and ragagent/sandbox:latest exists on the host.",
                    ex.getMessage());

        } catch (SandboxService.SandboxKilledException ex) {
            log.warn("[WorkflowRun] Run {} killed by sandbox watchdog: {}", run.getId(), ex.getMessage());
            finalizeFailure(run, "Task killed: " + ex.getMessage(), ex.getMessage());

        } catch (SandboxService.SandboxQueueFullException | SandboxService.SandboxResourceException ex) {
            log.warn("[WorkflowRun] Run {} rejected — sandbox capacity: {}", run.getId(), ex.getMessage());
            finalizeFailure(run, "Sandbox capacity limit reached: " + ex.getMessage() + ". Please retry in a moment.",
                    ex.getMessage());

        } catch (Exception ex) {
            log.error("[WorkflowRun] Run {} failed: {}", run.getId(), ex.getMessage(), ex);
            finalizeFailure(run, ex.getMessage(), ex.getMessage());

        } finally {
            runningFutures.remove(run.getId());
            sandboxService.destroySandbox(containerId);
            maybeSendCompletionEmail(run, workflow.getName());
        }
    }

    /**
     * Persists the terminal state for a failed run — unless a stop request raced it, in which
     * case CANCELLED wins over FAILED. The status is re-asserted here (not just left to
     * {@link #cancelRun}'s own save) because this thread's in-memory `run` object is a separate
     * instance that already overwrote status to RUNNING at the top of executeRun and would
     * otherwise clobber cancelRun's write when it saves below.
     */
    private void finalizeFailure(WorkflowRun run, String logDetail, String outputSummary) {
        if (cancelledRuns.remove(run.getId()) != null) {
            run.setStatus(WorkflowRun.RunStatus.CANCELLED);
            run.setFinishedAt(Instant.now());
            runRepo.save(run);
            emit(run.getId(), null, null, WorkflowRunLog.LogType.SYSTEM, "Run cancelled by user.");
            pushDoneEvent(run.getId(), "CANCELLED", outputSummary);
            return;
        }
        run.setStatus(WorkflowRun.RunStatus.FAILED);
        run.setFinishedAt(Instant.now());
        runRepo.save(run);
        emit(run.getId(), null, null, WorkflowRunLog.LogType.ERROR, logDetail);
        pushDoneEvent(run.getId(), "FAILED", outputSummary);
    }

    private void maybeSendCompletionEmail(WorkflowRun run, String workflowName) {
        if (!emailNotifyRuns.remove(run.getId(), true)) return;
        String to = resolveEmail(run.getOwnerUuid());
        if (to == null) return;
        asyncPool.submit(() -> notificationClient.sendWorkflowComplete(
                to, workflowName, run.getStatus().name(), run.getFinalOutput()));
    }

    // ── Orchestrator pattern ──────────────────────────────────────────────────

    private String executeOrchestrator(WorkflowRun run, List<WorkflowAgent> agents,
                                       String containerId, ChatClient effectiveClient) {
        WorkflowAgent mainAgent = agents.stream()
                .filter(a -> a.getRole() == WorkflowAgent.AgentRole.MAIN)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No MAIN agent defined in workflow"));

        List<WorkflowAgent> subAgents = agents.stream()
                .filter(a -> a.getRole() == WorkflowAgent.AgentRole.SUB)
                .toList();

        String systemPrompt = buildOrchestratorPrompt(mainAgent, subAgents);
        return runReActLoop(run, mainAgent, systemPrompt, run.getUserInput(), containerId, subAgents, effectiveClient);
    }

    private String buildOrchestratorPrompt(WorkflowAgent main, List<WorkflowAgent> subs) {
        StringBuilder sb = new StringBuilder();
        sb.append(main.getSystemPrompt() != null ? main.getSystemPrompt() : "You are a helpful orchestrator agent.");

        List<String> skillIds = workflowService.parseSkillIds(main);
        sb.append(buildSkillSection(skillIds));

        List<String> tools = workflowService.parseTools(main);
        if (!tools.isEmpty()) {
            sb.append(buildToolSection(tools));
        }

        sb.append(buildOutputSchemaSection(main.getOutputSchemaJson()));

        if (!subs.isEmpty()) {
            sb.append("""

                    ## Sub-Agents
                    You can delegate tasks to these specialized sub-agents using:
                    <delegate to="agent-name">
                    task description
                    </delegate>

                    Available sub-agents:
                    """);
            for (WorkflowAgent sub : subs) {
                String preview = sub.getSystemPrompt() != null && sub.getSystemPrompt().length() > 120
                        ? sub.getSystemPrompt().substring(0, 120) + "…"
                        : sub.getSystemPrompt();
                sb.append("- **").append(sub.getName()).append("**: ").append(preview).append("\n");
            }
            sb.append("\nAfter receiving results from sub-agents, synthesize them into a final answer.\n");
        }

        return sb.toString();
    }

    // ── Team pattern ──────────────────────────────────────────────────────────

    private String executeTeam(WorkflowRun run, Workflow workflow,
                               List<WorkflowAgent> agents, String containerId, ChatClient effectiveClient) {
        List<WorkflowAgent> peers = agents.stream()
                .filter(a -> a.getRole() == WorkflowAgent.AgentRole.PEER)
                .toList();
        if (peers.isEmpty()) throw new RuntimeException("No PEER agents defined for team workflow");

        return switch (workflow.getTeamExecMode()) {
            case PARALLEL   -> executeTeamParallel(run, peers, containerId, effectiveClient);
            case SEQUENTIAL -> executeTeamSequential(run, peers, containerId, effectiveClient);
            case null       -> executeTeamParallel(run, peers, containerId, effectiveClient);
        };
    }

    private String executeTeamParallel(WorkflowRun run, List<WorkflowAgent> peers,
                                       String containerId, ChatClient effectiveClient) {
        emit(run.getId(), null, null, WorkflowRunLog.LogType.SYSTEM,
                "Running " + peers.size() + " agents in parallel…");

        List<CompletableFuture<String>> futures = peers.stream()
                .map(agent -> CompletableFuture.supplyAsync(
                        () -> {
                            String sysPrompt = (agent.getSystemPrompt() != null ? agent.getSystemPrompt() : "")
                                    + buildSkillSection(workflowService.parseSkillIds(agent))
                                    + buildToolSection(workflowService.parseTools(agent))
                                    + buildOutputSchemaSection(agent.getOutputSchemaJson());
                            return runReActLoop(run, agent, sysPrompt, run.getUserInput(), containerId, List.of(), effectiveClient);
                        }, asyncPool))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        StringBuilder merged = new StringBuilder();
        for (int i = 0; i < peers.size(); i++) {
            merged.append("## ").append(peers.get(i).getName()).append("\n\n");
            merged.append(futures.get(i).join()).append("\n\n");
        }
        return merged.toString().strip();
    }

    private String executeTeamSequential(WorkflowRun run, List<WorkflowAgent> peers,
                                         String containerId, ChatClient effectiveClient) {
        emit(run.getId(), null, null, WorkflowRunLog.LogType.SYSTEM,
                "Running " + peers.size() + " agents sequentially…");

        String currentInput = run.getUserInput();
        for (int i = 0; i < peers.size(); i++) {
            WorkflowAgent agent = peers.get(i);
            String sysPrompt = (agent.getSystemPrompt() != null ? agent.getSystemPrompt() : "")
                    + buildSkillSection(workflowService.parseSkillIds(agent))
                    + buildToolSection(workflowService.parseTools(agent))
                    + buildOutputSchemaSection(agent.getOutputSchemaJson());
            currentInput = runReActLoop(run, agent, sysPrompt, currentInput, containerId, List.of(), effectiveClient);
            if (i < peers.size() - 1) {
                sandboxService.recycleSandbox(containerId);  // clean between sequential steps
            }
        }
        return currentInput;
    }

    // ── Graph pattern ────────────────────────────────────────────────────────

    /**
     * Walks an explicit node graph built by the user in the workflow builder:
     * AGENT nodes run a ReAct loop and hand their output to the single outgoing edge's
     * target; CONDITION nodes ask the LLM to pick one of their outgoing edges' branch
     * labels; END nodes terminate the run.
     */
    private String executeGraph(WorkflowRun run, List<WorkflowAgent> agents, List<WorkflowEdge> edges,
                                String containerId, ChatClient effectiveClient) {
        if (agents.isEmpty()) throw new RuntimeException("No nodes defined for graph workflow");

        Map<Long, WorkflowAgent> nodesById = agents.stream()
                .collect(Collectors.toMap(WorkflowAgent::getId, a -> a));

        Set<Long> hasIncoming = edges.stream().map(WorkflowEdge::getTargetNodeId).collect(Collectors.toSet());
        List<WorkflowAgent> startCandidates = agents.stream()
                .filter(a -> !hasIncoming.contains(a.getId()))
                .sorted(Comparator.comparingInt(WorkflowAgent::getOrderIndex))
                .toList();
        if (startCandidates.isEmpty()) {
            throw new RuntimeException("Graph workflow has no start node — every node has an incoming edge (check for a cycle)");
        }

        WorkflowAgent current = startCandidates.get(0);
        String currentInput = run.getUserInput();
        int maxSteps = agents.size() * 3 + 10;

        for (int step = 0; step < maxSteps; step++) {
            final WorkflowAgent node = current;
            switch (node.getNodeKind()) {
                case END -> {
                    emit(run.getId(), node.getId(), node.getName(), WorkflowRunLog.LogType.SYSTEM,
                            "Reached END node [" + node.getName() + "]");
                    return currentInput;
                }
                case CONDITION -> {
                    List<WorkflowEdge> outgoing = edges.stream()
                            .filter(e -> e.getSourceNodeId().equals(node.getId()))
                            .toList();
                    if (outgoing.isEmpty()) {
                        throw new RuntimeException("Condition node [" + node.getName() + "] has no outgoing edges");
                    }
                    String branch = classifyBranch(run, node, currentInput, outgoing, effectiveClient);
                    WorkflowEdge chosen = outgoing.stream()
                            .filter(e -> branch.equalsIgnoreCase(nullToEmpty(e.getBranchLabel())))
                            .findFirst()
                            .orElse(outgoing.get(0));
                    emit(run.getId(), node.getId(), node.getName(), WorkflowRunLog.LogType.DELEGATION,
                            "→ Branch [" + branch + "] selected");
                    current = requireNode(nodesById, chosen.getTargetNodeId());
                }
                case AGENT -> {
                    String sysPrompt = buildGraphAgentPrompt(node);
                    currentInput = runReActLoop(run, node, sysPrompt, currentInput, containerId, List.of(), effectiveClient);
                    List<WorkflowEdge> outgoing = edges.stream()
                            .filter(e -> e.getSourceNodeId().equals(node.getId()))
                            .toList();
                    if (outgoing.isEmpty()) {
                        return currentInput; // no outgoing edge — implicit end of graph
                    }
                    sandboxService.recycleSandbox(containerId);
                    current = requireNode(nodesById, outgoing.get(0).getTargetNodeId());
                }
            }
        }
        throw new RuntimeException("Graph workflow exceeded " + maxSteps + " steps — check for a cycle");
    }

    private WorkflowAgent requireNode(Map<Long, WorkflowAgent> nodesById, Long id) {
        WorkflowAgent node = nodesById.get(id);
        if (node == null) throw new RuntimeException("Edge targets unknown node id " + id);
        return node;
    }

    private String buildGraphAgentPrompt(WorkflowAgent agent) {
        return (agent.getSystemPrompt() != null ? agent.getSystemPrompt() : "")
                + buildSkillSection(workflowService.parseSkillIds(agent))
                + buildToolSection(workflowService.parseTools(agent))
                + buildOutputSchemaSection(agent.getOutputSchemaJson());
    }

    /** Asks the LLM to pick one of a condition node's outgoing branch labels for the given input. */
    private String classifyBranch(WorkflowRun run, WorkflowAgent conditionNode, String input,
                                  List<WorkflowEdge> outgoing, ChatClient effectiveClient) {
        List<String> labels = outgoing.stream().map(e -> nullToEmpty(e.getBranchLabel())).toList();

        String prompt = "You are a routing classifier for a workflow condition node.\n"
                + "Condition: " + (conditionNode.getConditionExpr() != null && !conditionNode.getConditionExpr().isBlank()
                        ? conditionNode.getConditionExpr() : "(no description provided — use your best judgement)") + "\n\n"
                + "Respond with EXACTLY one of these labels and nothing else: " + labels + "\n\n"
                + "Input to classify:\n" + input;

        String response = effectiveClient.prompt().user(prompt).call().content().strip();
        emit(run.getId(), conditionNode.getId(), conditionNode.getName(), WorkflowRunLog.LogType.LLM_RESPONSE, response);

        for (String label : labels) {
            if (label.equalsIgnoreCase(response) || response.toLowerCase().contains(label.toLowerCase())) {
                return label;
            }
        }
        return labels.get(0); // no confident match — fall back to the first outgoing edge
    }

    private String nullToEmpty(String s) { return s != null ? s : ""; }

    // ── ReAct loop ────────────────────────────────────────────────────────────

    /**
     * Core ReAct loop for a single agent:
     *  1. Call LLM with accumulated messages
     *  2. Parse response for tool calls or delegation
     *  3. Execute and feed result back
     *  4. Repeat until no more tool/delegate calls or max iterations
     */
    private String runReActLoop(WorkflowRun run, WorkflowAgent agent,
                                String systemPrompt, String userInput,
                                String containerId, List<WorkflowAgent> subAgents,
                                ChatClient effectiveClient) {

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", userInput));

        emit(run.getId(), agent.getId(), agent.getName(), WorkflowRunLog.LogType.SYSTEM,
                "Agent [" + agent.getName() + "] starting on: " + truncate(userInput, 200));

        for (int iter = 0; iter < MAX_REACT_ITERATIONS; iter++) {
            String context = buildContext(messages);
            String llmResponse = effectiveClient.prompt()
                    .system(systemPrompt)
                    .user(context)
                    .call()
                    .content();

            emit(run.getId(), agent.getId(), agent.getName(),
                    WorkflowRunLog.LogType.LLM_RESPONSE, llmResponse);

            // Check for delegation first (orchestrator only)
            Matcher delMatcher = DELEGATE_PATTERN.matcher(llmResponse);
            if (!subAgents.isEmpty() && delMatcher.find()) {
                String targetName = delMatcher.group(1).strip();
                String delegatedTask = delMatcher.group(2).strip();

                emit(run.getId(), agent.getId(), agent.getName(), WorkflowRunLog.LogType.DELEGATION,
                        "→ Delegating to [" + targetName + "]: " + truncate(delegatedTask, 200));

                WorkflowAgent subAgent = subAgents.stream()
                        .filter(a -> a.getName().equalsIgnoreCase(targetName))
                        .findFirst()
                        .orElse(null);

                String subResult;
                if (subAgent != null) {
                    String subPrompt = (subAgent.getSystemPrompt() != null ? subAgent.getSystemPrompt() : "")
                            + buildSkillSection(workflowService.parseSkillIds(subAgent))
                            + buildToolSection(workflowService.parseTools(subAgent))
                            + buildOutputSchemaSection(subAgent.getOutputSchemaJson());
                    subResult = runReActLoop(run, subAgent, subPrompt, delegatedTask, containerId, List.of(), effectiveClient);
                    sandboxService.recycleSandbox(containerId);  // clean workspace between delegations
                } else {
                    subResult = "[Sub-agent '" + targetName + "' not found]";
                }

                messages.add(Map.of("role", "assistant", "content", llmResponse));
                messages.add(Map.of("role", "user", "content",
                        "Result from [" + targetName + "]:\n\n" + subResult));
                continue;
            }

            // Check for tool calls
            Matcher toolMatcher = TOOL_PATTERN.matcher(llmResponse);
            if (toolMatcher.find()) {
                String toolName = toolMatcher.group(1);
                String command  = toolMatcher.group(2).strip();

                emit(run.getId(), agent.getId(), agent.getName(), WorkflowRunLog.LogType.TOOL_CALL,
                        "[" + toolName + "] " + command);

                String toolResult;

                if ("SCHEDULE".equalsIgnoreCase(toolName)) {
                    toolResult = dispatchScheduleTool(run.getOwnerUuid(), command);
                } else if (CONNECTOR_TOOL_NAMES.contains(toolName.toUpperCase())) {
                    toolResult = dispatchConnectorTool(toolName.toUpperCase(), command, run.getOwnerUuid(), run.getOrgId());
                } else {
                    String blocked = validateNetworkCommand(command, run.getOwnerUuid());
                    if (blocked != null) {
                        emit(run.getId(), agent.getId(), agent.getName(),
                                WorkflowRunLog.LogType.TOOL_RESULT, blocked);
                        messages.add(Map.of("role", "assistant", "content", llmResponse));
                        messages.add(Map.of("role", "user", "content",
                                "Tool result (" + toolName + "):\n" + blocked));
                        continue;
                    }
                    toolResult = sandboxService.exec(containerId, command);
                }

                emit(run.getId(), agent.getId(), agent.getName(), WorkflowRunLog.LogType.TOOL_RESULT,
                        toolResult);

                messages.add(Map.of("role", "assistant", "content", llmResponse));
                messages.add(Map.of("role", "user", "content",
                        "Tool result (" + toolName + "):\n" + toolResult));
                continue;
            }

            // No tool calls and no delegation → final answer, unless a schema demands a retry
            if (agent.getOutputSchemaJson() == null || agent.getOutputSchemaJson().isBlank()) {
                return llmResponse;
            }
            List<String> schemaErrors = validateOutputSchema(agent.getOutputSchemaJson(), llmResponse);
            if (schemaErrors.isEmpty()) {
                return llmResponse;
            }
            emit(run.getId(), agent.getId(), agent.getName(), WorkflowRunLog.LogType.ERROR,
                    "Output failed schema validation: " + String.join("; ", schemaErrors));
            if (iter == MAX_REACT_ITERATIONS - 1) {
                return llmResponse; // out of retries — return best effort, failure already logged
            }
            messages.add(Map.of("role", "assistant", "content", llmResponse));
            messages.add(Map.of("role", "user", "content",
                    "Your previous answer did not match the required output schema:\n"
                    + String.join("\n", schemaErrors)
                    + "\n\nRespond again with ONLY valid JSON matching the schema — no markdown fences, no extra text."));
            continue;
        }

        // Max iterations reached — return last LLM response
        String lastMsg = messages.stream()
                .filter(m -> "assistant".equals(m.get("role")))
                .reduce((first, second) -> second)
                .map(m -> m.get("content"))
                .orElse("(no response)");
        emit(run.getId(), agent.getId(), agent.getName(), WorkflowRunLog.LogType.SYSTEM,
                "Max iterations reached for agent [" + agent.getName() + "]");
        return lastMsg;
    }

    // ── Prompt building ───────────────────────────────────────────────────────

    private String dispatchScheduleTool(String ownerUuid, String json) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(json);
            String action = node.path("action").asText("create");
            return switch (action.toLowerCase()) {
                case "create" -> workflowScheduleClient.createSchedule(
                        ownerUuid,
                        node.path("conversationId").asText(),
                        node.path("message").asText(),
                        node.path("cron").asText("0 9 * * *"),
                        node.path("timezone").asText("UTC"),
                        node.path("topK").asInt(5),
                        node.path("useKnowledgeBase").asBoolean(true),
                        node.path("useWebFetch").asBoolean(false)
                );
                case "list" -> workflowScheduleClient.listSchedules(
                        ownerUuid,
                        node.path("conversationId").asText()
                );
                case "delete" -> workflowScheduleClient.deleteSchedule(
                        ownerUuid,
                        node.path("scheduleId").asText()
                );
                default -> "Unknown SCHEDULE action: " + action + ". Valid actions: create, list, delete";
            };
        } catch (Exception e) {
            return "SCHEDULE tool error: " + e.getMessage();
        }
    }

    private String dispatchConnectorTool(String toolName, String payload, String ownerUuid, String orgId) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(payload.trim());

            return switch (toolName) {
                case "GOOGLE_DOCS_WRITE" -> googleDocsService.createDocument(
                        node.path("title").asText("Untitled"),
                        node.path("content").asText(""),
                        ownerUuid, orgId);

                case "GOOGLE_DOCS_READ" -> {
                    String url = node.isTextual() ? node.asText()
                            : node.path("url").asText(payload.trim());
                    yield googleDocsService.readDocument(url, ownerUuid, orgId);
                }

                case "GOOGLE_SHEETS_WRITE" -> googleSheetsService.createSpreadsheet(
                        node.path("title").asText("Untitled"),
                        node.path("content").asText(""),
                        ownerUuid, orgId);

                case "GOOGLE_SLIDES_WRITE" -> googleSlidesService.createPresentation(
                        node.path("title").asText("Untitled"),
                        node.path("content").asText(""),
                        ownerUuid, orgId);

                case "TELEGRAM_SEND" -> {
                    String msg = node.isTextual() ? node.asText()
                            : node.path("message").asText(payload.trim());
                    yield telegramService.sendMessage(ownerUuid, orgId, msg);
                }

                default -> "Unknown connector tool: " + toolName;
            };
        } catch (IllegalStateException e) {
            return "Connector error: " + e.getMessage();
        } catch (Exception e) {
            return "Connector tool failed (" + toolName + "): " + e.getMessage();
        }
    }

    private String buildToolSection(List<String> tools) {
        if (tools.isEmpty()) return "";

        boolean hasSchedule = tools.stream().anyMatch(t -> "SCHEDULE".equalsIgnoreCase(t));

        // Separate connector tools (CONNECTOR_*) from sandbox tools
        List<String> connectorIds = tools.stream()
                .filter(t -> t.toUpperCase().startsWith("CONNECTOR_"))
                .toList();
        List<String> sandboxTools = tools.stream()
                .filter(t -> !"SCHEDULE".equalsIgnoreCase(t) && !t.toUpperCase().startsWith("CONNECTOR_"))
                .map(String::toLowerCase)
                .toList();

        StringBuilder sb = new StringBuilder("\n\n## Available Tools\n");
        sb.append("When you need to run a command, use this format and then STOP — wait for the result:\n");

        if (!sandboxTools.isEmpty()) {
            sb.append("""
                    <use_tool name="bash">
                    your shell command here
                    </use_tool>

                    Sandbox tools: """);
            sb.append(String.join(", ", sandboxTools)).append("\n");
        }

        if (hasSchedule) {
            sb.append("""

                    To create, list, or delete scheduled messages use the SCHEDULE tool with JSON:
                    <use_tool name="SCHEDULE">
                    {"action":"create","conversationId":"<id>","message":"<text>","cron":"0 9 * * 1-5","timezone":"UTC","topK":5,"useKnowledgeBase":true,"useWebFetch":false}
                    </use_tool>

                    <use_tool name="SCHEDULE">
                    {"action":"list","conversationId":"<id>"}
                    </use_tool>

                    <use_tool name="SCHEDULE">
                    {"action":"delete","scheduleId":"<schedule-id>"}
                    </use_tool>

                    cron format: minute hour day month weekday (e.g. "0 9 * * 1-5" = Mon-Fri 9 AM UTC)
                    """);
        }

        // Connector tool instructions
        if (!connectorIds.isEmpty()) {
            sb.append("\n\n### Connected Service Tools\n");
            sb.append("Use the exact tool names and JSON formats shown below:\n");

            if (connectorIds.contains("CONNECTOR_GOOGLE_DOCS")) {
                sb.append("""

                        Write a new Google Doc:
                        <use_tool name="GOOGLE_DOCS_WRITE">
                        {"title": "My Document", "content": "Full body text here..."}
                        </use_tool>

                        Read an existing Google Doc:
                        <use_tool name="GOOGLE_DOCS_READ">
                        {"url": "https://docs.google.com/document/d/.../edit"}
                        </use_tool>
                        """);
            }
            if (connectorIds.contains("CONNECTOR_GOOGLE_SHEETS")) {
                sb.append("""

                        Write a Google Sheet (rows separated by \\n, columns by tab or comma):
                        <use_tool name="GOOGLE_SHEETS_WRITE">
                        {"title": "My Spreadsheet", "content": "Name\\tAge\\nAlice\\t30\\nBob\\t25"}
                        </use_tool>
                        """);
            }
            if (connectorIds.contains("CONNECTOR_GOOGLE_SLIDES")) {
                sb.append("""

                        Create a Google Slides presentation (slides separated by --- on its own line):
                        <use_tool name="GOOGLE_SLIDES_WRITE">
                        {"title": "My Presentation", "content": "Slide Title\\nSlide body text\\n---\\nSlide 2 Title\\nSlide 2 body"}
                        </use_tool>
                        """);
            }
            if (connectorIds.contains("CONNECTOR_TELEGRAM")) {
                sb.append("""

                        Send a Telegram message to the workflow owner:
                        <use_tool name="TELEGRAM_SEND">
                        {"message": "Your message text here"}
                        </use_tool>
                        """);
            }
        }

        sb.append("\nWhen you have a final answer, respond normally without any XML tags.");
        return sb.toString();
    }

    private String buildOutputSchemaSection(String outputSchemaJson) {
        if (outputSchemaJson == null || outputSchemaJson.isBlank()) return "";
        return "\n\n## Required Output Format\n"
                + "Your final answer (once you are done using tools) MUST be valid JSON matching this schema:\n"
                + outputSchemaJson
                + "\nRespond with ONLY the JSON — no markdown code fences, no explanation text.\n";
    }

    /** Returns human-readable errors, or an empty list if the output satisfies the schema (or the schema itself is unparsable, which is not the agent's fault). */
    private List<String> validateOutputSchema(String schemaJson, String rawOutput) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            JsonNode schema = mapper.readTree(schemaJson);
            JsonNode data;
            try {
                data = OutputSchemaValidator.parseLenient(mapper, rawOutput);
            } catch (Exception e) {
                return List.of("Output is not valid JSON: " + e.getMessage());
            }
            return OutputSchemaValidator.validate(schema, data);
        } catch (Exception e) {
            log.warn("[WorkflowRun] Skipping output schema validation — schema itself is invalid: {}", e.getMessage());
            return List.of();
        }
    }

    private String buildSkillSection(List<String> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("\n\n## Knowledge\n");
        for (String id : skillIds) {
            skillService.getContent(id).ifPresent(content ->
                sb.append(content).append("\n\n"));
        }
        return sb.toString();
    }

    /** Flattens accumulated message pairs into a single user prompt string. */
    private String buildContext(List<Map<String, String>> messages) {
        if (messages.size() == 1) {
            return messages.get(0).get("content");
        }
        StringBuilder sb = new StringBuilder();
        for (Map<String, String> m : messages) {
            sb.append("[").append(m.get("role").toUpperCase()).append("]\n");
            sb.append(m.get("content")).append("\n\n");
        }
        return sb.toString().strip();
    }

    // ── SSE helpers ───────────────────────────────────────────────────────────

    private WorkflowRunLog emit(String runId, Long agentId, String agentName,
                                WorkflowRunLog.LogType type, String content) {
        WorkflowRunLog log = new WorkflowRunLog(runId, agentId, agentName, type, content);
        logRepo.save(log);

        SseEmitter emitter = emitters.get(runId);
        if (emitter != null) {
            try {
                pushLogEvent(emitter, log);
            } catch (Exception e) {
                emitters.remove(runId);
            }
        }
        return log;
    }

    private void pushLogEvent(SseEmitter emitter, WorkflowRunLog log) throws IOException {
        emitter.send(SseEmitter.event()
                .name("log")
                .data(Map.of(
                        "id",        log.getId(),
                        "agentName", log.getAgentName() != null ? log.getAgentName() : "",
                        "logType",   log.getLogType().name(),
                        "content",   log.getContent(),
                        "createdAt", log.getCreatedAt().toString()
                )));
    }

    private void pushDoneEvent(String runId, String status, String output) {
        SseEmitter emitter = emitters.get(runId);
        if (emitter == null) return;
        try {
            emitter.send(SseEmitter.event().name("done")
                    .data(Map.of("status", status,
                                 "output", output != null ? output : "")));
            emitter.complete();
        } catch (Exception e) {
            emitters.remove(runId);
        }
    }

    private String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "…" : String.valueOf(s);
    }

    /**
     * Returns a block message if the command contains curl/wget targeting a domain not in
     * the owner's whitelist. Returns null if the command is allowed.
     */
    private String validateNetworkCommand(String command, String ownerUuid) {
        if (!command.contains("curl") && !command.contains("wget")) {
            return null;
        }
        List<String> urls = new ArrayList<>();
        Matcher m = HTTP_URL_PATTERN.matcher(command);
        while (m.find()) {
            urls.add(m.group().replaceAll("[.,;)\\]]+$", ""));
        }
        if (urls.isEmpty()) {
            return "[Blocked: curl/wget without a recognizable URL is not permitted]";
        }
        OrgContext ownerCtx = new OrgContext(ownerUuid, null, "PERSONAL", null);
        for (String url : urls) {
            if (!webFetchService.isUrlAllowed(url, ownerCtx)) {
                String host = url.replaceAll("^https?://([^/?#]+).*", "$1");
                return "[Blocked: domain '" + host + "' is not in your web-fetch whitelist. "
                        + "Add it in Settings → Web Fetch before using it in a workflow.]";
            }
        }
        return null;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    /** Resolves an email to its user_uuid, or null if no such email is a registered user. */
    private String resolveUuid(String email) {
        if (email == null || email.isBlank()) return null;
        return userAccountService.findByEmail(email).map(User::getUuid).orElse(null);
    }

    /** Resolves a user_uuid to its (Redis-cached) email, or null. */
    private String resolveEmail(String uuid) {
        if (uuid == null || uuid.isBlank()) return null;
        return userAccountService.getEmailByUuid(uuid);
    }
}
