package com.wingmark.backend.service;

import com.wingmark.backend.dto.species.CreateSpeciesImageRequestDto;
import com.wingmark.backend.dto.species.CreateSpeciesRequestDto;
import com.wingmark.backend.dto.species.SpeciesImageResponseDto;
import com.wingmark.backend.dto.species.SpeciesResponseDto;
import com.wingmark.backend.dto.species.UpdateSpeciesRequestDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/** Manages the species guide: reference entries and their life-stage/gender images. */
public interface SpeciesService {

    /** Returns a page of species in the guide, optionally filtered by a common-name substring. */
    Page<SpeciesResponseDto> getAll(String search, Pageable pageable);

    /** Returns one species by id, including its reference images. */
    SpeciesResponseDto getById(UUID id);

    /** Admin-only: adds a new species to the guide. */
    SpeciesResponseDto create(CreateSpeciesRequestDto request);

    /** Admin-only: updates an existing species entry. */
    SpeciesResponseDto update(UUID id, UpdateSpeciesRequestDto request);

    /** Admin-only: removes a species (and its images) from the guide. */
    void delete(UUID id);

    /** Admin-only: attaches a curated reference image (life stage + gender) to a species. */
    SpeciesImageResponseDto addImage(UUID speciesId, CreateSpeciesImageRequestDto request);

    /** Admin-only: removes one of a species' reference images. */
    void deleteImage(UUID speciesId, UUID imageId);
}
