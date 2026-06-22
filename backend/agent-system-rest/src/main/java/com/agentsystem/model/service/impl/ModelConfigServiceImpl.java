package com.agentsystem.model.service.impl;

import com.agentsystem.model.service.ModelConfigService;

import com.agentsystem.model.entity.ModelConfig;
import com.agentsystem.model.repository.ModelConfigRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ModelConfigServiceImpl implements ModelConfigService {

    private final ModelConfigRepository repo;

    @Override
    public List<ModelConfig> listEnabled() {
        return repo.findByEnabledTrue();
    }

    @Override
    public List<ModelConfig> listAll() {
        return repo.findAll();
    }

    @Override
    public Optional<ModelConfig> findByDisplayName(String displayName) {
        return repo.findById(displayName);
    }

    @Override
    @Transactional
    public ModelConfig create(String displayName, String platform, String modelId) {
        if (repo.existsById(displayName)) {
            throw new IllegalArgumentException("Model config already exists: " + displayName);
        }
        return repo.save(new ModelConfig(displayName, platform, modelId));
    }

    @Override
    @Transactional
    public ModelConfig update(String displayName, String platform, String modelId, boolean enabled) {
        ModelConfig config = repo.findById(displayName)
                .orElseThrow(() -> new IllegalArgumentException("Model config not found: " + displayName));
        config.setPlatform(platform);
        config.setModelId(modelId);
        config.setEnabled(enabled);
        return repo.save(config);
    }

    @Override
    @Transactional
    public void delete(String displayName) {
        if (!repo.existsById(displayName)) {
            throw new IllegalArgumentException("Model config not found: " + displayName);
        }
        repo.deleteById(displayName);
    }
}
