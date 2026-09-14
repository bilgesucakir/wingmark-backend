package com.wingmark.backend.service.impl;

import com.wingmark.backend.dto.species.CreateSpeciesImageRequest;
import com.wingmark.backend.dto.species.CreateSpeciesRequest;
import com.wingmark.backend.dto.species.SpeciesImageResponse;
import com.wingmark.backend.dto.species.SpeciesResponse;
import com.wingmark.backend.dto.species.UpdateSpeciesRequest;
import com.wingmark.backend.entity.Species;
import com.wingmark.backend.entity.SpeciesImage;
import com.wingmark.backend.exception.DuplicateResourceException;
import com.wingmark.backend.exception.ResourceNotFoundException;
import com.wingmark.backend.repository.SpeciesImageRepository;
import com.wingmark.backend.repository.SpeciesRepository;
import com.wingmark.backend.service.SpeciesService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SpeciesServiceImpl implements SpeciesService {

    private final SpeciesRepository speciesRepository;
    private final SpeciesImageRepository speciesImageRepository;

    @Override
    public List<SpeciesResponse> list(String search) {
        List<Species> species = StringUtils.hasText(search)
                ? speciesRepository.findByCommonNameContainingIgnoreCase(search)
                : speciesRepository.findAll();
        return species.stream().map(this::toResponse).toList();
    }

    @Override
    public SpeciesResponse get(UUID id) {
        return toResponse(findSpecies(id));
    }

    @Override
    @Transactional
    public SpeciesResponse create(CreateSpeciesRequest request) {
        if (speciesRepository.existsByScientificNameIgnoreCase(request.scientificName())) {
            throw new DuplicateResourceException("A species with this scientific name already exists");
        }

        Species species = Species.builder()
                .commonName(request.commonName())
                .scientificName(request.scientificName())
                .family(request.family())
                .order(request.order())
                .description(request.description())
                .lifespan(request.lifespan())
                .diet(request.diet())
                .habitat(request.habitat())
                .sizeDescription(request.sizeDescription())
                .conservationStatus(request.conservationStatus())
                .nativeRange(request.nativeRange())
                .build();

        return toResponse(speciesRepository.save(species));
    }

    @Override
    @Transactional
    public SpeciesResponse update(UUID id, UpdateSpeciesRequest request) {
        Species species = findSpecies(id);

        if (!species.getScientificName().equalsIgnoreCase(request.scientificName())
                && speciesRepository.existsByScientificNameIgnoreCase(request.scientificName())) {
            throw new DuplicateResourceException("A species with this scientific name already exists");
        }

        species.setCommonName(request.commonName());
        species.setScientificName(request.scientificName());
        species.setFamily(request.family());
        species.setOrder(request.order());
        species.setDescription(request.description());
        species.setLifespan(request.lifespan());
        species.setDiet(request.diet());
        species.setHabitat(request.habitat());
        species.setSizeDescription(request.sizeDescription());
        species.setConservationStatus(request.conservationStatus());
        species.setNativeRange(request.nativeRange());

        return toResponse(speciesRepository.save(species));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        if (!speciesRepository.existsById(id)) {
            throw ResourceNotFoundException.of("Species", id);
        }
        speciesImageRepository.deleteBySpeciesId(id);
        speciesRepository.deleteById(id);
    }

    @Override
    @Transactional
    public SpeciesImageResponse addImage(UUID speciesId, CreateSpeciesImageRequest request) {
        if (!speciesRepository.existsById(speciesId)) {
            throw ResourceNotFoundException.of("Species", speciesId);
        }

        SpeciesImage image = SpeciesImage.builder()
                .speciesId(speciesId)
                .lifeStage(request.lifeStage())
                .gender(request.gender())
                .imageUrl(request.imageUrl())
                .caption(request.caption())
                .build();

        image = speciesImageRepository.save(image);
        return toImageResponse(image);
    }

    @Override
    @Transactional
    public void deleteImage(UUID speciesId, UUID imageId) {
        SpeciesImage image = speciesImageRepository.findById(imageId)
                .orElseThrow(() -> ResourceNotFoundException.of("SpeciesImage", imageId));

        if (!image.getSpeciesId().equals(speciesId)) {
            throw ResourceNotFoundException.of("SpeciesImage", imageId);
        }

        speciesImageRepository.delete(image);
    }

    private Species findSpecies(UUID id) {
        return speciesRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Species", id));
    }

    private SpeciesResponse toResponse(Species species) {
        List<SpeciesImageResponse> images = speciesImageRepository.findBySpeciesId(species.getId()).stream()
                .map(this::toImageResponse)
                .toList();

        return new SpeciesResponse(
                species.getId(),
                species.getCommonName(),
                species.getScientificName(),
                species.getFamily(),
                species.getOrder(),
                species.getDescription(),
                species.getLifespan(),
                species.getDiet(),
                species.getHabitat(),
                species.getSizeDescription(),
                species.getConservationStatus(),
                species.getNativeRange(),
                images
        );
    }

    private SpeciesImageResponse toImageResponse(SpeciesImage image) {
        return new SpeciesImageResponse(
                image.getId(),
                image.getLifeStage(),
                image.getGender(),
                image.getImageUrl(),
                image.getCaption()
        );
    }
}
