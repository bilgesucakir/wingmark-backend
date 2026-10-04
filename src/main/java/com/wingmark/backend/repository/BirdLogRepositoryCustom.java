package com.wingmark.backend.repository;

import com.wingmark.backend.entity.BirdLog;
import com.wingmark.backend.enums.Gender;
import com.wingmark.backend.enums.LifeStage;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.UUID;

/** Bird log queries that need dynamic filters. */
public interface BirdLogRepositoryCustom {

    /** Counts the distinct species in a user's logs. */
    long countDistinctSpeciesByUserId(UUID userId);

    /**
     * Returns a user's logs inside a lat/lng box, newest first, at most {@code limit}. Filters are
     * optional (null = no filter). {@code minLng > maxLng} means the box crosses the antimeridian.
     */
    List<BirdLog> findWithinBounds(UUID userId, double minLat, double maxLat, double minLng, double maxLng,
                                   Boolean hasSpecies, Gender gender, LifeStage lifeStage, int limit);

    /**
     * Returns logs sorted by {@code observedAt}; a null {@code userId} means every user's logs (admin) and
     * the other filters are optional (null = no filter, {@code hasSpecies} tests whether a species is set).
     */
    List<BirdLog> findFiltered(UUID userId, Boolean hasSpecies, Gender gender, LifeStage lifeStage, Sort.Direction observedAtDirection);
}
