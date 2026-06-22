package com.agentsystem.model.service;

import com.agentsystem.model.service.impl.ModelConfigServiceImpl;

import com.agentsystem.model.entity.ModelConfig;
import com.agentsystem.model.repository.ModelConfigRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModelConfigServiceTest {

    @Mock ModelConfigRepository repo;

    @InjectMocks ModelConfigServiceImpl service;

    // ── listEnabled ───────────────────────────────────────────────────────────

    @Test
    void listEnabled_returnsOnlyEnabledConfigs() {
        ModelConfig enabled = new ModelConfig("GPT-4o", "openai", "gpt-4o");
        when(repo.findByEnabledTrue()).thenReturn(List.of(enabled));

        List<ModelConfig> result = service.listEnabled();

        assertThat(result).containsExactly(enabled);
    }

    @Test
    void listEnabled_noConfigs_returnsEmptyList() {
        when(repo.findByEnabledTrue()).thenReturn(List.of());

        assertThat(service.listEnabled()).isEmpty();
    }

    // ── listAll ───────────────────────────────────────────────────────────────

    @Test
    void listAll_returnsAllIncludingDisabled() {
        ModelConfig enabled  = new ModelConfig("GPT-4o",   "openai",    "gpt-4o");
        ModelConfig disabled = new ModelConfig("GPT-3.5",  "openai",    "gpt-3.5-turbo");
        disabled.setEnabled(false);
        when(repo.findAll()).thenReturn(List.of(enabled, disabled));

        assertThat(service.listAll()).hasSize(2);
    }

    // ── findByDisplayName ─────────────────────────────────────────────────────

    @Test
    void findByDisplayName_existing_returnsConfig() {
        ModelConfig config = new ModelConfig("Claude-Sonnet", "anthropic", "claude-sonnet-4-6");
        when(repo.findById("Claude-Sonnet")).thenReturn(Optional.of(config));

        assertThat(service.findByDisplayName("Claude-Sonnet")).contains(config);
    }

    @Test
    void findByDisplayName_missing_returnsEmpty() {
        when(repo.findById("Ghost")).thenReturn(Optional.empty());

        assertThat(service.findByDisplayName("Ghost")).isEmpty();
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_savesNewConfig() {
        when(repo.existsById("DeepSeek-R1")).thenReturn(false);
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        ArgumentCaptor<ModelConfig> captor = ArgumentCaptor.forClass(ModelConfig.class);

        service.create("DeepSeek-R1", "deepseek", "deepseek-reasoner");

        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getDisplayName()).isEqualTo("DeepSeek-R1");
        assertThat(captor.getValue().getPlatform()).isEqualTo("deepseek");
        assertThat(captor.getValue().getModelId()).isEqualTo("deepseek-reasoner");
        assertThat(captor.getValue().isEnabled()).isTrue();
    }

    @Test
    void create_duplicateDisplayName_throwsIllegalArgument() {
        when(repo.existsById("GPT-4o")).thenReturn(true);

        assertThatThrownBy(() -> service.create("GPT-4o", "openai", "gpt-4o"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    void update_changesAllFields() {
        ModelConfig config = new ModelConfig("My Model", "openai", "gpt-4o-mini");
        when(repo.findById("My Model")).thenReturn(Optional.of(config));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        ModelConfig result = service.update("My Model", "anthropic", "claude-opus-4-7", false);

        assertThat(result.getPlatform()).isEqualTo("anthropic");
        assertThat(result.getModelId()).isEqualTo("claude-opus-4-7");
        assertThat(result.isEnabled()).isFalse();
    }

    @Test
    void update_notFound_throwsIllegalArgument() {
        when(repo.findById("Ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("Ghost", "openai", "gpt-4o", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_existingConfig_removesIt() {
        when(repo.existsById("GPT-4o")).thenReturn(true);

        service.delete("GPT-4o");

        verify(repo).deleteById("GPT-4o");
    }

    @Test
    void delete_notFound_throwsIllegalArgument() {
        when(repo.existsById("Ghost")).thenReturn(false);

        assertThatThrownBy(() -> service.delete("Ghost"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");

        verify(repo, never()).deleteById(any());
    }

    // ── enabled flag ──────────────────────────────────────────────────────────

    @Test
    void create_newConfigIsEnabledByDefault() {
        when(repo.existsById("New Model")).thenReturn(false);
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        ModelConfig result = service.create("New Model", "openrouter", "openai/gpt-4o");

        assertThat(result.isEnabled()).isTrue();
    }

    @Test
    void update_canDisableAndReenable() {
        ModelConfig config = new ModelConfig("My Model", "openai", "gpt-4o");
        when(repo.findById("My Model")).thenReturn(Optional.of(config));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        ModelConfig disabled = service.update("My Model", "openai", "gpt-4o", false);
        assertThat(disabled.isEnabled()).isFalse();

        ModelConfig reEnabled = service.update("My Model", "openai", "gpt-4o", true);
        assertThat(reEnabled.isEnabled()).isTrue();
    }
}
