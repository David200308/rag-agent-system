package com.agentsystem.alert.controller;

import com.agentsystem.config.AlertProperties;
import com.agentsystem.connector.service.TelegramService;
import com.agentsystem.notification.NotificationClient;
import com.agentsystem.user.service.UserAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

/**
 * Internal endpoint called by the Go investment-alert-task service when a price/DeFi/
 * prediction-market alert rule fires.
 *
 * Auth: validated via {@code X-Alert-Key} header — NOT a user JWT.
 *       The path /api/v1/alerts/trigger is exempt from AuthFilter (see AuthFilter).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
@Tag(name = "Alerts", description = "Internal trigger endpoint for the Go investment-alert-task service")
public class AlertTriggerController {

    private final AlertProperties     alertProperties;
    private final NotificationClient  notificationClient;
    private final TelegramService     telegramService;
    private final UserAccountService  userAccountService;
    private final StringRedisTemplate redisTemplate;

    private static final String IDEMPOTENCY_KEY_PREFIX = "alert:idempotency:";
    private static final Duration IDEMPOTENCY_TTL      = Duration.ofHours(24);

    /** Body sent by the Go investment-alert-task service when a rule fires. */
    public record AlertTriggerRequest(
            String ownerUuid,
            String orgId,
            String ruleType, // "price", "defi", "predict-market"
            String ruleId,
            String symbol,
            String message
    ) {}

    @PostMapping(value = "/trigger",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Deliver a fired alert to its owner via email/Telegram (internal, service-key protected)")
    public ResponseEntity<Void> trigger(
            @RequestHeader(value = "X-Alert-Key", required = false) String serviceKey,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody AlertTriggerRequest body) {

        if (serviceKey == null || !serviceKey.equals(alertProperties.serviceKey())) {
            log.warn("[AlertTrigger] Rejected request — invalid or missing X-Alert-Key");
            return ResponseEntity.status(401).build();
        }

        // investment-alert-task fires a fresh idempotency key per trigger (not per recurring
        // schedule), but the callback can still be retried by the caller on a network blip —
        // dedupe the same way SchedulerTriggerController does.
        if (idempotencyKey != null && !claimIdempotencyKey(idempotencyKey)) {
            log.info("[AlertTrigger] Duplicate request for idempotencyKey={} — skipping re-delivery", idempotencyKey);
            return ResponseEntity.ok().build();
        }

        log.info("[AlertTrigger] Firing ruleType={} ruleId={} owner={} message='{}'",
                body.ruleType(), body.ruleId(), body.ownerUuid(), body.message());

        String email = userAccountService.getEmailByUuid(body.ownerUuid());
        if (email != null) {
            notificationClient.sendAlertTriggered(email, body.ruleType(), body.symbol(), body.message());
        } else {
            log.warn("[AlertTrigger] No email found for ownerUuid={} — skipping email delivery", body.ownerUuid());
        }

        if (telegramService.isConnected(body.ownerUuid(), body.orgId())) {
            try {
                telegramService.sendMessage(body.ownerUuid(), body.orgId(), body.message());
            } catch (Exception e) {
                log.warn("[AlertTrigger] Failed to send Telegram message for owner={}: {}", body.ownerUuid(), e.getMessage());
            }
        }

        return ResponseEntity.ok().build();
    }

    /** Returns true the first time this key is seen (caller should proceed); false on a repeat. */
    private boolean claimIdempotencyKey(String idempotencyKey) {
        Boolean isNew = redisTemplate.opsForValue()
                .setIfAbsent(IDEMPOTENCY_KEY_PREFIX + idempotencyKey, "1", IDEMPOTENCY_TTL);
        return Boolean.TRUE.equals(isNew);
    }
}
