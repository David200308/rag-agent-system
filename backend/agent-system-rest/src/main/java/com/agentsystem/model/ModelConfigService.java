package com.agentsystem.model;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ModelConfigService {

    private final ModelConfigRepository repo;

    public List<ModelConfig> listEnabled() {
        return repo.findByEnabledTrue();
    }

    public List<ModelConfig> listAll() {
        return repo.findAll();
    }

    public Optional<ModelConfig> findByDisplayName(String displayName) {
        return repo.findById(displayName);
    }

    @Transactional
    public ModelConfig create(String displayName, String platform, String modelId) {
        if (repo.existsById(displayName)) {
            throw new IllegalArgumentException("Model config already exists: " + displayName);
        }
        return repo.save(new ModelConfig(displayName, platform, modelId));
    }

    @Transactional
    public ModelConfig update(String displayName, String platform, String modelId, boolean enabled) {
        ModelConfig config = repo.findById(displayName)
                .orElseThrow(() -> new IllegalArgumentException("Model config not found: " + displayName));
        config.setPlatform(platform);
        config.setModelId(modelId);
        config.setEnabled(enabled);
        return repo.save(config);
    }

    @Transactional
    public void delete(String displayName) {
        if (!repo.existsById(displayName)) {
            throw new IllegalArgumentException("Model config not found: " + displayName);
        }
        repo.deleteById(displayName);
    }
}
