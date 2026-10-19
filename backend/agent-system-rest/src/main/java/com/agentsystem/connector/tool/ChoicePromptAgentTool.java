package com.agentsystem.connector.tool;

import com.agentsystem.schema.ChoicePrompt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Spring AI tool: offer the user a short list of options to pick from instead of asking
 * them to type an answer. The prompt is handed back to GeneratorNode through a per-request
 * sink (same ThreadLocal pattern as the other tools) and returned on the AgentResponse; the
 * client renders it as buttons and sends the picked option as the user's next message.
 */
@Slf4j
@Component
public class ChoicePromptAgentTool {

    public static final String TOOL_NAME = "askUserChoice";

    static final int MIN_OPTIONS        = 2;
    static final int MAX_OPTIONS        = 6;
    static final int MAX_OPTION_CHARS   = 120;
    static final int MAX_QUESTION_CHARS = 300;

    private static final ThreadLocal<AtomicReference<ChoicePrompt>> SINK = new ThreadLocal<>();

    public void setCurrentSink(AtomicReference<ChoicePrompt> sink) { SINK.set(sink); }
    public void clearCurrentSink()                                 { SINK.remove(); }

    @Tool(name = TOOL_NAME, description = """
            Shows the user a question with 2-6 clickable options and lets them answer with
            one click. Call this when you need the user to make a decision before you can
            continue and the sensible answers form a short, fixed list — e.g. which of
            several interpretations they meant, which option to go with, or which next step
            to take. Do not call it for open-ended questions, for yes/no confirmations you
            can reasonably assume, or more than once per reply. The user can always type a
            different answer instead, so do not add an "Other" option.
            """)
    public String askUserChoice(
            @ToolParam(description = "The question to ask, as one short sentence") String question,
            @ToolParam(description = "2-6 short, distinct options, each a few words") List<String> options,
            @ToolParam(description = "true if the user may pick several options", required = false)
            Boolean multiSelect) {
        AtomicReference<ChoicePrompt> sink = SINK.get();
        if (sink == null) return "Error: options can't be shown here — ask the question in plain text instead.";
        if (sink.get() != null) return "Error: you already offered options in this reply — wait for the user's answer.";

        String q = question == null ? "" : question.strip();
        if (q.isEmpty()) return "Error: question must not be empty.";
        if (q.length() > MAX_QUESTION_CHARS) return "Error: question must be at most " + MAX_QUESTION_CHARS + " characters.";

        Set<String> distinct = new LinkedHashSet<>();
        if (options != null) {
            for (String o : options) {
                if (o == null || o.isBlank()) continue;
                String opt = o.strip();
                if (opt.length() > MAX_OPTION_CHARS) {
                    return "Error: each option must be at most " + MAX_OPTION_CHARS + " characters.";
                }
                distinct.add(opt);
            }
        }
        if (distinct.size() < MIN_OPTIONS || distinct.size() > MAX_OPTIONS) {
            return "Error: provide between " + MIN_OPTIONS + " and " + MAX_OPTIONS + " distinct, non-empty options.";
        }

        sink.set(new ChoicePrompt(q, List.copyOf(distinct), Boolean.TRUE.equals(multiSelect)));
        log.info("[ChoicePromptAgentTool] Offered {} option(s)", distinct.size());
        return """
                The question and options are now shown to the user as clickable buttons below \
                your reply; they may also type their own answer. End your reply now with a short \
                lead-in to the question. Do not list the options again, do not pick one for the \
                user, and do not continue the task until they answer.""";
    }
}
