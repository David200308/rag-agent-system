package com.ragagent.sandbox;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages ephemeral Docker sandbox containers for workflow runs.
 *
 * Resource governance:
 *  - sandbox.max-concurrent  caps how many containers run simultaneously (semaphore)
 *  - sandbox.queue-capacity  caps how many runs can wait in line before rejection
 *  - Admission check: refuses new sandboxes if system CPU or free memory
 *    exceed configured thresholds (fail-fast instead of overloading the host)
 *
 * Runtime watchdog:
 *  - Polls docker stats every sandbox.watchdog.interval-seconds seconds
 *  - Kills any container whose CPU% or memory% exceeds the configured limits
 *  - Killed containers cause the next exec() call to throw SandboxKilledException,
 *    which propagates up and fails the workflow run immediately
 */
@Slf4j
@Service
public class SandboxService {

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

    private Semaphore              slots;
    private BlockingQueue<String>  waitQueue;
    private final AtomicInteger    active  = new AtomicInteger(0);
    private final AtomicInteger    queued  = new AtomicInteger(0);

    /** containerId → runId for every live sandbox container. */
    private final ConcurrentHashMap<String, String> activeContainers = new ConcurrentHashMap<>();

    /** containerId → kill reason for containers the watchdog terminated. */
    private final ConcurrentHashMap<String, String> killedContainers = new ConcurrentHashMap<>();

    private ScheduledExecutorService watchdogExecutor;

    @PostConstruct
    void init() {
        slots     = new Semaphore(maxConcurrent, true);
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

    public String createSandbox(String runId) {
        return doCreate(runId, false);
    }

    public String createSandboxWithNetwork(String runId) {
        return doCreate(runId, true);
    }

    private String doCreate(String runId, boolean withNetwork) {
        if (!enabled) {
            log.info("[Sandbox] Disabled — skipping container for run {}", runId);
            return null;
        }

        checkSystemResources(runId);

        if (!waitQueue.offer(runId)) {
            throw new SandboxQueueFullException(
                    "Sandbox queue full (capacity=" + queueCapacity + "). Retry later.");
        }
        queued.incrementAndGet();
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

        try {
            String containerId = spawnContainer(runId, withNetwork);
            activeContainers.put(containerId, runId);
            return containerId;
        } catch (Exception e) {
            slots.release();
            active.decrementAndGet();
            log.warn("[Sandbox] Container creation failed for run {}: {}", runId, e.getMessage());
            return null;
        }
    }

    /**
     * Executes a shell command inside the container.
     *
     * @throws SandboxKilledException if the watchdog terminated this container
     *         due to excessive resource usage — callers should let this propagate
     *         to fail the workflow run.
     */
    public String exec(String containerId, String command) {
        if (containerId == null || containerId.isBlank()) {
            return "[Sandbox unavailable — Docker not running or sandbox disabled]";
        }

        String killReason = killedContainers.get(containerId);
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
     * Wipes /workspace and kills background processes so the container can be
     * reused for a chained task without leaving stale state.
     */
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
     * Skips the docker rm if the watchdog already removed it.
     */
    public void destroySandbox(String containerId) {
        if (containerId == null || containerId.isBlank()) return;
        activeContainers.remove(containerId);
        boolean wasKilledByWatchdog = killedContainers.remove(containerId) != null;
        if (!wasKilledByWatchdog) {
            try {
                runProcess(List.of("docker", "rm", "-f", containerId), 15);
                log.info("[Sandbox] Destroyed container {}", shortId(containerId));
            } catch (Exception e) {
                log.warn("[Sandbox] Could not destroy container {}: {}", containerId, e.getMessage());
            }
        }
        slots.release();
        active.decrementAndGet();
        log.info("[Sandbox] Slot released — active={} queued={}", active.get(), queued.get());
    }

    // ── Watchdog ──────────────────────────────────────────────────────────────

    private void checkContainerResources() {
        if (activeContainers.isEmpty()) return;

        for (String containerId : activeContainers.keySet()) {
            if (killedContainers.containsKey(containerId)) continue;

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
        killedContainers.put(containerId, reason);
        try {
            runProcess(List.of("docker", "rm", "-f", containerId), 10);
        } catch (Exception e) {
            log.warn("[Watchdog] Failed to remove container {}: {}", shortId(containerId), e.getMessage());
        }
    }

    // ── Status / observability ────────────────────────────────────────────────

    public SandboxStatus status() {
        return new SandboxStatus(maxConcurrent, active.get(), queued.get(), queueCapacity);
    }

    public record SandboxStatus(int maxConcurrent, int active, int queued, int queueCapacity) {
        public boolean atCapacity() { return active >= maxConcurrent; }
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

            long freePhys  = sunOs.getFreeMemorySize();
            long totalPhys = sunOs.getTotalMemorySize();
            if (totalPhys > 0) {
                double freeRatio = (double) freePhys / totalPhys;
                if (freeRatio < freeMemoryThreshold) {
                    throw new SandboxResourceException(
                            "Host free memory %.0f%% below threshold %.0f%% — try again later"
                                    .formatted(freeRatio * 100, freeMemoryThreshold * 100));
                }
            }
        }
    }

    private String spawnContainer(String runId, boolean withNetwork) throws IOException, InterruptedException {
        String shortRun = runId.substring(0, 8);
        List<String> cmd = withNetwork
                ? List.of("docker", "run", "-d", "--rm",
                          "--name", "ragagent-net-" + shortRun,
                          "--memory", memoryLimit,
                          "--cpu-quota", cpuQuota,
                          "--workdir", "/workspace",
                          sandboxImage, "tail", "-f", "/dev/null")
                : List.of("docker", "run", "-d", "--rm",
                          "--name", "ragagent-sandbox-" + shortRun,
                          "--memory", memoryLimit,
                          "--cpu-quota", cpuQuota,
                          "--network", "none",
                          "--workdir", "/workspace",
                          sandboxImage, "tail", "-f", "/dev/null");

        String containerId = runProcess(cmd, 30).strip();
        log.info("[Sandbox] Created container {} for run {} (network={})",
                shortId(containerId), runId, withNetwork);
        return containerId;
    }

    private String runProcess(List<String> cmd, int timeoutSeconds) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) output.append(line).append("\n");
        }

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Command timed out after " + timeoutSeconds + "s");
        }
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

    // ── Exception types ───────────────────────────────────────────────────────

    public static class SandboxQueueFullException extends RuntimeException {
        public SandboxQueueFullException(String msg) { super(msg); }
    }

    public static class SandboxResourceException extends RuntimeException {
        public SandboxResourceException(String msg) { super(msg); }
    }

    public static class SandboxKilledException extends RuntimeException {
        public SandboxKilledException(String msg) { super(msg); }
    }
}
