package com.agentsystem.user.service;

import com.agentsystem.user.entity.UserPreference;

public interface UserPreferenceService {

    UserPreference getOrDefault(String userUuid);

    UserPreference setTimezone(String userUuid, String timezone);

    UserPreference setSelectedModel(String userUuid, String displayName);

    String getSelectedModel(String userUuid);

    UserPreference setDefaultCurrency(String userUuid, String currency);
}
