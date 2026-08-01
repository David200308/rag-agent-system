package com.agentsystem.travel.service;

import com.agentsystem.travel.dto.TravelRecordDto;
import com.agentsystem.travel.entity.TravelRecord;

import java.util.List;
import java.util.Map;

public interface TravelService {

    List<TravelRecordDto> list(String ownerUuid);

    /** Trips this owner has explicitly opted into being visible to the chat agent. */
    List<TravelRecordDto> listChatVisible(String ownerUuid);

    TravelRecord create(String ownerUuid, Map<String, Object> body);

    TravelRecord update(String id, String ownerUuid, Map<String, Object> body);

    void delete(String id, String ownerUuid);
}
