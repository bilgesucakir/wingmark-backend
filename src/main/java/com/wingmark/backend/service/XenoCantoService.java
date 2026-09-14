package com.wingmark.backend.service;

import com.wingmark.backend.dto.species.SpeciesRecordingResponse;

import java.util.List;

public interface XenoCantoService {

    List<SpeciesRecordingResponse> findRecordings(String scientificName);
}
