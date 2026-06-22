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
    public UserPreference getOrDefault(String email) {
        return repo.findByEmail(email)
                   .orElseGet(() -> new UserPreference(email, "UTC"));
    }

    @Override
    @Transactional
    public UserPreference setTimezone(String email, String timezone) {
        UserPreference pref = repo.findByEmail(email)
                .orElseGet(() -> new UserPreference(email, timezone));
        pref.setTimezone(timezone);
        pref.setUpdatedAt(Instant.now());
        return repo.save(pref);
    }

    @Override
    @Transactional
    public UserPreference setSelectedModel(String email, String displayName) {
        UserPreference pref = repo.findByEmail(email)
                .orElseGet(() -> new UserPreference(email, "UTC"));
        pref.setSelectedModel(displayName);
        pref.setUpdatedAt(Instant.now());
        return repo.save(pref);
    }

    @Override
    public String getSelectedModel(String email) {
        return repo.findByEmail(email)
                .map(UserPreference::getSelectedModel)
                .orElse(null);
    }

    @Override
    @Transactional
    public UserPreference setDefaultCurrency(String email, String currency) {
        UserPreference pref = repo.findByEmail(email)
                .orElseGet(() -> new UserPreference(email, "UTC"));
        pref.setDefaultCurrency(currency == null || currency.isBlank() ? "USD" : currency.trim().toUpperCase());
        pref.setUpdatedAt(Instant.now());
        return repo.save(pref);
    }
}
