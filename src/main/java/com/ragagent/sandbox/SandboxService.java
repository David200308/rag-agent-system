package com.ragagent.sandbox;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
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
 * Callers receive a SandboxTicket. When the ticket's acquire() returns, a slot
 * is guaranteed and the container can be created. The ticket must be released
 * (via destroySandbox) to free the slot for the next queued run.
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

    /** Maximum simultaneous sandbox containers. */
    @Value("${sandbox.max-concurrent:3}")
    private int maxConcurrent;

    /** Maximum runs that can wait in the queue. Excess runs are rejected. */
    @Value("${sandbox.queue-capacity:10}")
    private int queueCapacity;

    /** Reject new runs if system CPU load exceeds this fraction (0.0–1.0). */
    @Value("${sandbox.cpu-load-threshold:0.90}")
    private double cpuLoadThreshold;

    /** Reject new runs if free physical memory falls below this fraction (0.0–1.0). */
    @Value("${sandbox.free-memory-threshold:0.10}")
    private double freeMemoryThreshold;

    // ── Resource governance ───────────────────────────────────────────────────

    private Semaphore              slots;
    private BlockingQueue<String>  waitQueue;   // queued runIds (for observability)
    private final AtomicInteger    active  = new AtomicInteger(0);
    private final AtomicInteger    queued  = new AtomicInteger(0);

    @PostConstruct
    void init() {
        slots     = new Semaphore(maxConcurrent, true);   // fair
        waitQueue = new ArrayBlockingQueue<>(queueCapacity);
        log.info("[Sandbox] max-concurrent={} queue-capacity={}", maxConcurrent, queueCapacity);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Acquires a sandbox slot (blocking if at capacity, bounded by queue limit),
     * then creates and starts a Docker container. Returns the container ID,
     * or null if sandbox is disabled / Docker unavailable.
     *
     * Throws {@link SandboxQueueFullException} if the queue is already full.
     * Throws {@link SandboxResourceException} if host resources are too strained.
     */
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
            // Block until a slot is available (fair ordering via semaphore)
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
            return spawnContainer(runId, withNetwork);
        } catch (Exception e) {
            slots.release();
            active.decrementAndGet();
            log.warn("[Sandbox] Container creation failed for run {}: {}", runId, e.getMessage());
            return null;
        }
    }

    /**
     * Executes a shell command inside the container. Returns combined stdout+stderr.
     * Returns an informative message if the container is unavailable.
     */
    public String exec(String containerId, String command) {
        if (containerId == null || containerId.isBlank()) {
            return "[Sandbox unavailable — Docker not running or sandbox disabled]";
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
     * Recycles the sandbox workspace after a workflow task completes:
     * wipes /workspace and kills any stray background processes.
     * The container stays alive so the same slot can be reused for a chained task.
     * Call destroySandbox() when the entire run is done.
     */
    public void recycleSandbox(String containerId) {
        if (containerId == null || containerId.isBlank()) return;
        try {
            // Kill any background processes started by agent tool calls
            exec(containerId, "pkill -9 -P 1 2>/dev/null || true");
            // Wipe workspace contents (not the directory itself)
            exec(containerId, "rm -rf /workspace/* /workspace/.[!.]* 2>/dev/null || true");
            log.info("[Sandbox] Recycled container {} — workspace cleaned", shortId(containerId));
        } catch (Exception e) {
            log.warn("[Sandbox] Recycle failed for {}: {}", containerId, e.getMessage());
        }
    }

    /**
     * Stops and removes the container, then releases the concurrency slot.
     * No-op if containerId is null.
     */
    public void destroySandbox(String containerId) {
        if (containerId == null || containerId.isBlank()) return;
        try {
            runProcess(List.of("docker", "rm", "-f", containerId), 15);
            log.info("[Sandbox] Destroyed container {}", shortId(containerId));
        } catch (Exception e) {
            log.warn("[Sandbox] Could not destroy container {}: {}", containerId, e.getMessage());
        } finally {
            slots.release();
            active.decrementAndGet();
            log.info("[Sandbox] Slot released — active={} queued={}", active.get(), queued.get());
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
}
