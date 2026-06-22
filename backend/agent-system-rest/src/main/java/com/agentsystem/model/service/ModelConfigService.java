package com.agentsystem.model.service;

import com.agentsystem.model.entity.ModelConfig;

import java.util.List;
import java.util.Optional;

public interface ModelConfigService {

    List<ModelConfig> listEnabled();

    List<ModelConfig> listAll();

    Optional<ModelConfig> findByDisplayName(String displayName);

    ModelConfig create(String displayName, String platform, String modelId);

    ModelConfig update(String displayName, String platform, String modelId, boolean enabled);

    void delete(String displayName);
}
