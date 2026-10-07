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

    /** Finds species whose common name (en or tr) or scientific name matches the regex; build it with {@code SearchPattern}. */
    @Query("{ $or: [ {'commonName.en': {$regex: ?0, $options: 'i'}}, {'commonName.tr': {$regex: ?0, $options: 'i'}}, "
            + "{'scientificName': {$regex: ?0, $options: 'i'}} ] }")
    Page<Species> findByNameMatching(String regex, Pageable pageable);
}
