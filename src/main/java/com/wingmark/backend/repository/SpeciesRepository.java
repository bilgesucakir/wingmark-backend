package com.wingmark.backend.repository;

import com.wingmark.backend.entity.Species;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface SpeciesRepository extends MongoRepository<Species, UUID> {

    Optional<Species> findByScientificNameIgnoreCase(String scientificName);

    boolean existsByScientificNameIgnoreCase(String scientificName);

    /** Matches the search term against the common name in any translated locale. */
    @Query("{ $or: [ {'commonName.en': {$regex: ?0, $options: 'i'}}, {'commonName.tr': {$regex: ?0, $options: 'i'}} ] }")
    Page<Species> findByCommonNameContainingIgnoreCase(String commonName, Pageable pageable);
}
