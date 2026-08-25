package com.agentsystem.user.controller;

import com.agentsystem.user.entity.UserPreference;
import com.agentsystem.user.service.UserPreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/user")
@Tag(name = "User Preferences")
@RequiredArgsConstructor
public class UserPreferenceController {

    private final UserPreferenceService service;

    @GetMapping("/preferences")
    @Operation(summary = "Get the current user's preferences")
    public ResponseEntity<Map<String, Object>> getPreferences(HttpServletRequest req) {
        String userUuid = (String) req.getAttribute("authenticatedUserUuid");
        if (userUuid == null) {
            return ResponseEntity.status(401).build();
        }
        UserPreference pref = service.getOrDefault(userUuid);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("timezone",         pref.getTimezone());
        result.put("selectedModel",    pref.getSelectedModel());
        result.put("defaultCurrency",  pref.getDefaultCurrency());
        return ResponseEntity.ok(result);
    }

    @PutMapping("/preferences")
    @Operation(summary = "Update the current user's preferences (timezone and/or selectedModel)")
    public ResponseEntity<Map<String, Object>> updatePreferences(
            @RequestBody Map<String, String> body,
            HttpServletRequest req) {

        String userUuid = (String) req.getAttribute("authenticatedUserUuid");
        if (userUuid == null) {
            return ResponseEntity.status(401).build();
        }
        UserPreference pref = service.getOrDefault(userUuid);

        String timezone = body.get("timezone");
        if (timezone != null && !timezone.isBlank()) {
            pref = service.setTimezone(userUuid, timezone.trim());
        }

        if (body.containsKey("selectedModel")) {
            String model = body.get("selectedModel");
            pref = service.setSelectedModel(userUuid, model == null || model.isBlank() ? null : model.trim());
        }

        if (body.containsKey("defaultCurrency")) {
            pref = service.setDefaultCurrency(userUuid, body.get("defaultCurrency"));
        }

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("timezone",        pref.getTimezone());
        result.put("selectedModel",   pref.getSelectedModel());
        result.put("defaultCurrency", pref.getDefaultCurrency());
        return ResponseEntity.ok(result);
    }
}
