package com.ragagent.workflow;

import com.ragagent.sandbox.SandboxService;
import com.ragagent.webfetch.WebFetchService;
import com.ragagent.workflow.entity.Workflow;
import com.ragagent.workflow.entity.WorkflowAgent;
import com.ragagent.workflow.entity.WorkflowRun;
import com.ragagent.workflow.entity.WorkflowRunLog;
import com.ragagent.workflow.repository.WorkflowAgentRepository;
import com.ragagent.workflow.repository.WorkflowRunLogRepository;
import com.ragagent.workflow.repository.WorkflowRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
public class WorkflowRunService {

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
    private final WorkflowService          workflowService;
    private final SandboxService           sandboxService;
    private final WebFetchService          webFetchService;
    private final ChatClient               chatClient;

    /** Active SSE emitters keyed by runId. */
    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    private final ExecutorService asyncPool = Executors.newVirtualThreadPerTaskExecutor();

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Creates a WorkflowRun record and starts async execution. Returns the runId.
     */
    public String startRun(String workflowId, String userInput, String ownerEmail) {
        WorkflowRun run = new WorkflowRun(UUID.randomUUID().toString(), workflowId, ownerEmail, userInput);
        run.setStatus(WorkflowRun.RunStatus.PENDING);
        runRepo.save(run);
        log.info("[WorkflowRun] Starting run {} for workflow {}", run.getId(), workflowId);

        asyncPool.submit(() -> executeRun(run));
        return run.getId();
    }

    /**
     * Opens an SSE stream for a run. The caller must hold the connection open.
     * Historical logs are replayed first, then live events streamed.
     */
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

    public List<WorkflowRunLog> getLogs(String runId) {
        return logRepo.findByRunIdOrderByCreatedAt(runId);
    }

    public List<WorkflowRun> getRuns(String workflowId) {
        return runRepo.findByWorkflowIdOrderByStartedAtDesc(workflowId);
    }

    // ── Execution engine ──────────────────────────────────────────────────────

    private void executeRun(WorkflowRun run) {
        Workflow workflow = workflowService.findById(run.getWorkflowId())
                .orElseThrow(() -> new RuntimeException("Workflow not found: " + run.getWorkflowId()));
        List<WorkflowAgent> agents = agentRepo.findByWorkflowIdOrderByOrderIndex(run.getWorkflowId());

        run.setStatus(WorkflowRun.RunStatus.RUNNING);
        runRepo.save(run);

        boolean needsNetwork = agents.stream().anyMatch(a -> {
            List<String> tools = workflowService.parseTools(a);
            return tools.stream().anyMatch(t -> t.equalsIgnoreCase("CURL") || t.equalsIgnoreCase("NET"));
        });

        String containerId = needsNetwork
                ? sandboxService.createSandboxWithNetwork(run.getId())
                : sandboxService.createSandbox(run.getId());
        run.setSandboxContainer(containerId);
        runRepo.save(run);

        emit(run.getId(), null, null, WorkflowRunLog.LogType.SYSTEM,
                "Workflow started [pattern=" + workflow.getAgentPattern() + "]");

        try {
            String output = switch (workflow.getAgentPattern()) {
                case ORCHESTRATOR -> executeOrchestrator(run, agents, containerId);
                case TEAM         -> executeTeam(run, workflow, agents, containerId);
            };

            run.setFinalOutput(output);
            run.setStatus(WorkflowRun.RunStatus.DONE);
            run.setFinishedAt(Instant.now());
            runRepo.save(run);

            emit(run.getId(), null, null, WorkflowRunLog.LogType.SYSTEM, "Workflow completed.");
            pushDoneEvent(run.getId(), "DONE", output);

        } catch (SandboxService.SandboxQueueFullException | SandboxService.SandboxResourceException ex) {
            log.warn("[WorkflowRun] Run {} rejected — sandbox capacity: {}", run.getId(), ex.getMessage());
            run.setStatus(WorkflowRun.RunStatus.FAILED);
            run.setFinishedAt(Instant.now());
            runRepo.save(run);
            emit(run.getId(), null, null, WorkflowRunLog.LogType.ERROR,
                    "Sandbox capacity limit reached: " + ex.getMessage() + ". Please retry in a moment.");
            pushDoneEvent(run.getId(), "FAILED", ex.getMessage());

        } catch (Exception ex) {
            log.error("[WorkflowRun] Run {} failed: {}", run.getId(), ex.getMessage(), ex);
            run.setStatus(WorkflowRun.RunStatus.FAILED);
            run.setFinishedAt(Instant.now());
            runRepo.save(run);

            emit(run.getId(), null, null, WorkflowRunLog.LogType.ERROR, ex.getMessage());
            pushDoneEvent(run.getId(), "FAILED", ex.getMessage());

        } finally {
            sandboxService.destroySandbox(containerId);
        }
    }

    // ── Orchestrator pattern ──────────────────────────────────────────────────

    private String executeOrchestrator(WorkflowRun run, List<WorkflowAgent> agents, String containerId) {
        WorkflowAgent mainAgent = agents.stream()
                .filter(a -> a.getRole() == WorkflowAgent.AgentRole.MAIN)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No MAIN agent defined in workflow"));

        List<WorkflowAgent> subAgents = agents.stream()
                .filter(a -> a.getRole() == WorkflowAgent.AgentRole.SUB)
                .toList();

        String systemPrompt = buildOrchestratorPrompt(mainAgent, subAgents);
        return runReActLoop(run, mainAgent, systemPrompt, run.getUserInput(), containerId, subAgents);
    }

