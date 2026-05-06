package com.ragagent.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UserPreferenceService {

    private final UserPreferenceRepository repo;

    @Transactional(readOnly = true)
    public UserPreference getOrDefault(String email) {
        return repo.findByEmail(email)
                   .orElseGet(() -> new UserPreference(email, "UTC"));
    }

    @Transactional
    public UserPreference setTimezone(String email, String timezone) {
        UserPreference pref = repo.findByEmail(email)
                .orElseGet(() -> new UserPreference(email, timezone));
        pref.setTimezone(timezone);
        pref.setUpdatedAt(Instant.now());
        return repo.save(pref);
    }

    @Transactional
    public UserPreference setSelectedModel(String email, String displayName) {
        UserPreference pref = repo.findByEmail(email)
                .orElseGet(() -> new UserPreference(email, "UTC"));
        pref.setSelectedModel(displayName);
        pref.setUpdatedAt(Instant.now());
        return repo.save(pref);
    }

    public String getSelectedModel(String email) {
        return repo.findByEmail(email)
                .map(UserPreference::getSelectedModel)
                .orElse(null);
    }
}
