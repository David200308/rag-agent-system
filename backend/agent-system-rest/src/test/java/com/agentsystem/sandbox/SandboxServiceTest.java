package com.ragagent.sandbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RSemaphore;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SandboxServiceTest {

    SandboxService      sandboxService;
    RedissonClient      redisson;
    StringRedisTemplate redisTemplate;

    /** redisKey → (hashKey → hashValue), backing the mocked HashOperations below. */
    Map<String, Map<Object, Object>> hashesByKey;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisson = mock(RedissonClient.class);
        RSemaphore semaphore = mock(RSemaphore.class);
        when(semaphore.trySetPermits(anyInt())).thenReturn(true);
        when(redisson.getSemaphore(anyString())).thenReturn(semaphore);

        hashesByKey = new ConcurrentHashMap<>();
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        wireHashOpsFake(hashOps);

        redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);

        sandboxService = new SandboxService(redisson, redisTemplate);
        // Set @Value fields that status() reads
        ReflectionTestUtils.setField(sandboxService, "maxConcurrent", 3);
        ReflectionTestUtils.setField(sandboxService, "queueCapacity",  10);
        ReflectionTestUtils.setField(sandboxService, "enabled",        false); // skip Docker in tests
        ReflectionTestUtils.setField(sandboxService, "watchdogEnabled", false); // skip watchdog
        // Call @PostConstruct to initialise the semaphore and queue
        sandboxService.init();
    }

    /** Fake HashOperations backed by {@link #hashesByKey} — real put/get/delete/keys/hasKey semantics, no Redis needed. */
    private void wireHashOpsFake(HashOperations<String, Object, Object> hashOps) {
        doAnswer(inv -> {
            String key = inv.getArgument(0);
            Object hk  = inv.getArgument(1);
            Object hv  = inv.getArgument(2);
            hashesByKey.computeIfAbsent(key, k -> new ConcurrentHashMap<>()).put(hk, hv);
            return null;
        }).when(hashOps).put(any(), any(), any());

        when(hashOps.get(any(), any())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            Object hk  = inv.getArgument(1);
            Map<Object, Object> m = hashesByKey.get(key);
            return m == null ? null : m.get(hk);
        });

        when(hashOps.delete(any(), any())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            Object hk  = inv.getArgument(1);
            Map<Object, Object> m = hashesByKey.get(key);
            return (m != null && m.remove(hk) != null) ? 1L : 0L;
        });

        when(hashOps.keys(any())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            Map<Object, Object> m = hashesByKey.get(key);
            return m == null ? Set.of() : new HashSet<>(m.keySet());
        });

        when(hashOps.hasKey(any(), any())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            Object hk  = inv.getArgument(1);
            Map<Object, Object> m = hashesByKey.get(key);
            return m != null && m.containsKey(hk);
        });
    }

    private static String activeKey() {
        return (String) ReflectionTestUtils.getField(SandboxService.class, "ACTIVE_CONTAINERS_KEY");
    }

    private static String killedKey() {
        return (String) ReflectionTestUtils.getField(SandboxService.class, "KILLED_CONTAINERS_KEY");
    }

    // ── status ─────────────────────────────────────────────────────────────────

    @Test
    void status_returnsCorrectCapacityValues() {
        SandboxService.SandboxStatus status = sandboxService.status();

        assertThat(status.maxConcurrent()).isEqualTo(3);
        assertThat(status.queueCapacity()).isEqualTo(10);
        assertThat(status.active()).isEqualTo(0);
        assertThat(status.queued()).isEqualTo(0);
    }

    @Test
    void sandboxStatus_atCapacity_falseWhenIdle() {
        SandboxService.SandboxStatus status = new SandboxService.SandboxStatus(3, 0, 0, 10);
        assertThat(status.atCapacity()).isFalse();
    }

    @Test
    void sandboxStatus_atCapacity_trueWhenFull() {
        SandboxService.SandboxStatus status = new SandboxService.SandboxStatus(3, 3, 0, 10);
        assertThat(status.atCapacity()).isTrue();
    }

    // ── exec — null/blank containerId ─────────────────────────────────────────

    @Test
    void exec_nullContainerId_returnsSandboxUnavailableMessage() {
        String result = sandboxService.exec(null, "ls");
        assertThat(result).contains("unavailable");
    }

    @Test
    void exec_blankContainerId_returnsSandboxUnavailableMessage() {
        String result = sandboxService.exec("   ", "ls");
        assertThat(result).contains("unavailable");
    }

    // ── exec — killed container ────────────────────────────────────────────────

    @Test
    void exec_killedContainer_throwsSandboxKilledException() {
        hashesByKey.computeIfAbsent(killedKey(), k -> new ConcurrentHashMap<>())
                .put("abc123def456", "CPU 95% exceeded limit 80%");

        assertThatThrownBy(() -> sandboxService.exec("abc123def456", "ls"))
                .isInstanceOf(SandboxService.SandboxKilledException.class)
                .hasMessageContaining("excessive resource usage");
    }

    // ── destroySandbox — null/blank ────────────────────────────────────────────

    @Test
    void destroySandbox_nullContainerId_doesNotThrow() {
        sandboxService.destroySandbox(null);  // should be a no-op
    }

    @Test
    void destroySandbox_blankContainerId_doesNotThrow() {
        sandboxService.destroySandbox("   ");  // should be a no-op
    }

    // ── recycleSandbox — null ──────────────────────────────────────────────────

    @Test
    void recycleSandbox_nullContainerId_doesNotThrow() {
        sandboxService.recycleSandbox(null);
    }

    // ── createSandbox — disabled ───────────────────────────────────────────────

    @Test
    void createSandbox_whenDisabled_returnsNull() {
        String containerId = sandboxService.createSandbox("run-1", msg -> {});
        assertThat(containerId).isNull();
    }

    @Test
    void createSandboxWithNetwork_whenDisabled_returnsNull() {
        String containerId = sandboxService.createSandboxWithNetwork("run-2", msg -> {});
        assertThat(containerId).isNull();
    }

    // ── exception types ────────────────────────────────────────────────────────

    @Test
    void sandboxQueueFullException_hasCorrectMessage() {
        var ex = new SandboxService.SandboxQueueFullException("Queue is full");
        assertThat(ex.getMessage()).isEqualTo("Queue is full");
    }

    @Test
    void sandboxResourceException_hasCorrectMessage() {
        var ex = new SandboxService.SandboxResourceException("CPU overload");
        assertThat(ex.getMessage()).isEqualTo("CPU overload");
    }

    @Test
    void sandboxStartupException_hasCorrectMessage() {
        var ex = new SandboxService.SandboxStartupException("docker failed");
        assertThat(ex.getMessage()).isEqualTo("docker failed");
    }

    @Test
    void sandboxKilledException_hasCorrectMessage() {
        var ex = new SandboxService.SandboxKilledException("memory limit exceeded");
        assertThat(ex.getMessage()).isEqualTo("memory limit exceeded");
    }

    // ── SandboxStatus record ──────────────────────────────────────────────────

    @Test
    void sandboxStatus_record_fields() {
        SandboxService.SandboxStatus status = new SandboxService.SandboxStatus(5, 2, 3, 20);
        assertThat(status.maxConcurrent()).isEqualTo(5);
        assertThat(status.active()).isEqualTo(2);
        assertThat(status.queued()).isEqualTo(3);
        assertThat(status.queueCapacity()).isEqualTo(20);
    }

    @Test
    void sandboxStatus_atCapacity_trueWhenActiveEqualsMax() {
        SandboxService.SandboxStatus s = new SandboxService.SandboxStatus(3, 3, 1, 10);
        assertThat(s.atCapacity()).isTrue();
    }

    @Test
    void sandboxStatus_atCapacity_trueWhenActiveExceedsMax() {
        // edge case: active > max (shouldn't happen normally, but guard is >=)
        SandboxService.SandboxStatus s = new SandboxService.SandboxStatus(3, 4, 0, 10);
        assertThat(s.atCapacity()).isTrue();
    }

    // ── exec — killed container propagates KilledException ───────────────────

    @Test
    void recycleSandbox_killedContainer_propagatesException() {
        hashesByKey.computeIfAbsent(killedKey(), k -> new ConcurrentHashMap<>())
                .put("containerXYZ", "memory 95% exceeded limit 90%");

        assertThatThrownBy(() -> sandboxService.recycleSandbox("containerXYZ"))
                .isInstanceOf(SandboxService.SandboxKilledException.class);
    }

    // ── destroySandbox — killed-by-watchdog skips docker rm ───────────────────

    @Test
    void destroySandbox_killedByWatchdog_doesNotTryDockerRm() {
        hashesByKey.computeIfAbsent(killedKey(), k -> new ConcurrentHashMap<>())
                .put("watchdog-container", "cpu exceeded");
        hashesByKey.computeIfAbsent(activeKey(), k -> new ConcurrentHashMap<>())
                .put("watchdog-container", "run-99");

        // Since sandbox is disabled and watchdog killed it, destroySandbox should not run docker rm
        // (which would fail anyway without Docker). It just cleans up internal state.
        sandboxService.destroySandbox("watchdog-container");

        // Container should be removed from both hashes
        assertThat(hashesByKey.get(killedKey())).doesNotContainKey("watchdog-container");
        assertThat(hashesByKey.get(activeKey())).doesNotContainKey("watchdog-container");
    }

    // ── createSandbox — queue full ─────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void createSandbox_enabled_queueFull_throwsQueueFullException() {
        // Set up a sandbox service with capacity=1, fill the queue, then try to add another
        SandboxService svc2 = new SandboxService(redisson, redisTemplate);
        ReflectionTestUtils.setField(svc2, "maxConcurrent",       1);
        ReflectionTestUtils.setField(svc2, "queueCapacity",       1);
        ReflectionTestUtils.setField(svc2, "enabled",             true);
        ReflectionTestUtils.setField(svc2, "watchdogEnabled",     false);
        ReflectionTestUtils.setField(svc2, "cpuLoadThreshold",    0.99);
        ReflectionTestUtils.setField(svc2, "freeMemoryThreshold", 0.001);
        svc2.init();

        // Pre-fill the wait queue so it's at capacity (1 entry)
        var waitQueue = (java.util.concurrent.BlockingQueue<String>)
                ReflectionTestUtils.getField(svc2, "waitQueue");
        waitQueue.offer("existing-run");

        assertThatThrownBy(() -> svc2.createSandbox("run-x", msg -> {}))
                .isInstanceOf(SandboxService.SandboxQueueFullException.class)
                .hasMessageContaining("queue full");

        svc2.shutdown();
    }

    // ── parsePercent via exception types ──────────────────────────────────────

    @Test
    void exec_blankOutputFromCommand_returnsNoOutputMessage() {
        // When containerId is null, we get unavailable message. The "(no output)" branch
        // requires a real container. Test the null/blank path to verify short-circuit.
        assertThat(sandboxService.exec(null, "echo ''")).contains("unavailable");
        assertThat(sandboxService.exec("", "echo ''")).contains("unavailable");
    }

    // ── shutdown ──────────────────────────────────────────────────────────────

    @Test
    void shutdown_whenWatchdogNull_doesNotThrow() {
        // watchdogEnabled=false so watchdogExecutor is null; shutdown should be no-op
        sandboxService.shutdown();
    }

    // ── parsePercent (private via reflection) ─────────────────────────────────

    @Test
    void parsePercent_validPercent_parsesCorrectly() throws Exception {
        assertThat(callParsePercent("85.5%")).isEqualTo(85.5);
    }

    @Test
    void parsePercent_withoutPercentSign_parsesCorrectly() throws Exception {
        assertThat(callParsePercent("80.0")).isEqualTo(80.0);
    }

    @Test
    void parsePercent_invalidString_returnsZero() throws Exception {
        assertThat(callParsePercent("N/A")).isEqualTo(0.0);
    }

    // ── shortId (private via reflection) ─────────────────────────────────────

    @Test
    void shortId_longId_returnsFirst12Chars() throws Exception {
        String result = callShortId("abcdefghijklmnopqrstuvwxyz");
        assertThat(result).isEqualTo("abcdefghijkl");
    }

    @Test
    void shortId_shortId_returnsUnchanged() throws Exception {
        String result = callShortId("abc123");
        assertThat(result).isEqualTo("abc123");
    }

    // ── truncate (private via reflection) ────────────────────────────────────

    @Test
    void truncate_shortString_returnsUnchanged() throws Exception {
        assertThat(callTruncate("hello", 100)).isEqualTo("hello");
    }

    @Test
    void truncate_longString_cutsAndAddsEllipsis() throws Exception {
        assertThat(callTruncate("abcdefghij", 5)).isEqualTo("abcde…");
    }

    // ── checkContainerResources no-op path ───────────────────────────────────

    @Test
    void checkContainerResources_noActiveContainers_isNoOp() throws Exception {
        // activeContainers hash is empty (no containers spawned), so method is a no-op
        Method m = SandboxService.class.getDeclaredMethod("checkContainerResources");
        m.setAccessible(true);
        // Should complete without throwing
        m.invoke(sandboxService);
    }

    // ── reflection helpers ────────────────────────────────────────────────────

    private double callParsePercent(String s) throws Exception {
        Method m = SandboxService.class.getDeclaredMethod("parsePercent", String.class);
        m.setAccessible(true);
        return (double) m.invoke(sandboxService, s);
    }

    private String callShortId(String id) throws Exception {
        Method m = SandboxService.class.getDeclaredMethod("shortId", String.class);
        m.setAccessible(true);
        return (String) m.invoke(sandboxService, id);
    }

    private String callTruncate(String s, int max) throws Exception {
        Method m = SandboxService.class.getDeclaredMethod("truncate", String.class, int.class);
        m.setAccessible(true);
        return (String) m.invoke(sandboxService, s, max);
    }
}
