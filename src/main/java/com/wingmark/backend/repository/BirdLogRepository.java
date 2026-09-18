package com.wingmark.backend.repository;

import com.wingmark.backend.entity.BirdLog;
import com.wingmark.backend.enums.LifeStage;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BirdLogRepository extends MongoRepository<BirdLog, UUID>, BirdLogRepositoryCustom {

    /** Admin-only use: every log across every user. There is no cross-user viewing feature for regular users. */
    List<BirdLog> findAllByOrderByObservedAtDesc();

    List<BirdLog> findByUserIdOrderByObservedAtDesc(UUID userId);

    Optional<BirdLog> findByIdAndUserId(UUID id, UUID userId);

    @Query("{ 'userId': ?0, 'latitude': { $gte: ?1, $lte: ?2 }, 'longitude': { $gte: ?3, $lte: ?4 } }")
    List<BirdLog> findWithinBounds(@Param("userId") UUID userId,
                                    @Param("minLat") double minLat,
                                    @Param("maxLat") double maxLat,
                                    @Param("minLng") double minLng,
                                    @Param("maxLng") double maxLng);

    long countByUserId(UUID userId);

    long countByUserIdAndPetTrue(UUID userId);

    long countByUserIdAndSpeciesIdIsNull(UUID userId);

    long countByUserIdAndLifeStage(UUID userId, LifeStage lifeStage);

    long countByUserIdAndSpeciesId(UUID userId, UUID speciesId);

    List<BirdLog> findByUserIdAndSpeciesIdIsNotNullAndPetFalse(UUID userId);

    List<BirdLog> findByUserIdAndPetFalse(UUID userId);
}
