package com.agentsystem.sandbox.service.impl;

import com.agentsystem.sandbox.entity.PersistentSandbox;
import com.agentsystem.sandbox.repository.PersistentSandboxRepository;
import com.agentsystem.sandbox.service.PersistentSandboxService;
import com.agentsystem.sandbox.service.SandboxService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class PersistentSandboxServiceImpl implements PersistentSandboxService {

    private final PersistentSandboxRepository sandboxRepo;
    private final SandboxService              sandboxService;

    @Value("${sandbox.persistent.max-per-user:1}")
    private int maxPerUser;

    @Override
    public List<PersistentSandbox> list(String ownerUuid) {
        return sandboxRepo.findByOwnerUuidOrderByCreatedAtDesc(ownerUuid);
    }

    @Override
    public Quota quota(String ownerUuid) {
        return new Quota(sandboxRepo.countByOwnerUuid(ownerUuid), maxPerUser);
    }

    @Transactional
    @Override
    public PersistentSandbox create(String ownerUuid, String name, boolean network) {
        if (sandboxRepo.countByOwnerUuid(ownerUuid) >= maxPerUser) {
            throw new IllegalStateException(
                    "Sandbox quota reached (" + maxPerUser + " per user). Remove an existing sandbox first.");
        }

        String id = UUID.randomUUID().toString();
        Consumer<String> logger = msg -> log.info("[PersistentSandbox {}] {}", shortId(id), msg);
        String containerId = network
                ? sandboxService.createSandboxWithNetwork(id, logger)
                : sandboxService.createSandbox(id, logger);

        PersistentSandbox sandbox = new PersistentSandbox(id, ownerUuid,
                (name == null || name.isBlank()) ? "sandbox-" + shortId(id) : name, network);
        sandbox.setContainerId(containerId);
        sandbox.setStatus(PersistentSandbox.Status.RUNNING);
        return sandboxRepo.save(sandbox);
    }

    @Transactional
    @Override
    public PersistentSandbox stop(String id, String ownerUuid) {
        PersistentSandbox sandbox = require(id, ownerUuid);
        if (sandbox.getStatus() == PersistentSandbox.Status.STOPPED) return sandbox;
        sandboxService.suspend(sandbox.getContainerId());
        sandbox.setStatus(PersistentSandbox.Status.STOPPED);
        return sandboxRepo.save(sandbox);
    }

    @Transactional
    @Override
    public PersistentSandbox restart(String id, String ownerUuid) {
        PersistentSandbox sandbox = require(id, ownerUuid);
        if (sandbox.getStatus() == PersistentSandbox.Status.RUNNING) {
            sandboxService.suspend(sandbox.getContainerId());
        }
        sandboxService.resume(id, sandbox.getContainerId());
        sandbox.setStatus(PersistentSandbox.Status.RUNNING);
        return sandboxRepo.save(sandbox);
    }

    @Override
    public void clear(String id, String ownerUuid) {
        PersistentSandbox sandbox = require(id, ownerUuid);
        if (sandbox.getStatus() != PersistentSandbox.Status.RUNNING) {
            throw new IllegalStateException("Sandbox must be running to clear its workspace.");
        }
        sandboxService.recycleSandbox(sandbox.getContainerId());
    }

    @Transactional
    @Override
    public void remove(String id, String ownerUuid) {
        PersistentSandbox sandbox = require(id, ownerUuid);
        sandboxService.destroySandbox(sandbox.getContainerId());
        sandboxRepo.delete(sandbox);
    }

    @Transactional
    @Override
    public PersistentSandbox acquireForRun(String id, String ownerUuid, Consumer<String> logger) {
        PersistentSandbox sandbox = sandboxRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Sandbox not found: " + id));
        if (!sandbox.getOwnerUuid().equals(ownerUuid)) {
            throw new SecurityException("Not the owner of the attached sandbox.");
        }
        if (sandbox.getStatus() == PersistentSandbox.Status.STOPPED) {
            logger.accept("Resuming attached persistent sandbox " + sandbox.getName() + "…");
            sandboxService.resume(id, sandbox.getContainerId());
            sandbox.setStatus(PersistentSandbox.Status.RUNNING);
            sandbox = sandboxRepo.save(sandbox);
        }
        return sandbox;
    }

    private PersistentSandbox require(String id, String ownerUuid) {
        PersistentSandbox sandbox = sandboxRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Sandbox not found: " + id));
        if (!sandbox.getOwnerUuid().equals(ownerUuid)) {
            throw new SecurityException("Only the owner can manage this sandbox.");
        }
        return sandbox;
    }

    private String shortId(String id) {
        return id.length() > 8 ? id.substring(0, 8) : id;
    }
}
