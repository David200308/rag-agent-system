package com.agentsystem.user;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserPreferenceControllerTest {

    @Mock UserPreferenceService service;
    @Mock HttpServletRequest    request;
    @InjectMocks UserPreferenceController controller;

    private UserPreference pref(String tz, String model, String currency) {
        UserPreference p = new UserPreference();
        p.setTimezone(tz);
        p.setSelectedModel(model);
        p.setDefaultCurrency(currency);
        return p;
    }

    private void stubEmail(String email) {
        when(request.getAttribute("authenticatedEmail")).thenReturn(email);
    }

    // ── getPreferences ────────────────────────────────────────────────────────

    @Test
    void getPreferences_noEmail_returns401() {
        stubEmail(null);

        ResponseEntity<Map<String, Object>> resp = controller.getPreferences(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void getPreferences_withEmail_returnsAllFields() {
        stubEmail("user@test.com");
        when(service.getOrDefault("user@test.com")).thenReturn(pref("UTC", "GPT-4", "USD"));

        ResponseEntity<Map<String, Object>> resp = controller.getPreferences(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("timezone", "UTC");
        assertThat(resp.getBody()).containsEntry("selectedModel", "GPT-4");
        assertThat(resp.getBody()).containsEntry("defaultCurrency", "USD");
    }

    @Test
    void getPreferences_nullFields_areIncluded() {
        stubEmail("user@test.com");
        when(service.getOrDefault("user@test.com")).thenReturn(pref(null, null, null));

        ResponseEntity<Map<String, Object>> resp = controller.getPreferences(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsKey("timezone");
        assertThat(resp.getBody()).containsKey("selectedModel");
        assertThat(resp.getBody()).containsKey("defaultCurrency");
    }

    // ── updatePreferences ─────────────────────────────────────────────────────

    @Test
    void updatePreferences_noEmail_returns401() {
        stubEmail(null);

        ResponseEntity<Map<String, Object>> resp =
                controller.updatePreferences(Map.of("timezone", "Asia/Tokyo"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void updatePreferences_timezone_callsSetTimezone() {
        stubEmail("user@test.com");
        when(service.getOrDefault("user@test.com")).thenReturn(pref("UTC", null, null));
        when(service.setTimezone("user@test.com", "Asia/Tokyo")).thenReturn(pref("Asia/Tokyo", null, null));

        ResponseEntity<Map<String, Object>> resp =
                controller.updatePreferences(Map.of("timezone", "Asia/Tokyo"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("timezone", "Asia/Tokyo");
        verify(service).setTimezone("user@test.com", "Asia/Tokyo");
    }

    @Test
    void updatePreferences_blankTimezone_doesNotCallSetTimezone() {
        stubEmail("user@test.com");
        when(service.getOrDefault("user@test.com")).thenReturn(pref("UTC", null, null));

        controller.updatePreferences(Map.of("timezone", "  "), request);

        verify(service, never()).setTimezone(anyString(), anyString());
    }

    @Test
    void updatePreferences_selectedModel_callsSetSelectedModel() {
        stubEmail("user@test.com");
        when(service.getOrDefault("user@test.com")).thenReturn(pref("UTC", null, null));
        when(service.setSelectedModel("user@test.com", "GPT-4")).thenReturn(pref("UTC", "GPT-4", null));

        ResponseEntity<Map<String, Object>> resp =
                controller.updatePreferences(Map.of("selectedModel", "GPT-4"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("selectedModel", "GPT-4");
        verify(service).setSelectedModel("user@test.com", "GPT-4");
    }

    @Test
    void updatePreferences_nullSelectedModel_setsNull() {
        stubEmail("user@test.com");
        when(service.getOrDefault("user@test.com")).thenReturn(pref("UTC", "GPT-4", null));
        when(service.setSelectedModel("user@test.com", null)).thenReturn(pref("UTC", null, null));

        Map<String, String> body = new java.util.HashMap<>();
        body.put("selectedModel", null);
        ResponseEntity<Map<String, Object>> resp = controller.updatePreferences(body, request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        verify(service).setSelectedModel("user@test.com", null);
    }

    @Test
    void updatePreferences_defaultCurrency_callsSetDefaultCurrency() {
        stubEmail("user@test.com");
        when(service.getOrDefault("user@test.com")).thenReturn(pref("UTC", null, "USD"));
        when(service.setDefaultCurrency("user@test.com", "HKD")).thenReturn(pref("UTC", null, "HKD"));

        ResponseEntity<Map<String, Object>> resp =
                controller.updatePreferences(Map.of("defaultCurrency", "HKD"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("defaultCurrency", "HKD");
        verify(service).setDefaultCurrency("user@test.com", "HKD");
    }

    @Test
    void updatePreferences_allFields_callsAllSetters() {
        stubEmail("user@test.com");
        when(service.getOrDefault("user@test.com")).thenReturn(pref("UTC", null, "USD"));
        when(service.setTimezone("user@test.com", "Europe/London")).thenReturn(pref("Europe/London", null, "USD"));
        when(service.setSelectedModel("user@test.com", "Claude")).thenReturn(pref("Europe/London", "Claude", "USD"));
        when(service.setDefaultCurrency("user@test.com", "GBP")).thenReturn(pref("Europe/London", "Claude", "GBP"));

        var resp = controller.updatePreferences(
                Map.of("timezone", "Europe/London", "selectedModel", "Claude", "defaultCurrency", "GBP"),
                request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        verify(service).setTimezone("user@test.com", "Europe/London");
        verify(service).setSelectedModel("user@test.com", "Claude");
        verify(service).setDefaultCurrency("user@test.com", "GBP");
    }
}
