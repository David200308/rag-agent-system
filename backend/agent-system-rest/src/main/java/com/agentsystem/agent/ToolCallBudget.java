package com.agentsystem.agent;

import org.springframework.stereotype.Component;

/**
 * Bounds how many connector tool calls (Google Docs/Sheets/Slides/Calendar, Telegram)
 * a single GeneratorNode invocation may make. Spring AI's internal tool-calling loop
 * has no iteration cap of its own — a model that keeps invoking tools would otherwise
 * run unbounded in cost, latency, and side effects (each call can send a real Telegram
 * message, write a real Google Doc, create a real calendar event, etc).
 *
 * Scoped via ThreadLocal, same pattern as the tool classes' per-request email/orgId —
 * GeneratorNode.process() and the whole Spring AI tool-calling loop it triggers run
 * synchronously on one thread, so this is safe under virtual threads without locking.
 */
@Component
public class ToolCallBudget {

    private static final int MAX_CALLS_PER_REQUEST = 8;

    private static final ThreadLocal<Integer> COUNT = ThreadLocal.withInitial(() -> 0);

    public void reset() {
        COUNT.set(0);
    }

    public void clear() {
        COUNT.remove();
    }

    /** Returns true and consumes one unit of budget if available; false once exhausted. */
    public boolean tryConsume() {
        int count = COUNT.get();
        if (count >= MAX_CALLS_PER_REQUEST) {
            return false;
        }
        COUNT.set(count + 1);
        return true;
    }

    public static final String EXHAUSTED_MESSAGE =
            "Error: tool-call limit reached for this request. Stop calling tools and answer with the information already gathered.";
}