    private String buildOrchestratorPrompt(WorkflowAgent main, List<WorkflowAgent> subs) {
        StringBuilder sb = new StringBuilder();
        sb.append(main.getSystemPrompt() != null ? main.getSystemPrompt() : "You are a helpful orchestrator agent.");

        List<String> tools = workflowService.parseTools(main);
        if (!tools.isEmpty()) {
            sb.append(buildToolSection(tools));
        }

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
                               List<WorkflowAgent> agents, String containerId) {
        List<WorkflowAgent> peers = agents.stream()
                .filter(a -> a.getRole() == WorkflowAgent.AgentRole.PEER)
                .toList();
        if (peers.isEmpty()) throw new RuntimeException("No PEER agents defined for team workflow");

        return switch (workflow.getTeamExecMode()) {
            case PARALLEL  -> executeTeamParallel(run, peers, containerId);
            case SEQUENTIAL -> executeTeamSequential(run, peers, containerId);
            case null -> executeTeamParallel(run, peers, containerId);
        };
    }

    private String executeTeamParallel(WorkflowRun run, List<WorkflowAgent> peers, String containerId) {
        emit(run.getId(), null, null, WorkflowRunLog.LogType.SYSTEM,
                "Running " + peers.size() + " agents in parallel…");

        List<CompletableFuture<String>> futures = peers.stream()
                .map(agent -> CompletableFuture.supplyAsync(
                        () -> {
                            String sysPrompt = (agent.getSystemPrompt() != null ? agent.getSystemPrompt() : "")
                                    + buildToolSection(workflowService.parseTools(agent));
                            return runReActLoop(run, agent, sysPrompt, run.getUserInput(), containerId, List.of());
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

    private String executeTeamSequential(WorkflowRun run, List<WorkflowAgent> peers, String containerId) {
        emit(run.getId(), null, null, WorkflowRunLog.LogType.SYSTEM,
                "Running " + peers.size() + " agents sequentially…");

        String currentInput = run.getUserInput();
        for (int i = 0; i < peers.size(); i++) {
            WorkflowAgent agent = peers.get(i);
            String sysPrompt = (agent.getSystemPrompt() != null ? agent.getSystemPrompt() : "")
                    + buildToolSection(workflowService.parseTools(agent));
            currentInput = runReActLoop(run, agent, sysPrompt, currentInput, containerId, List.of());
            if (i < peers.size() - 1) {
                sandboxService.recycleSandbox(containerId);  // clean between sequential steps
            }
        }
        return currentInput;
    }

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
                                String containerId, List<WorkflowAgent> subAgents) {

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", userInput));

        emit(run.getId(), agent.getId(), agent.getName(), WorkflowRunLog.LogType.SYSTEM,
                "Agent [" + agent.getName() + "] starting on: " + truncate(userInput, 200));

        for (int iter = 0; iter < MAX_REACT_ITERATIONS; iter++) {
            String context = buildContext(messages);
            String llmResponse = chatClient.prompt()
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
                            + buildToolSection(workflowService.parseTools(subAgent));
                    subResult = runReActLoop(run, subAgent, subPrompt, delegatedTask, containerId, List.of());
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

                String blocked = validateNetworkCommand(command, run.getOwnerEmail());
                if (blocked != null) {
                    emit(run.getId(), agent.getId(), agent.getName(),
                            WorkflowRunLog.LogType.TOOL_RESULT, blocked);
                    messages.add(Map.of("role", "assistant", "content", llmResponse));
                    messages.add(Map.of("role", "user", "content",
                            "Tool result (" + toolName + "):\n" + blocked));
                    continue;
                }

                String toolResult = sandboxService.exec(containerId, command);

                emit(run.getId(), agent.getId(), agent.getName(), WorkflowRunLog.LogType.TOOL_RESULT,
                        toolResult);

                messages.add(Map.of("role", "assistant", "content", llmResponse));
                messages.add(Map.of("role", "user", "content",
                        "Tool result (" + toolName + "):\n" + toolResult));
                continue;
            }

            // No tool calls and no delegation → final answer
            return llmResponse;
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

    private String buildToolSection(List<String> tools) {
        if (tools.isEmpty()) return "";
        return """

                ## Available Tools
                When you need to run a command, use this format and then STOP — wait for the result:
                <use_tool name="bash">
                your shell command here
                </use_tool>

                You can use: """ + String.join(", ", tools.stream().map(String::toLowerCase).toList()) + """

                When you have a final answer, respond normally without any XML tags.
                """;
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
    private String validateNetworkCommand(String command, String ownerEmail) {
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
        for (String url : urls) {
            if (!webFetchService.isUrlAllowed(url, ownerEmail)) {
                String host = url.replaceAll("^https?://([^/?#]+).*", "$1");
                return "[Blocked: domain '" + host + "' is not in your web-fetch whitelist. "
                        + "Add it in Settings → Web Fetch before using it in a workflow.]";
            }
        }
        return null;
    }
}
