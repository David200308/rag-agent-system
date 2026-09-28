package com.agentsystem.connector.tool;

import com.agentsystem.agent.ToolCallBudget;
import com.agentsystem.sandbox.entity.PersistentSandbox;
import com.agentsystem.sandbox.service.PersistentSandboxService;
import com.agentsystem.sandbox.service.SandboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * Spring AI tool: run a shell command in the caller's own persistent sandbox during chat.
 *
 * Chat can only ever use a sandbox the user already launched from the Sandbox Management
 * page — it never creates one. The selected sandbox id and caller's user_uuid are injected
 * per-request via setCurrentSandboxId / setCurrentUserUuid (same ThreadLocal pattern used by
 * the other chat tools), and this tool is only wired into the LLM call at all when the caller
 * opted into a sandbox for that turn (see GeneratorNode) — optional, off by default.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SandboxAgentTool {

    private final PersistentSandboxService persistentSandboxService;
    private final SandboxService           sandboxService;
    private final ToolCallBudget           toolCallBudget;

    private static final ThreadLocal<String> CURRENT_SANDBOX_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_USER_UUID  = new ThreadLocal<>();

    public void setCurrentSandboxId(String sandboxId) { CURRENT_SANDBOX_ID.set(sandboxId != null ? sandboxId : ""); }
    public void clearCurrentSandboxId()               { CURRENT_SANDBOX_ID.remove(); }

    public void setCurrentUserUuid(String uuid) { CURRENT_USER_UUID.set(uuid != null ? uuid : ""); }
    public void clearCurrentUserUuid()          { CURRENT_USER_UUID.remove(); }

    @Tool(description = """
            Runs a shell command in the sandbox the user selected for this conversation, and
            returns its output. Only available when the user has attached one of their own
            persistent sandboxes (launched from the Sandbox Management page) to this chat —
            never assume one is available beyond what this tool actually reports. Use it for
            tasks the user asks you to run, check, or inspect via that sandbox (e.g. run a
            script, check a file, install something), not for general knowledge questions.
            """)
    public String execInSandbox(String command) {
        if (!toolCallBudget.tryConsume()) return ToolCallBudget.EXHAUSTED_MESSAGE;

        String sandboxId = CURRENT_SANDBOX_ID.get();
        String userUuid  = CURRENT_USER_UUID.get();
        if (sandboxId == null || sandboxId.isBlank()) {
            return "No sandbox is attached to this conversation.";
        }

        try {
            PersistentSandbox sandbox = persistentSandboxService.acquireForRun(
                    sandboxId, userUuid, msg -> log.debug("[SandboxAgentTool] {}", msg));
            return sandboxService.exec(sandbox.getContainerId(), command);
        } catch (SecurityException e) {
            return "You don't have access to this sandbox.";
        } catch (IllegalArgumentException e) {
            return "The attached sandbox no longer exists — it may have been removed.";
        } catch (SandboxService.SandboxKilledException e) {
            return "Sandbox was terminated: " + e.getMessage();
        }
    }
}
