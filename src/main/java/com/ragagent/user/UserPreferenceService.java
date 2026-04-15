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
}
