package com.wingmark.backend.repository;

import com.wingmark.backend.entity.SpeciesImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpeciesImageRepository extends JpaRepository<SpeciesImage, UUID> {

    List<SpeciesImage> findBySpeciesId(UUID speciesId);

    void deleteBySpeciesId(UUID speciesId);
}
