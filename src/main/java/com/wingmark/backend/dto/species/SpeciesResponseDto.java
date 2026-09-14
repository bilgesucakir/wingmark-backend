package com.wingmark.backend.dto.species;

import java.util.List;
import java.util.UUID;

public record SpeciesResponseDto(
        UUID id,
        String commonName,
        String scientificName,
        String family,
        String order,
        String description,
        String lifespan,
        String diet,
        String habitat,
        String sizeDescription,
        String conservationStatus,
        String nativeRange,
        List<SpeciesImageResponseDto> images
) {
}
