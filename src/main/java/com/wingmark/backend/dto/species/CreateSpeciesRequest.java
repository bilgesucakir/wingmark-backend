package com.wingmark.backend.dto.species;

import jakarta.validation.constraints.NotBlank;

public record CreateSpeciesRequest(
        @NotBlank String commonName,
        @NotBlank String scientificName,
        String family,
        String order,
        String description,
        String lifespan,
        String diet,
        String habitat,
        String sizeDescription,
        String conservationStatus,
        String nativeRange
) {
}
