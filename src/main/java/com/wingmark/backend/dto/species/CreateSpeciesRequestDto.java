package com.wingmark.backend.dto.species;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.Map;

/** commonName must include at least a non-blank "en" translation; the other translation maps are optional. */
public record CreateSpeciesRequestDto(
        @NotEmpty Map<String, String> commonName,
        @NotBlank String scientificName,
        String family,
        String order,
        Map<String, String> description,
        Map<String, String> lifespan,
        Map<String, String> diet,
        Map<String, String> habitat,
        Map<String, String> sizeDescription,
        Map<String, String> conservationStatus,
        Map<String, String> nativeRange
) {
}
