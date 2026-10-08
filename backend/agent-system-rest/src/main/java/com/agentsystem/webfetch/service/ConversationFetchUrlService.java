package com.agentsystem.webfetch.service;

import com.agentsystem.webfetch.entity.ConversationFetchUrl;
import com.agentsystem.webfetch.repository.ConversationFetchUrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Durable, per-conversation memory of searchWeb result URLs, so a link a previous turn's
 * search returned stays fetchable in later turns (see {@link WebFetchUrlAllowlist}).
 * Only URLs the server itself got back from searchWeb are ever written — never text the
 * model or a fetched page produced. Stored as hashes of the normalized URL.
 *
 * Failures are logged and swallowed: losing this only means a later turn has to search again.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationFetchUrlService {

    private final ConversationFetchUrlRepository repository;

    /** @return hashes of the URLs earlier turns' searches returned; empty if none or no conversation */
    public Set<String> loadHashes(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) return Set.of();
        try {
            return new HashSet<>(repository.findUrlHashesByConversationId(conversationId));
        } catch (Exception e) {
            log.warn("[ConversationFetchUrlService] Load failed for conversationId={}: {}",
                    conversationId, e.getMessage());
            return Set.of();
        }
    }

    /**
     * Records {@code normalizedUrls} (this request's searchWeb results) for the conversation,
     * skipping any whose hash is already in {@code knownHashes}.
     */
    public void save(String conversationId, Set<String> knownHashes, Collection<String> normalizedUrls) {
        if (conversationId == null || conversationId.isBlank() || normalizedUrls.isEmpty()) return;
        Instant now = Instant.now();
        int saved = 0;
        for (String hash : normalizedUrls.stream().map(UrlNormalizer::hash).distinct().toList()) {
            if (knownHashes.contains(hash)) continue;
            try {
                repository.save(ConversationFetchUrl.builder()
                        .conversationId(conversationId)
                        .urlHash(hash)
                        .createdAt(now)
                        .build());
                saved++;
            } catch (Exception e) {
                // e.g. a concurrent request in the same conversation inserted it first — harmless
                log.debug("[ConversationFetchUrlService] Save skipped: {}", e.getMessage());
            }
        }
        if (saved > 0) {
            log.debug("[ConversationFetchUrlService] Saved {} URL(s) for conversationId={}", saved, conversationId);
        }
    }
}
