package com.agentsystem.connector.service;

import com.agentsystem.connector.entity.ConnectorToken;

import java.util.Map;
import java.util.Optional;

public interface ConnectorService {

    String getAuthUrl(String provider, String ownerEmail, String orgId);

    void exchangeCode(String provider, String code, String state);

    Map<String, Boolean> getStatus(String ownerEmail, String orgId);

    void disconnect(String provider, String ownerEmail, String orgId);

    Optional<ConnectorToken> getToken(String provider, String ownerEmail, String orgId);
}
