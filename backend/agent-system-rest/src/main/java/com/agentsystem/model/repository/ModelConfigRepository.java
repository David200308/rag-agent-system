package com.agentsystem.model.repository;

import com.agentsystem.model.entity.ModelConfig;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModelConfigRepository extends JpaRepository<ModelConfig, String> {
    List<ModelConfig> findByEnabledTrue();
}
