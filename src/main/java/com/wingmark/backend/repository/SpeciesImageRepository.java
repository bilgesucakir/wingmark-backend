package com.wingmark.backend.repository;

import com.wingmark.backend.entity.SpeciesImage;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.UUID;

public interface SpeciesImageRepository extends MongoRepository<SpeciesImage, UUID> {

    List<SpeciesImage> findBySpeciesId(UUID speciesId);

    void deleteBySpeciesId(UUID speciesId);
}
