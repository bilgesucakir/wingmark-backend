package com.wingmark.backend.service;

import com.wingmark.backend.dto.species.SpeciesRecordingResponseDto;

import java.util.List;

/** Live proxy to Xeno-canto's free bird-sound recording API (requires XENO_CANTO_API_KEY). */
public interface XenoCantoService {

    /** Fetches call/song recordings for a species by scientific name, most-voted first. */
    List<SpeciesRecordingResponseDto> findRecordings(String scientificName);
}
