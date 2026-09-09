package com.agentsystem.sandbox.service.impl;

import com.agentsystem.sandbox.service.SandboxService;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RSemaphore;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Manages ephemeral Docker sandbox containers for workflow runs.
 *
 * Resource governance:
 *  - sandbox.max-concurrent  caps how many containers run simultaneously, enforced by a
 *    Redisson distributed semaphore so the cap holds cluster-wide, not per-instance
 *  - sandbox.queue-capacity  caps how many runs can wait in line before rejection
 *  - Admission check: refuses new sandboxes if system CPU or free memory
 *    exceed configured thresholds (fail-fast instead of overloading the host)
 *
 * Runtime watchdog:
 *  - Polls docker stats every sandbox.watchdog.interval-seconds seconds
 *  - Kills any container whose CPU% or memory% exceeds the configured limits
 *  - Killed containers cause the next exec() call to throw SandboxKilledException,
 *    which propagates up and fails the workflow run immediately
 *
 * Live container tracking (activeContainers/killedContainers) is stored in Redis
 * rather than in-process so the watchdog and exec() admission checks see the same
 * state regardless of which backend instance created the container.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SandboxServiceImpl implements SandboxService {

    private final RedissonClient       redisson;
    private final StringRedisTemplate  redisTemplate;

    @Value("${sandbox.enabled:true}")
    private boolean enabled;

    @Value("${sandbox.image:ragagent/sandbox:latest}")
    private String sandboxImage;

    @Value("${sandbox.memory-limit:512m}")
    private String memoryLimit;

    @Value("${sandbox.cpu-quota:50000}")
    private String cpuQuota;

    @Value("${sandbox.exec-timeout-seconds:30}")
    private int execTimeoutSeconds;

    @Value("${sandbox.max-concurrent:3}")
    private int maxConcurrent;

    @Value("${sandbox.queue-capacity:10}")
    private int queueCapacity;

    @Value("${sandbox.cpu-load-threshold:0.90}")
    private double cpuLoadThreshold;

    @Value("${sandbox.free-memory-threshold:0.10}")
    private double freeMemoryThreshold;

    // ── Watchdog config ───────────────────────────────────────────────────────

    @Value("${sandbox.watchdog.enabled:true}")
    private boolean watchdogEnabled;

    /** How often the watchdog polls docker stats (seconds). */
    @Value("${sandbox.watchdog.interval-seconds:10}")
    private int watchdogIntervalSeconds;

    /** Kill a container whose CPU usage exceeds this percentage (0–100). */
    @Value("${sandbox.watchdog.cpu-percent-limit:80.0}")
    private double watchdogCpuLimit;

    /** Kill a container whose memory usage exceeds this percentage of its limit (0–100). */
    @Value("${sandbox.watchdog.memory-percent-limit:90.0}")
    private double watchdogMemoryLimit;

    // ── Runtime state ─────────────────────────────────────────────────────────

    private static final String SLOTS_SEMAPHORE_KEY      = "sandbox:slots";
    private static final String ACTIVE_CONTAINERS_KEY    = "sandbox:active-containers";
    private static final String KILLED_CONTAINERS_KEY    = "sandbox:killed-containers";
    private static final String SUSPENDED_CONTAINERS_KEY = "sandbox:suspended-containers";

    /** Cluster-wide concurrency cap — every backend instance shares the same Redis-backed permit pool. */
    private RSemaphore             slots;
    private BlockingQueue<String>  waitQueue;
    private final AtomicInteger    active  = new AtomicInteger(0);
    private final AtomicInteger    queued  = new AtomicInteger(0);

    /**
     * Container ids already torn down — guards destroySandbox() against being called twice
     * for the same container (cancelRun() calls it directly, then the interrupted worker
     * thread's own finally block calls it again once it unwinds), which would otherwise
     * double-release its concurrency slot. In-process only, matching this run's other
     * per-container coordination (active/queued counters) rather than the cluster-wide slots.
     */
    private final Set<String> destroyedContainers = ConcurrentHashMap.newKeySet();

    private ScheduledExecutorService watchdogExecutor;

    @PostConstruct
    void init() {
        slots = redisson.getSemaphore(SLOTS_SEMAPHORE_KEY);
        // trySetPermits is a no-op if another instance already initialized the pool — that's
        // intentional, otherwise every instance restart would reset permits out from under active runs.
        slots.trySetPermits(maxConcurrent);
        waitQueue = new ArrayBlockingQueue<>(queueCapacity);
        log.info("[Sandbox] max-concurrent={} queue-capacity={}", maxConcurrent, queueCapacity);

        if (watchdogEnabled) {
            watchdogExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "sandbox-watchdog");
                t.setDaemon(true);
                return t;
            });
            watchdogExecutor.scheduleAtFixedRate(
                    this::checkContainerResources,
                    watchdogIntervalSeconds, watchdogIntervalSeconds, TimeUnit.SECONDS);
            log.info("[Sandbox] Watchdog started — interval={}s cpu-limit={}% mem-limit={}%",
                    watchdogIntervalSeconds, watchdogCpuLimit, watchdogMemoryLimit);
        }
    }

    @PreDestroy
    void shutdown() {
        if (watchdogExecutor != null) {
            watchdogExecutor.shutdownNow();
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    @Override
    public String createSandbox(String runId, Consumer<String> logger) {
        return doCreate(runId, false, logger);
    }

    @Override
    public String createSandboxWithNetwork(String runId, Consumer<String> logger) {
        return doCreate(runId, true, logger);
    }

    private String doCreate(String runId, boolean withNetwork, Consumer<String> logger) {
        if (!enabled) {
            log.info("[Sandbox] Disabled — skipping container for run {}", runId);
            logger.accept("Sandbox disabled — running without isolation.");
            return null;
        }

        logger.accept("Checking host resources…");
        checkSystemResources(runId);

        if (!waitQueue.offer(runId)) {
            throw new SandboxQueueFullException(
                    "Sandbox queue full (capacity=" + queueCapacity + "). Retry later.");
        }
        queued.incrementAndGet();

        if (active.get() >= maxConcurrent) {
            logger.accept("Waiting for a free sandbox slot (active=" + active.get()
                    + "/" + maxConcurrent + ", queued=" + queued.get() + ")…");
        }
        log.info("[Sandbox] run {} queued — active={} queued={}", runId, active.get(), queued.get());

        try {
            slots.acquire();
        } catch (InterruptedException e) {
            waitQueue.remove(runId);
            queued.decrementAndGet();
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for sandbox slot", e);
        }

        waitQueue.remove(runId);
        queued.decrementAndGet();
        active.incrementAndGet();
        log.info("[Sandbox] Slot acquired for run {} — active={} queued={}", runId, active.get(), queued.get());
        logger.accept("Slot acquired (active=" + active.get() + "/" + maxConcurrent + "). Spawning container…");

        try {
            String containerId = spawnContainer(runId, withNetwork, logger);
            redisTemplate.<String, String>opsForHash().put(ACTIVE_CONTAINERS_KEY, containerId, runId);
            return containerId;
        } catch (Exception e) {
            slots.release();
            active.decrementAndGet();
            log.error("[Sandbox] Container creation failed for run {}: {}", runId, e.getMessage());
            throw new SandboxStartupException("Sandbox failed to start: " + e.getMessage());
        }
    }

    /**
     * Executes a shell command inside the container.
     *
     * @throws SandboxKilledException if the watchdog terminated this container
     *         due to excessive resource usage — callers should let this propagate
     *         to fail the workflow run.
     */
    @Override
    public String exec(String containerId, String command) {
        if (containerId == null || containerId.isBlank()) {
            return "[Sandbox unavailable — Docker not running or sandbox disabled]";
        }

        String killReason = redisTemplate.<String, String>opsForHash().get(KILLED_CONTAINERS_KEY, containerId);
        if (killReason != null) {
            throw new SandboxKilledException(
                    "Sandbox forcibly terminated due to excessive resource usage: " + killReason);
        }

        try {
            String output = runProcess(List.of("docker", "exec", containerId, "sh", "-c", command),
                    execTimeoutSeconds);
            log.debug("[Sandbox] exec in {}: cmd='{}' output_len={}",
                    shortId(containerId), truncate(command, 80), output.length());
            return output.isBlank() ? "(no output)" : output;
        } catch (Exception e) {
            log.warn("[Sandbox] exec failed: {}", e.getMessage());
            return "[Error executing command: " + e.getMessage() + "]";
        }
    }

    /**
     * Writes bytes to a file at the given absolute path inside the container,
     * creating parent directories as needed. No-op if the container is unavailable.
     */
    @Override
    public void writeFile(String containerId, String path, byte[] content) {
        if (containerId == null || containerId.isBlank()) return;

        Path tmp = null;
        try {
            tmp = Files.createTempFile("sandbox-upload-", ".bin");
            Files.write(tmp, content);

            int lastSlash = path.lastIndexOf('/');
            if (lastSlash > 0) {
                runProcess(List.of("docker", "exec", containerId, "mkdir", "-p", path.substring(0, lastSlash)), 10);
            }
            runProcess(List.of("docker", "cp", tmp.toString(), containerId + ":" + path), 30);
            log.debug("[Sandbox] Wrote {} bytes to {}:{}", content.length, shortId(containerId), path);
        } catch (Exception e) {
            log.warn("[Sandbox] writeFile failed for {}:{} — {}", shortId(containerId), path, e.getMessage());
            throw new RuntimeException("Failed to write file into sandbox: " + e.getMessage(), e);
        } finally {
            if (tmp != null) {
                try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
            }
        }
    }

    /**
     * Wipes /workspace and kills background processes so the container can be
     * reused for a chained task without leaving stale state.
     */
    @Override
    public void recycleSandbox(String containerId) {
        if (containerId == null || containerId.isBlank()) return;
        try {
            exec(containerId, "pkill -9 -P 1 2>/dev/null || true");
            exec(containerId, "rm -rf /workspace/* /workspace/.[!.]* 2>/dev/null || true");
            log.info("[Sandbox] Recycled container {} — workspace cleaned", shortId(containerId));
        } catch (SandboxKilledException e) {
            throw e; // let it propagate
        } catch (Exception e) {
            log.warn("[Sandbox] Recycle failed for {}: {}", containerId, e.getMessage());
        }
    }

    /**
     * Stops and removes the container, then releases the concurrency slot.
     * Skips the docker rm if the watchdog already removed it, and skips the slot
     * release if it was already released by {@link #suspend} (avoids double-releasing
     * a permit if a suspended run is later cancelled/deleted).
     *
     * Idempotent — callers can call this twice for the same container (e.g. cancelRun()
     * destroys it directly while the interrupted worker thread's own finally block does
     * the same once it unwinds) without double-releasing the concurrency slot.
     */
    @Override
    public void destroySandbox(String containerId) {
        if (containerId == null || containerId.isBlank()) return;

        if (!destroyedContainers.add(containerId)) {
            log.debug("[Sandbox] destroySandbox() called again for {} — already destroyed, skipping", shortId(containerId));
            return;
        }

        redisTemplate.opsForHash().delete(ACTIVE_CONTAINERS_KEY, containerId);
        Long killedRemoved = redisTemplate.opsForHash().delete(KILLED_CONTAINERS_KEY, containerId);
        boolean wasKilledByWatchdog = killedRemoved != null && killedRemoved > 0;
        Long suspendedRemoved = redisTemplate.opsForHash().delete(SUSPENDED_CONTAINERS_KEY, containerId);
        boolean wasSuspended = suspendedRemoved != null && suspendedRemoved > 0;
        if (!wasKilledByWatchdog) {
            try {
                runProcess(List.of("docker", "rm", "-f", containerId), 15);
                log.info("[Sandbox] Destroyed container {}", shortId(containerId));
            } catch (Exception e) {
                log.warn("[Sandbox] Could not destroy container {}: {}", containerId, e.getMessage());
            }
        }
        if (wasSuspended) {
            log.info("[Sandbox] Destroyed suspended container {} — slot was already released, skipping", shortId(containerId));
            return;
        }
        slots.release();
        active.decrementAndGet();
        log.info("[Sandbox] Slot released — active={} queued={}", active.get(), queued.get());
    }

    /**
     * Docker-stops (not removes) the container and releases its concurrency slot so other
     * runs can use it — /workspace and the container object are kept intact for resume().
     */
    @Override
    public void suspend(String containerId) {
        if (containerId == null || containerId.isBlank()) return;
        try {
            runProcess(List.of("docker", "stop", "-t", "5", containerId), 15);
            log.info("[Sandbox] Suspended (stopped) container {}", shortId(containerId));
        } catch (Exception e) {
            log.warn("[Sandbox] Failed to stop container {} on suspend: {}", shortId(containerId), e.getMessage());
        }
        // Drop it from active tracking so the watchdog stops polling a stopped container.
        redisTemplate.opsForHash().delete(ACTIVE_CONTAINERS_KEY, containerId);
        redisTemplate.<String, String>opsForHash().put(SUSPENDED_CONTAINERS_KEY, containerId, "1");
        slots.release();
        active.decrementAndGet();
        log.info("[Sandbox] Slot released for suspended container {} — active={} queued={}",
                shortId(containerId), active.get(), queued.get());
    }

    /**
     * Reverses suspend(): re-acquires a concurrency slot (blocks if all slots are taken)
     * and docker-starts the container again.
     */
    @Override
    public void resume(String runId, String containerId) {
        if (containerId == null || containerId.isBlank()) return;
        Long removed = redisTemplate.opsForHash().delete(SUSPENDED_CONTAINERS_KEY, containerId);
        if (removed == null || removed == 0) {
            log.warn("[Sandbox] resume() called for container {} that isn't marked suspended — ignoring",
                    shortId(containerId));
            return;
        }
        try {
            slots.acquire();
        } catch (InterruptedException e) {
            redisTemplate.<String, String>opsForHash().put(SUSPENDED_CONTAINERS_KEY, containerId, "1");
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for a sandbox slot to resume", e);
        }
        active.incrementAndGet();
        try {
            runProcess(List.of("docker", "start", containerId), 30);
            redisTemplate.<String, String>opsForHash().put(ACTIVE_CONTAINERS_KEY, containerId, runId);
            log.info("[Sandbox] Resumed container {} for run {} — active={} queued={}",
                    shortId(containerId), runId, active.get(), queued.get());
        } catch (Exception e) {
            slots.release();
            active.decrementAndGet();
            redisTemplate.<String, String>opsForHash().put(SUSPENDED_CONTAINERS_KEY, containerId, "1");
            throw new RuntimeException("Failed to resume sandbox: " + e.getMessage(), e);
        }
    }

    // ── Watchdog ──────────────────────────────────────────────────────────────

    private void checkContainerResources() {
        Set<String> containerIds = redisTemplate.<String, String>opsForHash().keys(ACTIVE_CONTAINERS_KEY);
        if (containerIds.isEmpty()) return;

        for (String containerId : containerIds) {
            if (redisTemplate.opsForHash().hasKey(KILLED_CONTAINERS_KEY, containerId)) continue;

            try {
                String stats = runProcess(
                        List.of("docker", "stats", "--no-stream",
                                "--format", "{{.CPUPerc}}\t{{.MemPerc}}", containerId),
                        10);

                if (stats.isBlank()) continue;

                String[] parts = stats.strip().split("\t");
                if (parts.length < 2) continue;

                double cpu = parsePercent(parts[0]);
                double mem = parsePercent(parts[1]);

                log.debug("[Watchdog] Container {} — cpu={}% mem={}%", shortId(containerId), cpu, mem);

                if (cpu > watchdogCpuLimit) {
                    killByWatchdog(containerId,
                            "CPU %.1f%% exceeded limit %.1f%%".formatted(cpu, watchdogCpuLimit));
                } else if (mem > watchdogMemoryLimit) {
                    killByWatchdog(containerId,
                            "memory %.1f%% exceeded limit %.1f%%".formatted(mem, watchdogMemoryLimit));
                }
            } catch (Exception e) {
                log.debug("[Watchdog] Could not check container {}: {}", shortId(containerId), e.getMessage());
            }
        }
    }

    private void killByWatchdog(String containerId, String reason) {
        log.warn("[Watchdog] Killing container {} — {}", shortId(containerId), reason);
        redisTemplate.opsForHash().put(KILLED_CONTAINERS_KEY, containerId, reason);
        try {
            runProcess(List.of("docker", "rm", "-f", containerId), 10);
        } catch (Exception e) {
            log.warn("[Watchdog] Failed to remove container {}: {}", shortId(containerId), e.getMessage());
        }
    }

    // ── Status / observability ────────────────────────────────────────────────

    @Override
    public SandboxStatus status() {
        return new SandboxStatus(maxConcurrent, active.get(), queued.get(), queueCapacity);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void checkSystemResources(String runId) {
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();

        if (os instanceof com.sun.management.OperatingSystemMXBean sunOs) {
            double cpuLoad = sunOs.getCpuLoad();
            if (cpuLoad >= 0 && cpuLoad > cpuLoadThreshold) {
                throw new SandboxResourceException(
                        "Host CPU load %.0f%% exceeds threshold %.0f%% — try again later"
                                .formatted(cpuLoad * 100, cpuLoadThreshold * 100));
            }

            long totalPhys = sunOs.getTotalMemorySize();
            if (totalPhys > 0) {
                // On Linux, MemFree is near-zero because the kernel fills it with buffer/cache.
                // MemAvailable from /proc/meminfo is the correct metric — it includes reclaimable
                // cache and is what the kernel itself uses to judge whether a new process can start.
                long availableBytes = readMemAvailableBytes();
                if (availableBytes < 0) {
                    // Non-Linux fallback: use getFreeMemorySize()
                    availableBytes = sunOs.getFreeMemorySize();
                }
                double availRatio = (double) availableBytes / totalPhys;
                if (availRatio < freeMemoryThreshold) {
                    throw new SandboxResourceException(
                            "Host available memory %.0f%% below threshold %.0f%% — try again later"
                                    .formatted(availRatio * 100, freeMemoryThreshold * 100));
                }
                log.debug("[Sandbox] Memory check passed — available={} MiB ({}%)",
                        availableBytes / (1024 * 1024), (int)(availRatio * 100));
            }
        }
    }

    /**
     * Reads MemAvailable from /proc/meminfo (Linux only).
     * Returns bytes, or -1 if the file is unavailable (non-Linux or permission error).
     *
     * MemAvailable accounts for reclaimable buffer/cache and is the correct metric
     * for "can we start a new process?" — unlike MemFree which ignores the cache.
     */
    private long readMemAvailableBytes() {
        try {
            for (String line : Files.readAllLines(Path.of("/proc/meminfo"))) {
                if (line.startsWith("MemAvailable:")) {
                    String[] parts = line.trim().split("\\s+");
                    return Long.parseLong(parts[1]) * 1024L; // kB → bytes
                }
            }
        } catch (Exception e) {
            log.debug("[Sandbox] /proc/meminfo unavailable: {}", e.getMessage());
        }
        return -1;
    }

    private String spawnContainer(String runId, boolean withNetwork, Consumer<String> logger)
            throws IOException, InterruptedException {
        String shortRun = runId.substring(0, 8);
        String name = withNetwork ? "ragagent-net-" + shortRun : "ragagent-sandbox-" + shortRun;
        List<String> cmd = withNetwork
                ? List.of("docker", "run", "-d", "--rm",
                          "--name", name,
                          "--memory", memoryLimit,
                          "--cpu-quota", cpuQuota,
                          "--workdir", "/workspace",
                          sandboxImage, "tail", "-f", "/dev/null")
                : List.of("docker", "run", "-d", "--rm",
                          "--name", name,
                          "--memory", memoryLimit,
                          "--cpu-quota", cpuQuota,
                          "--network", "none",
                          "--workdir", "/workspace",
                          sandboxImage, "tail", "-f", "/dev/null");

        logger.accept("docker run " + sandboxImage
                + " [mem=" + memoryLimit + ", cpu-quota=" + cpuQuota
                + ", network=" + (withNetwork ? "bridge" : "none") + "]");

        String rawOutput = runProcess(cmd, 30);
        log.debug("[Sandbox] docker run output for run {}: {}", runId, truncate(rawOutput.strip(), 300));

        // docker run -d outputs the 64-char container ID as the last non-empty line.
        // Preceding lines may be warnings (e.g., platform mismatch). We find the last
        // line that looks like a hex container ID (≥12 chars, no whitespace).
        String containerId = rawOutput.lines()
                .map(String::strip)
                .filter(l -> !l.isBlank() && l.length() >= 12 && l.matches("[0-9a-f]+"))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new RuntimeException(
                        "docker run produced no valid container ID. Output: " + truncate(rawOutput.strip(), 300)));

        logger.accept("Container ready: " + shortId(containerId)
                + " [image=" + sandboxImage + ", name=" + name + "]");
        log.info("[Sandbox] Created container {} for run {} (network={})",
                shortId(containerId), runId, withNetwork);
        return containerId;
    }

    private String runProcess(List<String> cmd, int timeoutSeconds) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start();

        // Read output on a virtual thread so the readLine() loop cannot block
        // the caller forever if the subprocess stalls without closing stdout
        // (e.g., docker hanging while trying to reach an inaccessible daemon).
        // destroyForcibly() closes the stream and unblocks the reader.
        StringBuilder output = new StringBuilder();
        Thread readerThread = Thread.ofVirtual().start(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) output.append(line).append("\n");
            } catch (IOException ignored) {}
        });

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            readerThread.interrupt();
            throw new RuntimeException("Command timed out after " + timeoutSeconds + "s: " + cmd.get(0));
        }

        readerThread.join(2000); // drain any remaining buffered output
        return output.toString();
    }

    private double parsePercent(String s) {
        try {
            return Double.parseDouble(s.strip().replace("%", ""));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private String shortId(String id) {
        return id.length() > 12 ? id.substring(0, 12) : id;
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
