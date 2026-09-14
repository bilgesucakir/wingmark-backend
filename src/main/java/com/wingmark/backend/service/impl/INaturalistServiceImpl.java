package com.wingmark.backend.service.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wingmark.backend.dto.species.PhotoCandidateDto;
import com.wingmark.backend.enums.ImageGender;
import com.wingmark.backend.enums.LifeStageImage;
import com.wingmark.backend.exception.ExternalServiceException;
import com.wingmark.backend.service.INaturalistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;

import java.time.Duration;
import java.util.List;

/**
 * Wraps iNaturalist's public observations API (no API key required). Life stage and
 * sex are both stored on each observation as "controlled term" annotations, but the
 * API does not reliably AND multiple annotation filters together in one query - so
 * this queries by the sex filter alone (when a gender is requested) and then filters
 * the results client-side for the matching life-stage annotation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class INaturalistServiceImpl implements INaturalistService {

    // iNaturalist controlled term ids, confirmed via GET /v1/controlled_terms
    private static final int TERM_LIFE_STAGE = 1;
    private static final int VALUE_ADULT = 2;
    private static final int VALUE_JUVENILE = 8;
    private static final int TERM_SEX = 9;
    private static final int VALUE_FEMALE = 10;
    private static final int VALUE_MALE = 11;

    private static final int CANDIDATE_FETCH_SIZE = 30;
    private static final int MAX_CANDIDATES_RETURNED = 10;

    private final WebClient iNaturalistWebClient;

    @Override
    public List<PhotoCandidateDto> findPhotoCandidates(String scientificName, LifeStageImage lifeStage, ImageGender gender) {
        Integer sexValueId = toSexValueId(gender);
        int lifeStageValueId = toLifeStageValueId(lifeStage);

        try {
            INaturalistResponse response = iNaturalistWebClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path("/observations")
                                .queryParam("taxon_name", scientificName)
                                .queryParam("photos", true)
                                .queryParam("per_page", CANDIDATE_FETCH_SIZE)
                                .queryParam("order_by", "votes");
                        if (sexValueId != null) {
                            builder = builder.queryParam("term_id", TERM_SEX).queryParam("term_value_id", sexValueId);
                        } else {
                            builder = builder.queryParam("term_id", TERM_LIFE_STAGE).queryParam("term_value_id", lifeStageValueId);
                        }
                        return builder.build();
                    })
                    .retrieve()
                    .bodyToMono(INaturalistResponse.class)
                    .timeout(Duration.ofSeconds(8))
                    .onErrorMap(WebClientException.class,
                            ex -> new ExternalServiceException("Failed to reach the photo reference service", ex))
                    .block();

            if (response == null || response.results() == null) {
                return List.of();
            }

            return response.results().stream()
                    .filter(obs -> sexValueId == null || hasAnnotation(obs, TERM_LIFE_STAGE, lifeStageValueId))
                    .flatMap(obs -> obs.photos() == null ? java.util.stream.Stream.empty() : obs.photos().stream()
                            .filter(photo -> !photo.hidden())
                            .map(photo -> toResponse(obs, photo)))
                    .limit(MAX_CANDIDATES_RETURNED)
                    .toList();
        } catch (ExternalServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Unexpected error fetching photo candidates for {}", scientificName, ex);
            throw new ExternalServiceException("Failed to reach the photo reference service", ex);
        }
    }

    private boolean hasAnnotation(Observation obs, int attributeId, int valueId) {
        if (obs.annotations() == null) {
            return false;
        }
        return obs.annotations().stream()
                .anyMatch(a -> a.controlledAttributeId() == attributeId && a.controlledValueId() == valueId);
    }

    private Integer toSexValueId(ImageGender gender) {
        return switch (gender) {
            case MALE -> VALUE_MALE;
            case FEMALE -> VALUE_FEMALE;
            case NOT_APPLICABLE -> null;
        };
    }

    private int toLifeStageValueId(LifeStageImage lifeStage) {
        return switch (lifeStage) {
            case ADULT -> VALUE_ADULT;
            case BABY -> VALUE_JUVENILE;
        };
    }

    private PhotoCandidateDto toResponse(Observation obs, Photo photo) {
        return new PhotoCandidateDto(
                String.valueOf(obs.id()),
                photo.url().replace("square.", "medium."),
                photo.licenseCode(),
                photo.attribution(),
                obs.uri()
        );
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record INaturalistResponse(List<Observation> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Observation(long id, String uri, List<Photo> photos, List<Annotation> annotations) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Photo(
            long id,
            String url,
            String attribution,
            @JsonProperty("license_code") String licenseCode,
            boolean hidden) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Annotation(
            @JsonProperty("controlled_attribute_id") int controlledAttributeId,
            @JsonProperty("controlled_value_id") int controlledValueId) {
    }
}
