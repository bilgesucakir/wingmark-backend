package com.wingmark.backend.service;

import com.wingmark.backend.dto.birdlog.BirdLogResponse;
import com.wingmark.backend.dto.birdlog.CreateBirdLogRequest;
import com.wingmark.backend.dto.birdlog.UpdateBirdLogRequest;

import java.util.List;
import java.util.UUID;

public interface BirdLogService {

    List<BirdLogResponse> listForUser(UUID userId);

    BirdLogResponse get(UUID userId, UUID logId);

    List<BirdLogResponse> findWithinBounds(UUID userId, double minLat, double maxLat, double minLng, double maxLng);

    BirdLogResponse create(UUID userId, CreateBirdLogRequest request);

    BirdLogResponse update(UUID userId, UUID logId, UpdateBirdLogRequest request);

    void delete(UUID userId, UUID logId);
}
