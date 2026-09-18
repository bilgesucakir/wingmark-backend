package com.wingmark.backend.repository;

import com.wingmark.backend.entity.Species;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpeciesRepository extends MongoRepository<Species, UUID> {

    Optional<Species> findByScientificNameIgnoreCase(String scientificName);

    boolean existsByScientificNameIgnoreCase(String scientificName);

    List<Species> findByCommonNameContainingIgnoreCase(String commonName);
}
