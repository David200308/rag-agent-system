package com.agentsystem.user.service.impl;

import com.agentsystem.user.service.UserPreferenceService;

import com.agentsystem.user.entity.UserPreference;
import com.agentsystem.user.repository.UserPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UserPreferenceServiceImpl implements UserPreferenceService {

    private final UserPreferenceRepository repo;

    @Override
    @Transactional(readOnly = true)
    public UserPreference getOrDefault(String userUuid) {
        return repo.findByUserUuid(userUuid)
                   .orElseGet(() -> new UserPreference(userUuid, "UTC"));
    }

    @Override
    @Transactional
    public UserPreference setTimezone(String userUuid, String timezone) {
        UserPreference pref = repo.findByUserUuid(userUuid)
                .orElseGet(() -> new UserPreference(userUuid, timezone));
        pref.setTimezone(timezone);
        pref.setUpdatedAt(Instant.now());
        return repo.save(pref);
    }

    @Override
    @Transactional
    public UserPreference setSelectedModel(String userUuid, String displayName) {
        UserPreference pref = repo.findByUserUuid(userUuid)
                .orElseGet(() -> new UserPreference(userUuid, "UTC"));
        pref.setSelectedModel(displayName);
        pref.setUpdatedAt(Instant.now());
        return repo.save(pref);
    }

    @Override
    public String getSelectedModel(String userUuid) {
        return repo.findByUserUuid(userUuid)
                .map(UserPreference::getSelectedModel)
                .orElse(null);
    }

    @Override
    @Transactional
    public UserPreference setDefaultCurrency(String userUuid, String currency) {
        UserPreference pref = repo.findByUserUuid(userUuid)
                .orElseGet(() -> new UserPreference(userUuid, "UTC"));
        pref.setDefaultCurrency(currency == null || currency.isBlank() ? "USD" : currency.trim().toUpperCase());
        pref.setUpdatedAt(Instant.now());
        return repo.save(pref);
    }
}
