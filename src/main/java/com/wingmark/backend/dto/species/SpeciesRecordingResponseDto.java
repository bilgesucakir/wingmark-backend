package com.wingmark.backend.dto.species;

/** A bird-sound recording from Xeno-canto. */
public record SpeciesRecordingResponseDto(
        String id,
        String recordingUrl,
        String type,
        String quality,
        String recordist,
        String licenseUrl
) {
}
