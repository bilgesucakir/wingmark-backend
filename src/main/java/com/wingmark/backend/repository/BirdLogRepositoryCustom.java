package com.wingmark.backend.repository;

import com.wingmark.backend.entity.BirdLog;
import com.wingmark.backend.enums.Gender;
import com.wingmark.backend.enums.LifeStage;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.UUID;

public interface BirdLogRepositoryCustom {

    long countDistinctSpeciesByUserId(UUID userId);

    /**
     * Every filter is optional (null = don't filter on it). userId null means every
     * user's logs (the admin "get all" endpoint); non-null scopes to one user.
     * hasSpecies true/false filters on speciesId being set/unset.
     */
    List<BirdLog> findFiltered(UUID userId, Boolean hasSpecies, Gender gender, LifeStage lifeStage, Sort.Direction observedAtDirection);
}
