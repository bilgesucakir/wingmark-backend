package com.wingmark.backend.repository;

import com.wingmark.backend.entity.BirdLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BirdLogRepository extends JpaRepository<BirdLog, UUID> {

    /** Admin-only use: every log across every user. There is no cross-user viewing feature for regular users. */
    List<BirdLog> findAllByOrderByObservedAtDesc();

    List<BirdLog> findByUserIdOrderByObservedAtDesc(UUID userId);

    Optional<BirdLog> findByIdAndUserId(UUID id, UUID userId);

    @Query("""
            select b from BirdLog b
            where b.userId = :userId
              and b.latitude between :minLat and :maxLat
              and b.longitude between :minLng and :maxLng
            """)
    List<BirdLog> findWithinBounds(@Param("userId") UUID userId,
                                    @Param("minLat") double minLat,
                                    @Param("maxLat") double maxLat,
                                    @Param("minLng") double minLng,
                                    @Param("maxLng") double maxLng);

    long countByUserId(UUID userId);

    long countByUserIdAndPetTrue(UUID userId);

    long countByUserIdAndSpeciesIdIsNull(UUID userId);

    @Query("select count(distinct b.speciesId) from BirdLog b where b.userId = :userId and b.speciesId is not null and b.pet = false")
    long countDistinctSpeciesByUserId(@Param("userId") UUID userId);

    long countByUserIdAndLifeStage(UUID userId, com.wingmark.backend.enums.LifeStage lifeStage);

    List<BirdLog> findByUserIdAndSpeciesIdIsNotNullAndPetFalse(UUID userId);

    List<BirdLog> findByUserIdAndPetFalse(UUID userId);
}
