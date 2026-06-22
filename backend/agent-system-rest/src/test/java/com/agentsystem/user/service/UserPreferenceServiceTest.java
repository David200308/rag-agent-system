package com.agentsystem.user.service;

import com.agentsystem.user.service.impl.UserPreferenceServiceImpl;

import com.agentsystem.user.entity.UserPreference;
import com.agentsystem.user.repository.UserPreferenceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserPreferenceServiceTest {

    @Mock UserPreferenceRepository repo;

    @InjectMocks UserPreferenceServiceImpl service;

    // ── getOrDefault ──────────────────────────────────────────────────────────

    @Test
    void getOrDefault_existingPreference_returnsIt() {
        UserPreference pref = new UserPreference("user@test.com", "America/New_York");
        when(repo.findByEmail("user@test.com")).thenReturn(Optional.of(pref));

        UserPreference result = service.getOrDefault("user@test.com");

        assertThat(result.getTimezone()).isEqualTo("America/New_York");
    }

    @Test
    void getOrDefault_noPreference_returnsUtcDefault() {
        when(repo.findByEmail("user@test.com")).thenReturn(Optional.empty());

        UserPreference result = service.getOrDefault("user@test.com");

        assertThat(result.getTimezone()).isEqualTo("UTC");
        verify(repo, never()).save(any());
    }

    // ── setTimezone ───────────────────────────────────────────────────────────

    @Test
    void setTimezone_existingPreference_updatesTimezone() {
        UserPreference pref = new UserPreference("user@test.com", "UTC");
        when(repo.findByEmail("user@test.com")).thenReturn(Optional.of(pref));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        UserPreference result = service.setTimezone("user@test.com", "Asia/Tokyo");

        assertThat(result.getTimezone()).isEqualTo("Asia/Tokyo");
    }

    @Test
    void setTimezone_noExistingPreference_createsAndSaves() {
        when(repo.findByEmail("new@test.com")).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        ArgumentCaptor<UserPreference> captor = ArgumentCaptor.forClass(UserPreference.class);

        service.setTimezone("new@test.com", "Europe/London");

        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("new@test.com");
        assertThat(captor.getValue().getTimezone()).isEqualTo("Europe/London");
    }

    // ── setSelectedModel ──────────────────────────────────────────────────────

    @Test
    void setSelectedModel_savesDisplayName() {
        UserPreference pref = new UserPreference("user@test.com", "UTC");
        when(repo.findByEmail("user@test.com")).thenReturn(Optional.of(pref));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        UserPreference result = service.setSelectedModel("user@test.com", "GPT-4o");

        assertThat(result.getSelectedModel()).isEqualTo("GPT-4o");
    }

    @Test
    void setSelectedModel_nullValue_clearsModel() {
        UserPreference pref = new UserPreference("user@test.com", "UTC");
        pref.setSelectedModel("GPT-4o");
        when(repo.findByEmail("user@test.com")).thenReturn(Optional.of(pref));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        UserPreference result = service.setSelectedModel("user@test.com", null);

        assertThat(result.getSelectedModel()).isNull();
    }

    @Test
    void setSelectedModel_noExistingPreference_createsRecord() {
        when(repo.findByEmail("new@test.com")).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        ArgumentCaptor<UserPreference> captor = ArgumentCaptor.forClass(UserPreference.class);

        service.setSelectedModel("new@test.com", "Claude-Sonnet");

        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getSelectedModel()).isEqualTo("Claude-Sonnet");
    }

    // ── getSelectedModel ──────────────────────────────────────────────────────

    @Test
    void getSelectedModel_returnsStoredModel() {
        UserPreference pref = new UserPreference("user@test.com", "UTC");
        pref.setSelectedModel("DeepSeek-R1");
        when(repo.findByEmail("user@test.com")).thenReturn(Optional.of(pref));

        assertThat(service.getSelectedModel("user@test.com")).isEqualTo("DeepSeek-R1");
    }

    @Test
    void getSelectedModel_noPreference_returnsNull() {
        when(repo.findByEmail("user@test.com")).thenReturn(Optional.empty());

        assertThat(service.getSelectedModel("user@test.com")).isNull();
    }

    @Test
    void getSelectedModel_preferenceExistsButNoModel_returnsNull() {
        UserPreference pref = new UserPreference("user@test.com", "UTC");
        when(repo.findByEmail("user@test.com")).thenReturn(Optional.of(pref));

        assertThat(service.getSelectedModel("user@test.com")).isNull();
    }

    // ── setDefaultCurrency ────────────────────────────────────────────────────

    @Test
    void setDefaultCurrency_validCurrency_savesUppercase() {
        UserPreference pref = new UserPreference("user@test.com", "UTC");
        when(repo.findByEmail("user@test.com")).thenReturn(Optional.of(pref));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        UserPreference result = service.setDefaultCurrency("user@test.com", "hkd");

        assertThat(result.getDefaultCurrency()).isEqualTo("HKD");
    }

    @Test
    void setDefaultCurrency_nullCurrency_defaultsToUSD() {
        UserPreference pref = new UserPreference("user@test.com", "UTC");
        when(repo.findByEmail("user@test.com")).thenReturn(Optional.of(pref));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        UserPreference result = service.setDefaultCurrency("user@test.com", null);

        assertThat(result.getDefaultCurrency()).isEqualTo("USD");
    }

    @Test
    void setDefaultCurrency_blankCurrency_defaultsToUSD() {
        UserPreference pref = new UserPreference("user@test.com", "UTC");
        when(repo.findByEmail("user@test.com")).thenReturn(Optional.of(pref));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        UserPreference result = service.setDefaultCurrency("user@test.com", "  ");

        assertThat(result.getDefaultCurrency()).isEqualTo("USD");
    }

    @Test
    void setDefaultCurrency_noPref_createsNewRecord() {
        when(repo.findByEmail("new@test.com")).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        UserPreference result = service.setDefaultCurrency("new@test.com", "EUR");

        assertThat(result.getDefaultCurrency()).isEqualTo("EUR");
    }
}
