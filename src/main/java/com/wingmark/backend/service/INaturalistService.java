package com.wingmark.backend.service;

import com.wingmark.backend.dto.species.PhotoCandidateDto;
import com.wingmark.backend.enums.ImageGender;
import com.wingmark.backend.enums.LifeStageImage;

import java.util.List;

public interface INaturalistService {

    /**
     * Searches iNaturalist observations for a species matching the given life stage
     * and (optionally) sex, returning candidate reference photos for admin curation
     * into the guide - this is not meant to be called on every guide page view.
     */
    List<PhotoCandidateDto> findPhotoCandidates(String scientificName, LifeStageImage lifeStage, ImageGender gender);
}
