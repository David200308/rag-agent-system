package com.ragagent.user;

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
    public ResponseEntity<Map<String, String>> getPreferences(HttpServletRequest req) {
        String email = (String) req.getAttribute("authenticatedEmail");
        if (email == null) {
            return ResponseEntity.status(401).build();
        }
        UserPreference pref = service.getOrDefault(email);
        return ResponseEntity.ok(Map.of("timezone", pref.getTimezone()));
    }

    @PutMapping("/preferences")
    @Operation(summary = "Update the current user's preferences")
    public ResponseEntity<Map<String, String>> updatePreferences(
            @RequestBody Map<String, String> body,
            HttpServletRequest req) {

        String email = (String) req.getAttribute("authenticatedEmail");
        if (email == null) {
            return ResponseEntity.status(401).build();
        }
        String timezone = body.get("timezone");
        if (timezone == null || timezone.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        UserPreference pref = service.setTimezone(email, timezone.trim());
        return ResponseEntity.ok(Map.of("timezone", pref.getTimezone()));
    }
}
