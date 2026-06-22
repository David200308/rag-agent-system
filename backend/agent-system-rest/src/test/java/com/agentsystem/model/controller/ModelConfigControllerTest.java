package com.agentsystem.model.controller;

import com.agentsystem.model.entity.ModelConfig;
import com.agentsystem.model.service.ModelConfigService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModelConfigControllerTest {

    @Mock ModelConfigService service;
    @InjectMocks ModelConfigController controller;

    private ModelConfig model(String name) {
        ModelConfig m = new ModelConfig();
        m.setDisplayName(name);
        m.setPlatform("openai");
        m.setModelId("gpt-4");
        m.setEnabled(true);
        return m;
    }

    // ── listEnabled ───────────────────────────────────────────────────────────

    @Test
    void listEnabled_returnsEnabledModels() {
        when(service.listEnabled()).thenReturn(List.of(model("GPT-4")));

        ResponseEntity<List<ModelConfig>> resp = controller.listEnabled();

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).hasSize(1);
        assertThat(resp.getBody().get(0).getDisplayName()).isEqualTo("GPT-4");
    }

    @Test
    void listEnabled_empty_returnsEmptyList() {
        when(service.listEnabled()).thenReturn(List.of());

        ResponseEntity<List<ModelConfig>> resp = controller.listEnabled();

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isEmpty();
    }

    // ── listAll ───────────────────────────────────────────────────────────────

    @Test
    void listAll_returnsAllModels() {
        when(service.listAll()).thenReturn(List.of(model("GPT-4"), model("Claude")));

        ResponseEntity<List<ModelConfig>> resp = controller.listAll();

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).hasSize(2);
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_validBody_returns200() {
        when(service.create("GPT-4", "openai", "gpt-4")).thenReturn(model("GPT-4"));

        var resp = controller.create(Map.of("displayName", "GPT-4", "platform", "openai", "modelId", "gpt-4"));

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void create_missingDisplayName_returns400() {
        var resp = controller.create(Map.of("platform", "openai", "modelId", "gpt-4"));

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void create_blankDisplayName_returns400() {
        var resp = controller.create(Map.of("displayName", "  ", "platform", "openai", "modelId", "gpt-4"));

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void create_missingPlatform_returns400() {
        var resp = controller.create(Map.of("displayName", "GPT-4", "modelId", "gpt-4"));

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void create_missingModelId_returns400() {
        var resp = controller.create(Map.of("displayName", "GPT-4", "platform", "openai"));

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void create_duplicate_returns400() {
        when(service.create(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalArgumentException("already exists"));

        var resp = controller.create(Map.of("displayName", "GPT-4", "platform", "openai", "modelId", "gpt-4"));

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    void update_validBody_returns200() {
        when(service.update("GPT-4", "openai", "gpt-4o", true)).thenReturn(model("GPT-4"));

        var resp = controller.update("GPT-4", Map.of("platform", "openai", "modelId", "gpt-4o", "enabled", true));

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void update_missingPlatform_returns400() {
        var resp = controller.update("GPT-4", Map.of("modelId", "gpt-4o"));

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void update_blankModelId_returns400() {
        var resp = controller.update("GPT-4", Map.of("platform", "openai", "modelId", ""));

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void update_notFound_returns404() {
        when(service.update(anyString(), anyString(), anyString(), anyBoolean()))
                .thenThrow(new IllegalArgumentException("not found"));

        var resp = controller.update("Unknown", Map.of("platform", "openai", "modelId", "gpt-4"));

        assertThat(resp.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void update_enabledDefaultsToTrue_whenNotProvided() {
        when(service.update("GPT-4", "openai", "gpt-4", true)).thenReturn(model("GPT-4"));

        // body has no "enabled" key → defaults to true
        var resp = controller.update("GPT-4", Map.of("platform", "openai", "modelId", "gpt-4"));

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        verify(service).update("GPT-4", "openai", "gpt-4", true);
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_success_returns204() {
        doNothing().when(service).delete("GPT-4");

        ResponseEntity<Void> resp = controller.delete("GPT-4");

        assertThat(resp.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void delete_notFound_returns404() {
        doThrow(new IllegalArgumentException("not found")).when(service).delete("Unknown");

        ResponseEntity<Void> resp = controller.delete("Unknown");

        assertThat(resp.getStatusCode().value()).isEqualTo(404);
    }
}
