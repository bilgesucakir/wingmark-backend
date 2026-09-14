package com.wingmark.backend.service;

import com.wingmark.backend.dto.species.CreateSpeciesImageRequest;
import com.wingmark.backend.dto.species.CreateSpeciesRequest;
import com.wingmark.backend.dto.species.SpeciesImageResponse;
import com.wingmark.backend.dto.species.SpeciesResponse;
import com.wingmark.backend.dto.species.UpdateSpeciesRequest;

import java.util.List;
import java.util.UUID;

public interface SpeciesService {

    List<SpeciesResponse> list(String search);

    SpeciesResponse get(UUID id);

    SpeciesResponse create(CreateSpeciesRequest request);

    SpeciesResponse update(UUID id, UpdateSpeciesRequest request);

    void delete(UUID id);

    SpeciesImageResponse addImage(UUID speciesId, CreateSpeciesImageRequest request);

    void deleteImage(UUID speciesId, UUID imageId);
}
