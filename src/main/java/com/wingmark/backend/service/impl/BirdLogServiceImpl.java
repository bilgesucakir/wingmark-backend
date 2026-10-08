package com.wingmark.backend.service.impl;

import com.wingmark.backend.dto.birdlog.BirdLogLocationResultDto;
import com.wingmark.backend.dto.birdlog.BirdLogResponseDto;
import com.wingmark.backend.dto.birdlog.CreateBirdLogRequestDto;
import com.wingmark.backend.dto.birdlog.UpdateBirdLogRequestDto;
import com.wingmark.backend.entity.BirdLog;
import com.wingmark.backend.enums.Gender;
import com.wingmark.backend.enums.LifeStage;
import com.wingmark.backend.exception.ErrorCode;
import com.wingmark.backend.exception.BadRequestException;
import com.wingmark.backend.exception.InvalidReferenceException;
import com.wingmark.backend.exception.ResourceNotFoundException;
import com.wingmark.backend.repository.BirdLogRepository;
import com.wingmark.backend.repository.SpeciesRepository;
import com.wingmark.backend.service.BadgeService;
import com.wingmark.backend.service.FileStorageService;
import com.wingmark.backend.service.BirdLogService;
import com.wingmark.backend.util.LocalizedTextResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Bird log queries and changes scoped to the owner. */
@Service
@RequiredArgsConstructor
public class BirdLogServiceImpl implements BirdLogService {

    private static final Duration OBSERVED_AT_CLOCK_SKEW = Duration.ofMinutes(5);
    static final int MAX_LOCATION_LIMIT = 1000;

    private final BirdLogRepository birdLogRepository;
    private final SpeciesRepository speciesRepository;
    private final BadgeService badgeService;
    private final FileStorageService fileStorageService;
    private final UploadedFileCleaner uploadedFileCleaner;

    @Override
    public List<BirdLogResponseDto> getAll(Boolean hasSpecies, Gender gender, LifeStage lifeStage, Sort.Direction sortDirection, Locale locale) {
        return birdLogRepository.findFiltered(null, hasSpecies, gender, lifeStage, sortDirection).stream()
                .map(log -> toResponse(log, locale))
                .toList();
    }

    @Override
    public List<BirdLogResponseDto> getByUserId(UUID userId, Boolean hasSpecies, Gender gender, LifeStage lifeStage, Sort.Direction sortDirection, Locale locale) {
        return birdLogRepository.findFiltered(userId, hasSpecies, gender, lifeStage, sortDirection).stream()
                .map(log -> toResponse(log, locale))
                .toList();
    }

    @Override
    public BirdLogResponseDto getById(UUID userId, UUID logId, Locale locale) {
        return toResponse(findOwnedLog(userId, logId), locale);
    }

    @Override
    public BirdLogLocationResultDto getByLocation(UUID userId, double minLat, double maxLat, double minLng, double maxLng,
                                                  Boolean hasSpecies, Gender gender, LifeStage lifeStage, int limit, Locale locale) {
        validateBounds(minLat, maxLat, minLng, maxLng);
        if (limit < 1 || limit > MAX_LOCATION_LIMIT) {
            throw new BadRequestException(ErrorCode.INVALID_PARAMETER, "limit must be between 1 and " + MAX_LOCATION_LIMIT);
        }

        // Fetch one extra to know whether there were more than `limit` without a count query.
        List<BirdLog> found = birdLogRepository.findWithinBounds(userId, minLat, maxLat, minLng, maxLng,
                hasSpecies, gender, lifeStage, limit + 1);
        boolean truncated = found.size() > limit;
        List<BirdLogResponseDto> logs = found.stream()
                .limit(limit)
                .map(log -> toResponse(log, locale))
                .toList();
        return new BirdLogLocationResultDto(logs, truncated);
    }

    private static void validateBounds(double minLat, double maxLat, double minLng, double maxLng) {
        if (!inRange(minLat, 90) || !inRange(maxLat, 90) || !inRange(minLng, 180) || !inRange(maxLng, 180)) {
            throw new BadRequestException(ErrorCode.INVALID_BOUNDS,
                    "Latitudes must be within [-90, 90] and longitudes within [-180, 180]");
        }
        if (minLat > maxLat) {
            throw new BadRequestException(ErrorCode.INVALID_BOUNDS, "minLat must not be greater than maxLat");
        }
        // minLng > maxLng is allowed: it means the box crosses the antimeridian.
    }

    private static boolean inRange(double value, double limit) {
        return !Double.isNaN(value) && value >= -limit && value <= limit;
    }

