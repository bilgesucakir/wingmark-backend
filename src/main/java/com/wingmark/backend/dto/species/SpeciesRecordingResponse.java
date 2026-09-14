package com.wingmark.backend.dto.species;

public record SpeciesRecordingResponse(
        String id,
        String recordingUrl,
        String type,
        String quality,
        String recordist,
        String licenseUrl
) {
}
