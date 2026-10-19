package com.agentsystem.agent;

import com.agentsystem.connector.tool.ChoicePromptAgentTool;
import com.agentsystem.connector.tool.WebFetchAgentTool;
import com.agentsystem.schema.ToolActivity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

/**
 * Wraps a request's tool callbacks so every call the model makes is appended to {@code sink}
 * as a {@link ToolActivity} — the label comes from the tool name and its arguments, the status
 * from the tool's result. Recording happens here, in one place, instead of in each tool.
 */
public final class ToolActivityRecorder {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_DETAIL_CHARS = 200;

    /** Result prefixes the tools use for a refused call (allowlist / Web Risk). */
    private static final List<String> BLOCKED_PREFIXES = List.of(
            WebFetchAgentTool.NOT_ALLOWED_MESSAGE, "Blocked:");

    /** Result prefixes the tools use when a call did not succeed. */
    private static final List<String> FAILED_PREFIXES = List.of(
            ToolCallBudget.EXHAUSTED_MESSAGE, "Error:", "Could not", "Cannot fetch",
            "Web search is currently unavailable", "Web page fetch is currently unavailable",
            "Sandbox was terminated", "No sandbox is attached", "You don't have access to this sandbox",
            "The attached sandbox no longer exists");

    private ToolActivityRecorder() {}

    public static ToolCallbackProvider recording(ToolCallbackProvider delegate, List<ToolActivity> sink) {
        ToolCallback[] wrapped = Arrays.stream(delegate.getToolCallbacks())
                .map(cb -> (ToolCallback) new Recording(cb, sink))
                .toArray(ToolCallback[]::new);
        return () -> wrapped;
    }

    private record Recording(ToolCallback delegate, List<ToolActivity> sink) implements ToolCallback {

        @Override public ToolDefinition getToolDefinition() { return delegate.getToolDefinition(); }
        @Override public ToolMetadata   getToolMetadata()   { return delegate.getToolMetadata(); }

        @Override
        public String call(String toolInput) {
            return record(toolInput, () -> delegate.call(toolInput));
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            return record(toolInput, () -> delegate.call(toolInput, toolContext));
        }

        private String record(String toolInput, java.util.function.Supplier<String> call) {
            String name = delegate.getToolDefinition().name();
            // The offered options are shown as buttons under the answer, not as an activity line.
            if (ChoicePromptAgentTool.TOOL_NAME.equals(name)) return call.get();
            try {
                String result = call.get();
                sink.add(describe(name, toolInput, statusOf(result)));
                return result;
            } catch (RuntimeException e) {
                sink.add(describe(name, toolInput, ToolActivity.Status.FAILED));
                throw e;
            }
        }
    }

    static ToolActivity describe(String tool, String toolInput, ToolActivity.Status status) {
        JsonNode args = parse(toolInput);
        return switch (tool) {
            case "searchWeb"                  -> activity(tool, "Searched the web", text(args, "query"), status);
            case "fetchWebPage"               -> activity(tool, "Read web page", host(text(args, "url")), status);
            case "readGoogleDoc"              -> activity(tool, "Read Google Doc", null, status);
            case "readGoogleSheet"            -> activity(tool, "Read Google Sheet", null, status);
            case "readGoogleSlide"            -> activity(tool, "Read Google Slides", null, status);
            case "writeToGoogleDocs"          -> activity(tool, "Created Google Doc", text(args, "title"), status);
            case "writeToGoogleSheets"        -> activity(tool, "Created Google Sheet", text(args, "title"), status);
            case "writeToGoogleSlides"        -> activity(tool, "Created Google Slides", text(args, "title"), status);
            case "listUpcomingEvents"         -> activity(tool, "Checked calendar", null, status);
            case "createCalendarEvent"        -> activity(tool, "Created calendar event", text(args, "title"), status);
            case "sendTelegramMessage"        -> activity(tool, "Sent Telegram message", null, status);
            case "createTelegramGroupSession" -> activity(tool, "Sent to Telegram group", null, status);
            case "getTravelRecords"           -> activity(tool, "Looked up trips", null, status);
            case "execInSandbox"              -> activity(tool, "Ran sandbox command", text(args, "command"), status);
            default                           -> activity(tool, tool, null, status);
        };
    }

    static ToolActivity.Status statusOf(String result) {
        String text = unquote(result);
        if (BLOCKED_PREFIXES.stream().anyMatch(text::startsWith)) return ToolActivity.Status.BLOCKED;
        if (FAILED_PREFIXES.stream().anyMatch(text::startsWith))  return ToolActivity.Status.FAILED;
        return ToolActivity.Status.OK;
    }

    private static ToolActivity activity(String tool, String label, String detail, ToolActivity.Status status) {
        if (detail != null && detail.length() > MAX_DETAIL_CHARS) {
            detail = detail.substring(0, MAX_DETAIL_CHARS) + "…";
        }
        return new ToolActivity(tool, label, detail, status);
    }

    /** Spring AI JSON-encodes a String return value; tolerate both encoded and raw results. */
    private static String unquote(String result) {
        if (result == null) return "";
        if (result.startsWith("\"")) {
            try {
                return JSON.readValue(result, String.class);
            } catch (Exception ignored) {
                // fall through to the raw text
            }
        }
        return result;
    }

    private static JsonNode parse(String toolInput) {
        try {
            return toolInput == null ? null : JSON.readTree(toolInput);
        } catch (Exception e) {
            return null;
        }
    }

    private static String text(JsonNode args, String field) {
        if (args == null) return null;
        JsonNode v = args.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    /** Only the host is shown — a user-supplied URL's path/query can carry tokens. */
    private static String host(String url) {
        if (url == null) return null;
        try {
            String host = URI.create(url.strip()).getHost();
            return host == null ? null : host.toLowerCase(java.util.Locale.ROOT);
        } catch (Exception e) {
            return null;
        }
    }
}
