package com.wingmark.backend.service;

import com.wingmark.backend.dto.birdlog.BirdLogResponseDto;
import com.wingmark.backend.dto.birdlog.CreateBirdLogRequestDto;
import com.wingmark.backend.dto.birdlog.UpdateBirdLogRequestDto;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Manages bird sighting logs. */
public interface BirdLogService {

    /** Admin-only: returns every log across every user, most recently observed first. */
    List<BirdLogResponseDto> getAll(Locale locale);

    /** Returns every log belonging to this user, most recently observed first. */
    List<BirdLogResponseDto> getByUserId(UUID userId, Locale locale);

    /** Returns one of this user's logs by id, or throws if it doesn't exist/isn't theirs. */
    BirdLogResponseDto getById(UUID userId, UUID logId, Locale locale);

    /** Returns this user's logs whose coordinates fall within the given lat/lng box, for the map view. */
    List<BirdLogResponseDto> getByLocation(UUID userId, double minLat, double maxLat, double minLng, double maxLng, Locale locale);

    /** Creates a new log for this user and re-evaluates their badge progress. */
    BirdLogResponseDto create(UUID userId, CreateBirdLogRequestDto request, Locale locale);

    /** Updates one of this user's existing logs and re-evaluates their badge progress. */
    BirdLogResponseDto update(UUID userId, UUID logId, UpdateBirdLogRequestDto request, Locale locale);

    /** Deletes one of this user's logs and re-evaluates their badge progress. */
    void delete(UUID userId, UUID logId);
}
