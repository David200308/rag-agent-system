package com.agentsystem.user.service;

import com.agentsystem.user.entity.UserPreference;

public interface UserPreferenceService {

    UserPreference getOrDefault(String email);

    UserPreference setTimezone(String email, String timezone);

    UserPreference setSelectedModel(String email, String displayName);

    String getSelectedModel(String email);

    UserPreference setDefaultCurrency(String email, String currency);
}
