package com.wingmark.backend.service.impl;

import com.wingmark.backend.dto.birdlog.BirdLogResponseDto;
import com.wingmark.backend.dto.birdlog.CreateBirdLogRequestDto;
import com.wingmark.backend.dto.birdlog.UpdateBirdLogRequestDto;
import com.wingmark.backend.entity.BirdLog;
import com.wingmark.backend.entity.Species;
import com.wingmark.backend.enums.Gender;
import com.wingmark.backend.enums.LifeStage;
import com.wingmark.backend.exception.ResourceNotFoundException;
import com.wingmark.backend.repository.BirdLogRepository;
import com.wingmark.backend.repository.SpeciesRepository;
import com.wingmark.backend.service.BadgeService;
import com.wingmark.backend.service.BirdLogService;
import com.wingmark.backend.util.LocalizedTextResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BirdLogServiceImpl implements BirdLogService {

    private final BirdLogRepository birdLogRepository;
    private final SpeciesRepository speciesRepository;
    private final BadgeService badgeService;

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
    public List<BirdLogResponseDto> getByLocation(UUID userId, double minLat, double maxLat, double minLng, double maxLng, Locale locale) {
        return birdLogRepository.findWithinBounds(userId, minLat, maxLat, minLng, maxLng).stream()
                .map(log -> toResponse(log, locale))
                .toList();
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
                .observedAt(Instant.now())
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
        log.setPhotoUrl(request.photoUrl());
        log.setNote(request.note());
        log.setLatitude(request.latitude());
        log.setLongitude(request.longitude());
        log.setLocationName(request.locationName());

        log = birdLogRepository.save(log);
        badgeService.evaluateForUser(userId);

        return toResponse(log, locale);
    }

    @Override
    public void delete(UUID userId, UUID logId) {
        BirdLog log = findOwnedLog(userId, logId);
        birdLogRepository.delete(log);
        badgeService.evaluateForUser(userId);
    }

    private void validateSpecies(UUID speciesId) {
        if (speciesId != null && !speciesRepository.existsById(speciesId)) {
            throw ResourceNotFoundException.of("Species", speciesId);
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
                log.getNote(),
                log.getLatitude(),
                log.getLongitude(),
                log.getLocationName(),
                log.getObservedAt(),
                log.getVisibility(),
                log.getCreatedAt()
        );
    }
}
