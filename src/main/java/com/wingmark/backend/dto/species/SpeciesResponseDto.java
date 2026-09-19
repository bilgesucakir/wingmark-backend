package com.wingmark.backend.dto.species;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SpeciesResponseDto(
        UUID id,
        Map<String, String> commonName,
        String scientificName,
        String family,
        String order,
        Map<String, String> description,
        Map<String, String> lifespan,
        Map<String, String> diet,
        Map<String, String> habitat,
        Map<String, String> sizeDescription,
        Map<String, String> conservationStatus,
        Map<String, String> nativeRange,
        List<SpeciesImageResponseDto> images
) {
}
