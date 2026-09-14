package com.wingmark.backend.repository;

import com.wingmark.backend.entity.Species;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpeciesRepository extends JpaRepository<Species, UUID> {

    Optional<Species> findByScientificNameIgnoreCase(String scientificName);

    boolean existsByScientificNameIgnoreCase(String scientificName);

    List<Species> findByCommonNameContainingIgnoreCase(String commonName);
}
