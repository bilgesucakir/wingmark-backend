package com.wingmark.backend.repository;

import com.wingmark.backend.entity.Species;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Optional;
import java.util.UUID;

/** Species guide entries. */
public interface SpeciesRepository extends MongoRepository<Species, UUID> {

    Optional<Species> findByScientificNameIgnoreCase(String scientificName);

    boolean existsByScientificNameIgnoreCase(String scientificName);

    /** Finds species whose common name in any locale matches the regex; callers must quote the input. */
    @Query("{ $or: [ {'commonName.en': {$regex: ?0, $options: 'i'}}, {'commonName.tr': {$regex: ?0, $options: 'i'}} ] }")
    Page<Species> findByCommonNameContainingIgnoreCase(String commonName, Pageable pageable);
}
