package com.agentsystem.travel;

import com.agentsystem.config.TravelInnerProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Calls travel-inner's internal REST API on behalf of authenticated users, and on behalf
 * of TravelAgentTool during chat tool-calling. Uses X-Travel-Key authentication — no JWT
 * needed (mirrors StorageClient/FinanceInnerClient).
 *
 * List/expenses/create/update responses are deserialized generically (Object/List<Object>)
 * since TravelController only relays that JSON through — it never inspects those fields.
 * {@link #listChatVisible} is the one exception: TravelAgentTool reads structured fields
 * (title, dates, stops, notes, expenses) to format its own text output, so this client
 * defines a local {@link TravelRecord} record mirroring the wire shape — the same pattern
 * StorageClient uses for its own response records, rather than depending on travel-inner's
 * internal DTO class.
 *
 * A 403 from travel-inner (id not owned by this user) is re-thrown here as
 * {@link SecurityException}; a 404 (id not found) as {@link IllegalArgumentException} —
 * matching what the old in-process TravelServiceImpl used to throw, so TravelController's
 * existing per-endpoint catch blocks don't need to change.
 */
@Component
public class TravelInnerClient {

    private final RestClient restClient;

    public TravelInnerClient(TravelInnerProperties props) {
        this.restClient = RestClient.builder()
                .baseUrl(props.url())
                .defaultHeader("X-Travel-Key", props.serviceKey())
                .defaultStatusHandler(HttpStatusCode::is4xxClientError, (req, res) -> {
                    if (res.getStatusCode().value() == 403) {
                        throw new SecurityException("Not authorised");
                    }
                    if (res.getStatusCode().value() == 404) {
                        throw new IllegalArgumentException("Not found");
                    }
                })
                .build();
    }

    private static final ParameterizedTypeReference<List<Object>> LIST_TYPE =
            new ParameterizedTypeReference<>() {};

    /** Mirrors travel-inner's TravelRecordDto wire shape — see class javadoc. */
    public record TravelRecord(
            String id, String ownerUuid, String title, String startDate, String endDate,
            List<Map<String, Object>> stops, List<Map<String, Object>> expenses,
            String notes, boolean allowChat, Instant createdAt, Instant updatedAt) {}

    private static final ParameterizedTypeReference<List<TravelRecord>> CHAT_VISIBLE_TYPE =
            new ParameterizedTypeReference<>() {};

    public List<Object> list(String ownerUuid) {
        return restClient.get()
                .uri("/internal/travel?ownerUuid={o}", ownerUuid)
                .retrieve().body(LIST_TYPE);
    }

    public List<TravelRecord> listChatVisible(String ownerUuid) {
        List<TravelRecord> result = restClient.get()
                .uri("/internal/travel/chat-visible?ownerUuid={o}", ownerUuid)
                .retrieve().body(CHAT_VISIBLE_TYPE);
        return result != null ? result : List.of();
    }

    public List<Object> getExpenses(String id, String ownerUuid) {
        return restClient.get()
                .uri("/internal/travel/{id}/expenses?ownerUuid={o}", id, ownerUuid)
                .retrieve().body(LIST_TYPE);
    }

    public Object create(String ownerUuid, Map<String, Object> body) {
        return restClient.post()
                .uri("/internal/travel?ownerUuid={o}", ownerUuid)
                .body(body).retrieve().body(Object.class);
    }

    public Object update(String id, String ownerUuid, Map<String, Object> body) {
        return restClient.put()
                .uri("/internal/travel/{id}?ownerUuid={o}", id, ownerUuid)
                .body(body).retrieve().body(Object.class);
    }

    public void delete(String id, String ownerUuid) {
        restClient.delete()
                .uri("/internal/travel/{id}?ownerUuid={o}", id, ownerUuid)
                .retrieve().toBodilessEntity();
    }
}
