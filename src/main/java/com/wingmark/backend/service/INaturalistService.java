package com.wingmark.backend.service;

import com.wingmark.backend.dto.species.PhotoCandidateDto;
import com.wingmark.backend.enums.ImageGender;
import com.wingmark.backend.enums.LifeStageImage;

import java.util.List;

/** Client for iNaturalist photo search. */
public interface INaturalistService {

    /** Searches iNaturalist for candidate reference photos of a species by life stage and optional sex, for admin curation. */
    List<PhotoCandidateDto> findPhotoCandidates(String scientificName, LifeStageImage lifeStage, ImageGender gender);
}
