package com.wingmark.backend.repository;

import com.wingmark.backend.entity.BirdLog;
import com.wingmark.backend.enums.LifeStage;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BirdLogRepository extends MongoRepository<BirdLog, UUID>, BirdLogRepositoryCustom {

    Optional<BirdLog> findByIdAndUserId(UUID id, UUID userId);


    long countByUserId(UUID userId);

    long countByUserIdAndPetTrue(UUID userId);

    long countByUserIdAndSpeciesIdIsNull(UUID userId);

    long countByUserIdAndLifeStage(UUID userId, LifeStage lifeStage);

    long countByUserIdAndSpeciesId(UUID userId, UUID speciesId);

    List<BirdLog> findByUserIdAndSpeciesIdIsNotNullAndPetFalse(UUID userId);

    List<BirdLog> findByUserIdAndPetFalse(UUID userId);

    List<BirdLog> findByUserId(UUID userId);

    boolean existsByPhotoUrlEndingWith(String suffix);

    void deleteByUserId(UUID userId);
}
