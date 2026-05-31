package com.ragagent.sandbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SandboxServiceTest {

    SandboxService sandboxService;

    @BeforeEach
    void setUp() {
        sandboxService = new SandboxService();
        // Set @Value fields that status() reads
        ReflectionTestUtils.setField(sandboxService, "maxConcurrent", 3);
        ReflectionTestUtils.setField(sandboxService, "queueCapacity",  10);
        ReflectionTestUtils.setField(sandboxService, "enabled",        false); // skip Docker in tests
        ReflectionTestUtils.setField(sandboxService, "watchdogEnabled", false); // skip watchdog
        // Call @PostConstruct to initialise Semaphore and queue
        sandboxService.init();
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
    @SuppressWarnings("unchecked")
    void exec_killedContainer_throwsSandboxKilledException() {
        var killedContainers = (java.util.concurrent.ConcurrentHashMap<String, String>)
                ReflectionTestUtils.getField(sandboxService, "killedContainers");
        killedContainers.put("abc123def456", "CPU 95% exceeded limit 80%");

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
}
