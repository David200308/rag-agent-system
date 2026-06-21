package com.agentsystem.model;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModelConfigRepository extends JpaRepository<ModelConfig, String> {
    List<ModelConfig> findByEnabledTrue();
}
