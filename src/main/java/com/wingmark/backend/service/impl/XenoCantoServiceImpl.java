package com.wingmark.backend.service.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.wingmark.backend.config.XenoCantoProperties;
import com.wingmark.backend.dto.species.SpeciesRecordingResponseDto;
import com.wingmark.backend.exception.ExternalServiceException;
import com.wingmark.backend.service.XenoCantoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class XenoCantoServiceImpl implements XenoCantoService {

    private final WebClient xenoCantoWebClient;
    private final XenoCantoProperties xenoCantoProperties;

    @Override
    public List<SpeciesRecordingResponseDto> findRecordings(String scientificName) {
        if (!StringUtils.hasText(xenoCantoProperties.apiKey())) {
            throw new ExternalServiceException(
                    "Bird sound lookup is not configured: set the XENO_CANTO_API_KEY environment variable " +
                    "(register a free key at https://xeno-canto.org/account)");
        }

        try {
            XenoCantoApiResponse response = xenoCantoWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/recordings")
                            .queryParam("query", toTaggedQuery(scientificName))
                            .queryParam("key", xenoCantoProperties.apiKey())
                            .build())
                    .retrieve()
                    .bodyToMono(XenoCantoApiResponse.class)
                    .timeout(Duration.ofSeconds(8))
                    .onErrorMap(WebClientException.class,
                            ex -> new ExternalServiceException("Failed to reach the bird sound service", ex))
                    .block();

            if (response == null || response.recordings() == null) {
                return List.of();
            }

            return response.recordings().stream()
                    .map(this::toResponse)
                    .toList();
        } catch (ExternalServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Unexpected error fetching recordings for {}", scientificName, ex);
            throw new ExternalServiceException("Failed to reach the bird sound service", ex);
        }
    }

    private SpeciesRecordingResponseDto toResponse(XenoCantoRecording recording) {
        return new SpeciesRecordingResponseDto(
                recording.id(),
                normalizeUrl(recording.file()),
                recording.type(),
                recording.q(),
                recording.rec(),
                normalizeUrl(recording.lic())
        );
    }

    /**
     * v3 requires field-tagged queries (e.g. {@code gen:"Passer" sp:"domesticus"}) rather
     * than a bare search string. Species names in our data are two-word binomials, so the
     * first token becomes the genus tag and the rest becomes the species tag.
     */
    private String toTaggedQuery(String scientificName) {
        String[] parts = scientificName.trim().split("\\s+", 2);
        if (parts.length < 2) {
            return "sp:\"" + parts[0] + "\"";
        }
        return "gen:\"" + parts[0] + "\" sp:\"" + parts[1] + "\"";
    }

    private String normalizeUrl(String url) {
        if (url == null) {
            return null;
        }
        return url.startsWith("//") ? "https:" + url : url;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record XenoCantoApiResponse(List<XenoCantoRecording> recordings) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record XenoCantoRecording(String id, String file, String type, String q, String rec, String lic) {
    }
}
