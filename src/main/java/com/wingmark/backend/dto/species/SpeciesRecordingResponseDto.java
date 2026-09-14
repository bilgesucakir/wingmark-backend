package com.wingmark.backend.dto.species;

public record SpeciesRecordingResponseDto(
        String id,
        String recordingUrl,
        String type,
        String quality,
        String recordist,
        String licenseUrl
) {
}