    @Override
    public BirdLogResponseDto create(UUID userId, CreateBirdLogRequestDto request, Locale locale) {
        validateSpecies(request.speciesId());

        BirdLog log = BirdLog.builder()
                .userId(userId)
                .speciesId(request.speciesId())
                .speciesStatus(request.speciesId() != null ? request.speciesStatus() : null)
                .pet(request.pet())
                .customName(request.customName())
                .lifeStage(request.lifeStage())
                .gender(request.gender())
                .photoUrl(request.photoUrl())
                .note(request.note())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .locationName(request.locationName())
                .observedAt(request.observedAt() != null ? validateObservedAt(request.observedAt()) : Instant.now())
                .build();

        log = birdLogRepository.save(log);
        badgeService.evaluateForUser(userId);

        return toResponse(log, locale);
    }

    @Override
    public BirdLogResponseDto update(UUID userId, UUID logId, UpdateBirdLogRequestDto request, Locale locale) {
        BirdLog log = findOwnedLog(userId, logId);
        validateSpecies(request.speciesId());

        log.setSpeciesId(request.speciesId());
        log.setSpeciesStatus(request.speciesId() != null ? request.speciesStatus() : null);
        log.setPet(request.pet());
        log.setCustomName(request.customName());
        log.setLifeStage(request.lifeStage());
        log.setGender(request.gender());
        String previousPhotoUrl = log.getPhotoUrl();
        log.setPhotoUrl(request.photoUrl());
        log.setNote(request.note());
        log.setLatitude(request.latitude());
        log.setLongitude(request.longitude());
        log.setLocationName(request.locationName());
        // Omitted on update means "leave as is" - resetting it to now would silently
        // re-date an old sighting every time it's edited.
        if (request.observedAt() != null) {
            log.setObservedAt(validateObservedAt(request.observedAt()));
        }

        log = birdLogRepository.save(log);
        if (previousPhotoUrl != null && !previousPhotoUrl.equals(request.photoUrl())) {
            uploadedFileCleaner.deleteIfUnreferenced(previousPhotoUrl);
        }
        badgeService.evaluateForUser(userId);

        return toResponse(log, locale);
    }

    @Override
    public void delete(UUID userId, UUID logId) {
        BirdLog log = findOwnedLog(userId, logId);
        birdLogRepository.delete(log);
        uploadedFileCleaner.deleteIfUnreferenced(log.getPhotoUrl());
        badgeService.evaluateForUser(userId);
    }

    /** Rejects sightings dated in the future, with slack for a fast phone clock. */
    private Instant validateObservedAt(Instant observedAt) {
        if (observedAt.isAfter(Instant.now().plus(OBSERVED_AT_CLOCK_SKEW))) {
            throw new BadRequestException(ErrorCode.OBSERVED_AT_IN_FUTURE, "observedAt cannot be in the future");
        }
        return observedAt;
    }

    private void validateSpecies(UUID speciesId) {
        if (speciesId != null && !speciesRepository.existsById(speciesId)) {
            throw InvalidReferenceException.of("speciesId", "species", speciesId);
        }
    }

    private BirdLog findOwnedLog(UUID userId, UUID logId) {
        // Scoped by userId in the query itself (not a separate ownership check after
        // an unscoped lookup) so another user's log ID never leaks its existence.
        return birdLogRepository.findByIdAndUserId(logId, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("BirdLog", logId));
    }

    private BirdLogResponseDto toResponse(BirdLog log, Locale locale) {
        String speciesCommonName = null;
        if (log.getSpeciesId() != null) {
            speciesCommonName = speciesRepository.findById(log.getSpeciesId())
                    .map(species -> LocalizedTextResolver.resolve(species.getCommonName(), locale))
                    .orElse(null);
        }

        return new BirdLogResponseDto(
                log.getId(),
                log.getUserId(),
                log.getSpeciesId(),
                speciesCommonName,
                log.getSpeciesStatus(),
                log.isPet(),
                log.getCustomName(),
                log.getLifeStage(),
                log.getGender(),
                log.getPhotoUrl(),
                fileStorageService.thumbnailUrl(log.getPhotoUrl()).orElse(null),
                log.getNote(),
                log.getLatitude(),
                log.getLongitude(),
                log.getLocationName(),
                // Legacy logs saved before observedAt existed are backfilled on startup
                // (LegacyTimestampBackfill); this keeps the API from ever sending null meanwhile.
                log.getObservedAt() != null ? log.getObservedAt() : log.getCreatedAt(),
                log.getVisibility(),
                log.getCreatedAt()
        );
    }
}
